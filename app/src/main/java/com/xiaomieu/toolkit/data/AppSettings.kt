package com.xiaomieu.toolkit.data

import android.content.Context
import android.content.SharedPreferences

/** App 级设置。设置项很少，直接用 SharedPreferences，不引额外依赖。 */
object AppSettings {

    private const val PREF_NAME = "settings"
    private const val KEY_HIDE_SYSTEM_APPS = "hide_system_apps"

    /** 是否在所有应用列表里隐藏系统应用。默认隐藏。 */
    fun hideSystemApps(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_SYSTEM_APPS, true)

    fun setHideSystemApps(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_SYSTEM_APPS, value).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
