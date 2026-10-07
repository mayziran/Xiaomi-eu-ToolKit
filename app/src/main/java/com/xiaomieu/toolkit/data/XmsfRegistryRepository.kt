package com.xiaomieu.toolkit.data

import com.xiaomieu.toolkit.data.model.DataFileStatus
import com.xiaomieu.toolkit.data.model.FrameworkInfo
import com.xiaomieu.toolkit.data.model.FrameworkVerdict
import com.xiaomieu.toolkit.data.model.MiPushSupport
import com.xiaomieu.toolkit.data.model.RegisteredApp
import com.xiaomieu.toolkit.data.model.RegistrationStatus
import com.xiaomieu.toolkit.data.model.XmsfDiagnostics
import com.xiaomieu.toolkit.data.model.XmsfRegion
import com.xiaomieu.toolkit.data.model.XmsfSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the official XMSF MiPush registration state through root and aggregates it into a
 * single [XmsfSnapshot].
 *
 * "Registered" is judged from the framework's own data:
 * - `pref_registered_pkg_names` (package -> appId) means registered,
 * - `mipush_app_info/unregistered_pkg_names` means explicitly unregistered.
 *
 * The app list is the union of the registry, the unregister list, and installed packages that
 * integrate MiPush — so "integrates MiPush but not registered" shows up in the main list with
 * [RegistrationStatus.NOT_REGISTERED].
 */
