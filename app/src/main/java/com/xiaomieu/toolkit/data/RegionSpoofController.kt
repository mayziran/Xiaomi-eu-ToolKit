package com.xiaomieu.toolkit.data

import android.content.SharedPreferences
import com.xiaomieu.toolkit.service.ServiceHolder

/** app 侧对区域伪装开关的读写，走 LSPosed 的 remote preferences；服务未连接时读返回 null、写返回 false。 */
object RegionSpoofController {

    data class State(val weather: Boolean, val download: Boolean)

    /** 读取两个开关；服务未连接或读取失败返回 null。 */
    fun read(): State? {
        val prefs = prefs() ?: return null
        return runCatching {
            State(
                weather = prefs.getBoolean(RegionSpoofPrefs.KEY_WEATHER, RegionSpoofPrefs.DEFAULT_ENABLED),
                download = prefs.getBoolean(RegionSpoofPrefs.KEY_DOWNLOAD, RegionSpoofPrefs.DEFAULT_ENABLED),
            )
        }.getOrNull()
    }

    /** 写入单个开关；返回是否成功。 */
    fun write(key: String, value: Boolean): Boolean {
        val prefs = prefs() ?: return false
        return runCatching { prefs.edit().putBoolean(key, value).commit() }.getOrDefault(false)
    }

    /**
     * 三个目标包里还没加入模块作用域的部分；服务未连接返回 null。
     * 作用域由用户在 LSPosed 中勾选（`scope.list` 只是推荐值），没勾上的包不会执行 hook。
     */
    fun missingScope(): List<String>? {
        val service = ServiceHolder.getService() ?: return null
        return runCatching {
            val scope = service.scope.toSet()
            RegionSpoofPrefs.TARGET_PACKAGES.filterNot { it in scope }
        }.getOrNull()
    }

    private fun prefs(): SharedPreferences? = runCatching {
        ServiceHolder.getService()?.getRemotePreferences(RegionSpoofPrefs.GROUP)
    }.getOrNull()
}
