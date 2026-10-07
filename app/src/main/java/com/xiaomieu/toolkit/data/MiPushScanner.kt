package com.xiaomieu.toolkit.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.xiaomieu.toolkit.data.model.MiPushSupport

/**
 * Detects whether an installed package integrates MiPush, following the same signals the
 * reference projects use:
 *
 * 1. the SDK's `ManifestChecker` rules —
 *    `com.xiaomi.mipush.sdk.PushMessageHandler` (enabled, **exported**) and
 *    `com.xiaomi.mipush.sdk.MessageHandleService` (enabled, not exported), same process; or
 * 2. the `miui_push_app` meta-data marker used by MIUI system push apps.
 *
 * Services that are declared but misconfigured are reported as [MiPushSupport.PARTIAL].
 */
class MiPushScanner(private val context: Context) {

    /** package name -> support level, for every installed package that declares MiPush signals. */
    fun scan(): Map<String, MiPushSupport> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SERVICES or PackageManager.GET_META_DATA
        val packages = runCatching { pm.getInstalledPackages(flags) }.getOrDefault(emptyList())
        val result = LinkedHashMap<String, MiPushSupport>()
        for (info in packages) {
            val support = support(info)
            if (support != MiPushSupport.NONE) {
                result[info.packageName] = support
            }
        }
        return result
    }

    fun support(info: PackageInfo): MiPushSupport {
        val services = info.services.orEmpty()
        val handler = services.firstOrNull { it.name == HANDLER }
        val handle = services.firstOrNull { it.name == HANDLE }

        val configured = handler != null && handle != null &&
            handler.enabled && handler.exported &&
            handle.enabled && !handle.exported &&
            handler.processName == handle.processName
        if (configured) return MiPushSupport.SUPPORTED

        if (info.applicationInfo?.metaData?.containsKey(MIUI_PUSH_APP) == true) {
            return MiPushSupport.SUPPORTED
        }

        if (handler != null || handle != null) return MiPushSupport.PARTIAL
        return MiPushSupport.NONE
    }

    companion object {
        const val HANDLER = "com.xiaomi.mipush.sdk.PushMessageHandler"
        const val HANDLE = "com.xiaomi.mipush.sdk.MessageHandleService"
        const val MIUI_PUSH_APP = "miui_push_app"
    }
}
