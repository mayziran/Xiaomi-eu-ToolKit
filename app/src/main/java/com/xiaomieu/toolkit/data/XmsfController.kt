package com.xiaomieu.toolkit.data

import com.topjohnwu.superuser.Shell

/** Root operations against the official XMSF (`com.xiaomi.xmsf`). */
object XmsfController {

    /**
     * Sets the region markers XMSF reads on startup to the mainland-China values
     * (`mipush_region=China`, `mipush_country_code=CN`).
     *
     * Truncating in place preserves the existing owner and SELinux label; for files we have to
     * create, ownership and label are repaired afterwards.
     */
    fun applyChinaRegion(dataDir: String, ownerUid: Int?): Boolean {
        val region = XmsfPaths.filePath(dataDir, XmsfPaths.FILE_REGION)
        val country = XmsfPaths.filePath(dataDir, XmsfPaths.FILE_COUNTRY_CODE)
        val command = buildString {
            append("printf '%s' 'China' > \"$region\" && ")
            append("printf '%s' 'CN' > \"$country\" && ")
            if (ownerUid != null) {
                append("chown $ownerUid:$ownerUid \"$region\" \"$country\" 2>/dev/null; ")
            }
            append("chmod 600 \"$region\" \"$country\" 2>/dev/null; ")
            append("restorecon \"$region\" \"$country\" 2>/dev/null; ")
            append("echo XTK_DONE")
        }
        return runCatching {
            val result = Shell.cmd(command).exec()
            result.isSuccess && result.out.any { it.trim() == "XTK_DONE" }
        }.getOrDefault(false)
    }

    /** Force-stops the framework process. */
    fun forceStop(): Boolean = runCatching {
        Shell.cmd("am force-stop ${XmsfPaths.XMSF_PACKAGE}").exec().isSuccess
    }.getOrDefault(false)

    /**
     * Clears the framework's data and cache (`pm clear`). This also wipes the registration
     * table, so every app has to register again afterwards.
     */
    fun clearDataAndCache(): Boolean = runCatching {
        val result = Shell.cmd("pm clear ${XmsfPaths.XMSF_PACKAGE}").exec()
        result.out.any { it.contains("Success", ignoreCase = true) }
    }.getOrDefault(false)
}