class XmsfRegistryRepository(
    private val appInfoResolver: AppInfoResolver,
    private val miPushScanner: MiPushScanner,
) {

    suspend fun loadSnapshot(userId: Int = 0): XmsfSnapshot = withContext(Dispatchers.IO) {
        val rootAvailable = RootShell.isRootAvailable()
        val dataDir = if (rootAvailable) pickDataDir(userId) else null
        val supportMap = runCatching { miPushScanner.scan() }.getOrDefault(emptyMap())
        val framework = frameworkInfo()

        if (dataDir == null) {
            return@withContext XmsfSnapshot(
                apps = collectApps(emptyMap(), emptyMap(), emptySet(), emptySet(), supportMap),
                diagnostics = XmsfDiagnostics(
                    rootAvailable = rootAvailable,
                    dataDir = null,
                    framework = framework,
                    region = XmsfRegion(null, null),
                    files = emptyList(),
                    registeredCount = 0,
                    unregisteredCount = 0,
                    pushDisabledCount = 0,
                    integratedCount = supportMap.keys.foreignCount(),
                    selfRegistered = false,
                    frameworkPushEnabled = false,
                ),
            )
        }

        val files = mutableListOf<DataFileStatus>()

        val registry = readAndTrack(files, "注册表 (pref_registered_pkg_names)",
            XmsfPaths.sharedPref(dataDir, XmsfPaths.PREF_REGISTERED),
            missingNote = "还没有任何应用注册（或读取失败）")
            ?.let { SharedPrefsXmlParser.stringValues(it) }
            ?.filterValues { it.isNotBlank() }
            ?: emptyMap()

        val secrets = readAndTrack(files, "注册密钥 (mipush_apps_scrt)",
            XmsfPaths.sharedPref(dataDir, XmsfPaths.PREF_APPS_SCRT),
            missingNote = "还没有应用注册过（正常）")
            ?.let { SharedPrefsXmlParser.stringValues(it) }
            ?: emptyMap()

        val appInfo = readAndTrack(files, "应用信息 (mipush_app_info)",
            XmsfPaths.sharedPref(dataDir, XmsfPaths.PREF_APP_INFO),
            missingNote = "没有任何应用被显式注销或禁用（正常）")
            ?.let { SharedPrefsXmlParser.stringValues(it) }
            ?: emptyMap()
        val unregistered = appInfo.packageSet(XmsfPaths.KEY_UNREGISTERED)
        val pushDisabled = appInfo.packageSet(XmsfPaths.KEY_DISABLED)

        val region = readAndTrack(files, "区域 (mipush_region)",
            XmsfPaths.filePath(dataDir, XmsfPaths.FILE_REGION),
            missingNote = "未设置")?.trim()
        val countryCode = readAndTrack(files, "国家代码 (mipush_country_code)",
            XmsfPaths.filePath(dataDir, XmsfPaths.FILE_COUNTRY_CODE),
            missingNote = "未设置")?.trim()

        // The framework's read path locks per name, so `mipush_country_code.lock` only appears
        // once the framework has read the country code. Its write path hardcodes
        // `mipush_region.lock` for both values, so writing never creates the country-code lock.
        trackOnly(files, "区域锁 (mipush_region.lock)",
            XmsfPaths.filePath(dataDir, XmsfPaths.FILE_REGION_LOCK),
            missingNote = "框架还没有读写过区域配置")
        trackOnly(files, "国家锁 (mipush_country_code.lock)",
            XmsfPaths.filePath(dataDir, XmsfPaths.FILE_COUNTRY_CODE_LOCK),
            missingNote = "框架还没有读取过国家代码（通常表示框架没起来/没连过服务器）")

        XmsfSnapshot(
            apps = collectApps(registry, secrets, unregistered, pushDisabled, supportMap),
            diagnostics = XmsfDiagnostics(
                rootAvailable = true,
                dataDir = dataDir,
                framework = framework,
                region = XmsfRegion(region, countryCode),
                files = files,
                registeredCount = registry.foreignEntryCount(),
                unregisteredCount = unregistered.foreignCount(),
                pushDisabledCount = pushDisabled.foreignCount(),
                integratedCount = supportMap.keys.foreignCount(),
                selfRegistered = registry.containsKey(XmsfPaths.XMSF_PACKAGE),
                frameworkPushEnabled = region == XmsfPaths.REGION_CHINA,
            ),
        )
    }

    private fun collectApps(
        registry: Map<String, String>,
        secrets: Map<String, String>,
        unregistered: Set<String>,
        pushDisabled: Set<String>,
        supportMap: Map<String, MiPushSupport>,
    ): List<RegisteredApp> {
        val packages = (registry.keys + unregistered + pushDisabled + supportMap.keys)
            .filter { it != XmsfPaths.XMSF_PACKAGE }
            .distinct()
            .sorted()
        return packages.map { pkg ->
            val appId = registry[pkg]
            val hasSecret = !secrets[pkg].isNullOrBlank()
            RegisteredApp(
                packageName = pkg,
                appId = appId,
                status = when {
                    appId != null && hasSecret -> RegistrationStatus.REGISTERED
                    appId != null -> RegistrationStatus.REGISTERED_NO_SECRET
                    pkg in unregistered -> RegistrationStatus.UNREGISTERED
                    else -> RegistrationStatus.NOT_REGISTERED
                },
                hasSecret = hasSecret,
                pushDisabled = pkg in pushDisabled,
                miPushSupport = supportMap[pkg] ?: MiPushSupport.NONE,
                installed = appInfoResolver.isInstalled(pkg),
                systemApp = appInfoResolver.isSystem(pkg),
                label = appInfoResolver.label(pkg),
                uid = appInfoResolver.uid(pkg),
            )
        }
    }

    /**
     * Splits one of the comma-separated package lists in `mipush_app_info`. Blank entries are
     * dropped: the framework can leave an empty string in `unregistered_pkg_names`.
     */
    private fun Map<String, String>.packageSet(key: String): Set<String> =
        this[key]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    private fun Map<String, String>.foreignEntryCount(): Int =
        keys.count { it != XmsfPaths.XMSF_PACKAGE }

    private fun Collection<String>.foreignCount(): Int =
        count { it != XmsfPaths.XMSF_PACKAGE }

    private fun frameworkInfo(): FrameworkInfo {
        val installed = appInfoResolver.isInstalled(XmsfPaths.XMSF_PACKAGE)
        val isSystem = appInfoResolver.isSystem(XmsfPaths.XMSF_PACKAGE)
        val cert = appInfoResolver.signingSha256(XmsfPaths.XMSF_PACKAGE)
        val official = cert?.equals(OFFICIAL_XMSF_CERT_SHA256, ignoreCase = true)
        val verdict = when {
            !installed -> FrameworkVerdict.MISSING
            !isSystem -> FrameworkVerdict.NOT_SYSTEM_APP
            official == true -> FrameworkVerdict.OFFICIAL
            official == false -> FrameworkVerdict.SIGNATURE_MISMATCH
            else -> FrameworkVerdict.UNKNOWN
        }
        return FrameworkInfo(
            installed = installed,
            isSystemApp = isSystem,
            versionName = appInfoResolver.versionName(XmsfPaths.XMSF_PACKAGE),
            versionCode = appInfoResolver.versionCode(XmsfPaths.XMSF_PACKAGE),
            signingSha256 = cert,
            signatureIsOfficial = official,
            verdict = verdict,
        )
    }

    private fun pickDataDir(userId: Int): String? {
        val candidates = XmsfPaths.dataDirs(userId)
        candidates.firstOrNull { RootShell.exists("$it/shared_prefs") }?.let { return it }
        return candidates.firstOrNull { RootShell.exists(it) }
    }

    private fun readAndTrack(
        files: MutableList<DataFileStatus>,
        label: String,
        path: String,
        missingNote: String? = null,
    ): String? {
        val exists = RootShell.exists(path)
        val content = if (exists) RootShell.readText(path) else null
        files.add(
            DataFileStatus(
                label = label,
                path = path,
                exists = exists,
                readable = content != null,
                missingNote = missingNote,
            ),
        )
        return content
    }

    /** Records a file that only matters by its presence, e.g. the framework's `.lock` siblings. */
    private fun trackOnly(
        files: MutableList<DataFileStatus>,
        label: String,
        path: String,
        missingNote: String,
    ) {
        val exists = RootShell.exists(path)
        files.add(
            DataFileStatus(
                label = label,
                path = path,
                exists = exists,
                readable = exists,
                missingNote = missingNote,
            ),
        )
    }

    companion object {
        /** Signing certificate SHA-256 of the official Xiaomi/MIUI-signed service framework. */
        const val OFFICIAL_XMSF_CERT_SHA256 =
            "C9:00:9D:01:EB:F9:F5:D0:30:2B:C7:1B:2F:E9:AA:9A:47:A4:32:BB:A1:73:08:A3:11:1B:75:D7:B2:14:90:25"
    }
}
