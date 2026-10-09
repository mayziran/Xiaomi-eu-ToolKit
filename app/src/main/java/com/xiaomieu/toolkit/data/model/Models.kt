package com.xiaomieu.toolkit.data.model

/** Registration state inside the official XMSF registry. */
enum class RegistrationStatus {
    /** Has an appId in `pref_registered_pkg_names` and a regSecret in `mipush_apps_scrt`. */
    REGISTERED,

    /** Has an appId but no regSecret — a stale entry that will not receive push. */
    REGISTERED_NO_SECRET,

    /** Listed in `mipush_app_info/unregistered_pkg_names` and not in the registry. */
    UNREGISTERED,

    /** Not registered (includes apps that integrate MiPush but never got a registry entry). */
    NOT_REGISTERED,
    ;

    val isRegistered: Boolean
        get() = this == REGISTERED || this == REGISTERED_NO_SECRET
}

/**
 * How a package wires up MiPush, mirroring what the two reference projects treat as "integrates
 * MiPush":
 *
 * - the SDK's `ManifestChecker` rules (both services declared, enabled, correct exported flags,
 *   same process), or
 * - the `miui_push_app` meta-data marker used by MIUI system push apps.
 */
enum class MiPushSupport { SUPPORTED, PARTIAL, NONE }

/** Whether the installed `com.xiaomi.xmsf` looks like the official system framework. */
enum class FrameworkVerdict {
    /** Installed, system app, and signed with the known MIUI certificate. */
    OFFICIAL,

    /** Installed as a system app but the signing certificate is not the known MIUI one. */
    SIGNATURE_MISMATCH,

    /** Installed but as a user app (e.g. a third-party replacement). */
    NOT_SYSTEM_APP,

    /** `com.xiaomi.xmsf` is not installed. */
    MISSING,

    /** Could not be determined. */
    UNKNOWN,
}

data class RegisteredApp(
    val packageName: String,
    val appId: String?,
    val status: RegistrationStatus,
    /** True when `mipush_apps_scrt` holds a non-blank regSecret for this package. */
    val hasSecret: Boolean,
    /** True when the package is in `mipush_app_info/disable_push_pkg_names`. */
    val pushDisabled: Boolean,
    val miPushSupport: MiPushSupport,
    val installed: Boolean,
    val systemApp: Boolean,
    val label: String,
    val uid: Int?,
    /** 首次安装时间（毫秒），列表排序用；未安装为 null。 */
    val firstInstallTime: Long? = null,
    /** 最近更新时间（毫秒），列表排序用；未安装为 null。 */
    val lastUpdateTime: Long? = null,
)

data class XmsfRegion(
    val region: String?,
    val countryCode: String?,
)

data class DataFileStatus(
    val label: String,
    val path: String,
    val exists: Boolean,
    val readable: Boolean,
    /** Shown when the file does not exist, to explain that it may simply be absent. */
    val missingNote: String? = null,
)

data class FrameworkInfo(
    val installed: Boolean,
    val isSystemApp: Boolean,
    val versionName: String?,
    val versionCode: Long?,
    val signingSha256: String?,
    val signatureIsOfficial: Boolean?,
    val verdict: FrameworkVerdict,
    /**
     * 官方框架是否声明了"支持系统推送"（`pushSupportFlag` meta-data / PushSupportProvider）。
     *
     * 国际版（版本号以 `-G` 结尾）只声明了推送组件、没有这两个标记，也没有推送实现，
     * 因此任何应用都不会向它注册；[installed] 为 false 或读不到时为 null。
     */
    val declaresPushSupport: Boolean? = null,
)

data class XmsfDiagnostics(
    val rootAvailable: Boolean,
    val dataDir: String?,
    val framework: FrameworkInfo,
    val region: XmsfRegion,
    val files: List<DataFileStatus>,
    /** Counts exclude `com.xiaomi.xmsf` itself, which the framework registers on its own. */
    val registeredCount: Int,
    val unregisteredCount: Int,
    val pushDisabledCount: Int,
    val integratedCount: Int,
    /** True when the framework's own registry entry exists. */
    val selfRegistered: Boolean,
    /** Mirrors `XMPushService.isPushEnabled()`: the framework only pushes when region is China. */
    val frameworkPushEnabled: Boolean,
)

data class XmsfSnapshot(
    val apps: List<RegisteredApp>,
    val diagnostics: XmsfDiagnostics,
) {
    val isRegionCn: Boolean
        get() = diagnostics.region.countryCode.equals("CN", ignoreCase = true) ||
            diagnostics.region.region.equals("China", ignoreCase = true)
}
