package com.xiaomieu.toolkit.data

/**
 * Paths and preference keys of the official Xiaomi Service Framework (`com.xiaomi.xmsf`).
 *
 * Every name here was confirmed against the official APK (`com/xiaomi/push/service/r0.java`,
 * `n0.java`, `f.java`):
 * - `pref_registered_pkg_names` holds `package -> appId`,
 * - `mipush_apps_scrt` holds `package -> regSecret` and is written together with the appId,
 * - `mipush_app_info` holds the comma-separated `unregistered_pkg_names` and
 *   `disable_push_pkg_names` lists,
 * - `mipush_region` / `mipush_country_code` are plain files guarded by `.lock` siblings.
 */
object XmsfPaths {

    const val XMSF_PACKAGE = "com.xiaomi.xmsf"

    /** Region value the framework treats as mainland China (`XMPushService.isPushEnabled`). */
    const val REGION_CHINA = "China"

    // SharedPreferences files (under <dataDir>/shared_prefs/).
    const val PREF_REGISTERED = "pref_registered_pkg_names"
    const val PREF_APP_INFO = "mipush_app_info"
    const val PREF_APPS_SCRT = "mipush_apps_scrt"

    // Keys inside mipush_app_info (comma-separated package lists).
    const val KEY_UNREGISTERED = "unregistered_pkg_names"
    const val KEY_DISABLED = "disable_push_pkg_names"
    const val KEY_DISABLED_CACHE = "disable_push_pkg_names_cache"

    /** Plain files (under <dataDir>/files/). */
    const val FILE_REGION = "mipush_region"
    const val FILE_COUNTRY_CODE = "mipush_country_code"
    const val FILE_REGION_LOCK = "mipush_region.lock"
    const val FILE_COUNTRY_CODE_LOCK = "mipush_country_code.lock"

    /**
     * 官方国区框架用这两个声明"本框架支持系统推送"：application 的 `pushSupportFlag` meta-data，
     * 以及 authority 为 `com.xiaomi.push.provider.PUSH_SUPPORT` 的 provider。
     *
     * 国际版（版本号以 `-G` 结尾）是个空壳：保留了推送组件声明，但既没有这两个标记，也没有推送
     * 实现（连 `xmpush.xiaomi.com` 之类服务器地址都没有），所以任何应用都不会向它注册。
     */
    const val META_PUSH_SUPPORT_FLAG = "pushSupportFlag"
    const val PROVIDER_PUSH_SUPPORT = "com.xiaomi.push.provider.PUSH_SUPPORT"

    /** Candidate data directories, in probe order (primary user first). */
    fun dataDirs(userId: Int): List<String> = listOf(
        "/data/user/$userId/$XMSF_PACKAGE",
        "/data/data/$XMSF_PACKAGE",
        "/data_mirror/data_ce/null/$userId/$XMSF_PACKAGE",
    )

    fun sharedPref(dataDir: String, name: String): String = "$dataDir/shared_prefs/$name.xml"

    fun filePath(dataDir: String, name: String): String = "$dataDir/files/$name"
}
