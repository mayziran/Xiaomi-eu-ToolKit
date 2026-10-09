package com.xiaomieu.toolkit.data

/** 区域伪装：app 侧（功能页开关）与模块侧（[com.xiaomieu.toolkit.xposed.HookEntry]）共享的配置。 */
object RegionSpoofPrefs {

    /** RemotePreferences 组名。 */
    const val GROUP = "region_spoof"

    /** 天气：把请求参数 `isGlobal` 改成 `false`，让服务端下发国内数据源。 */
    const val KEY_WEATHER = "weather_enabled"

    /** 下载管理：让迅雷引擎初始化、设置页出现"迅雷加速"。 */
    const val KEY_DOWNLOAD = "download_enabled"

    /** 两个开关默认关闭，用户手动打开前不做任何改动。 */
    const val DEFAULT_ENABLED = false

    const val WEATHER_PACKAGE = "com.miui.weather2"
    const val DOWNLOADS_PROVIDER_PACKAGE = "com.android.providers.downloads"
    const val DOWNLOADS_UI_PACKAGE = "com.android.providers.downloads.ui"

    /** 需要 hook 的三个应用，与 `META-INF/xposed/scope.list` 保持一致。 */
    val TARGET_PACKAGES = listOf(
        WEATHER_PACKAGE,
        DOWNLOADS_PROVIDER_PACKAGE,
        DOWNLOADS_UI_PACKAGE,
    )

    /** 该应用归哪个开关管；不在功能范围内返回 null。 */
    fun switchKeyForPackage(packageName: String): String? = when (packageName) {
        WEATHER_PACKAGE -> KEY_WEATHER
        DOWNLOADS_PROVIDER_PACKAGE, DOWNLOADS_UI_PACKAGE -> KEY_DOWNLOAD
        else -> null
    }
}
