package com.xiaomieu.toolkit.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import java.security.MessageDigest

/** Resolves package names to app label / icon / flags via the PackageManager. */
class AppInfoResolver(context: Context) {

    private val pm: PackageManager = context.packageManager
    private val infoCache = HashMap<String, ApplicationInfo>()
    private val missing = HashSet<String>()
    private val labelCache = LruCache<String, String>(256)
    private val iconCache = LruCache<String, Drawable>(64)
    private val timesCache = HashMap<String, Pair<Long, Long>>()

    fun applicationInfo(pkg: String): ApplicationInfo? {
        if (infoCache.containsKey(pkg)) return infoCache[pkg]
        if (missing.contains(pkg)) return null
        return try {
            pm.getApplicationInfo(pkg, 0).also { infoCache[pkg] = it }
        } catch (e: PackageManager.NameNotFoundException) {
            missing.add(pkg)
            null
        }
    }

    fun isInstalled(pkg: String): Boolean = applicationInfo(pkg) != null

    fun label(pkg: String): String = labelCache.get(pkg) ?: run {
        val value = applicationInfo(pkg)?.loadLabel(pm)?.toString() ?: pkg
        labelCache.put(pkg, value)
        value
    }

    fun icon(pkg: String): Drawable? = iconCache.get(pkg) ?: run {
        val value = applicationInfo(pkg)?.loadIcon(pm)
        if (value != null) iconCache.put(pkg, value)
        value
    }

    fun isSystem(pkg: String): Boolean {
        val flags = applicationInfo(pkg)?.flags ?: return false
        return (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
    }

    fun uid(pkg: String): Int? = applicationInfo(pkg)?.uid

    /** 该包的 application meta-data 里是否有 [key]；包不存在返回 null。 */
    fun hasMetaData(pkg: String, key: String): Boolean? {
        val info = try {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        return info.metaData?.containsKey(key) ?: false
    }

    /** 该包是否声明了 authority 为 [authority] 的 provider；包不存在返回 null。 */
    fun hasProvider(pkg: String, authority: String): Boolean? {
        val info = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_PROVIDERS)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        return info.providers?.any { it.authority == authority } ?: false
    }

    /** 首次安装时间（毫秒）；未安装返回 null。用于列表排序。 */
    fun firstInstallTime(pkg: String): Long? = installTimes(pkg)?.first

    /** 最近更新时间（毫秒）；未安装返回 null。用于列表排序。 */
    fun lastUpdateTime(pkg: String): Long? = installTimes(pkg)?.second

    private fun installTimes(pkg: String): Pair<Long, Long>? = timesCache[pkg] ?: run {
        val value = try {
            val info = pm.getPackageInfo(pkg, 0)
            info.firstInstallTime to info.lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        if (value != null) timesCache[pkg] = value
        value
    }

    fun versionName(pkg: String): String? = try {
        pm.getPackageInfo(pkg, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun versionCode(pkg: String): Long? = try {
        pm.getPackageInfo(pkg, 0).longVersionCode
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** SHA-256 of the package's signing certificate, formatted as uppercase hex pairs. */
    fun signingSha256(pkg: String): String? = try {
        val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()?.let { bytes ->
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        @Volatile
        private var instance: AppInfoResolver? = null

        /**
         * Process-wide instance. The label/icon caches are only useful when shared, and the
         * list creates one [AppIcon] composable per row.
         */
        fun shared(context: Context): AppInfoResolver =
            instance ?: synchronized(this) {
                instance ?: AppInfoResolver(context.applicationContext).also { instance = it }
            }
    }
}
