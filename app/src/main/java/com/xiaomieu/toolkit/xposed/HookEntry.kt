package com.xiaomieu.toolkit.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * LSPosed entry point for Xiaomi.eu ToolKit.
 *
 * The module skeleton is intentionally minimal for now: the current feature set (viewing the
 * official XMSF MiPush registration state) is read-only and done through root on the app side.
 * Hook installation for XMSF and other apps is added here later, gated behind the module scope
 * declared in `META-INF/xposed/scope.list`.
 */
class HookEntry : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        try {
            log(Log.INFO, TAG, "module loaded in process=${param.processName} systemServer=${param.isSystemServer}")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "onModuleLoaded failed", t)
        }
    }

    companion object {
        const val TAG = "XiaomiEuToolKit"
    }
}
