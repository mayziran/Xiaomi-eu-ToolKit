package com.xiaomieu.toolkit.data

import com.topjohnwu.superuser.Shell

/**
 * Thin wrapper around libsu for reading files that belong to the system app
 * `com.xiaomi.xmsf` (its private data directory is not reachable without root).
 *
 * All calls block; invoke them from a background dispatcher.
 */
object RootShell {

    /** True when a root shell can be obtained (i.e. the user granted root to this app). */
    fun isRootAvailable(): Boolean =
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)

    /** Reads a UTF-8 text file through root; returns null when missing or unreadable. */
    fun readText(path: String): String? = runCatching {
        val result = Shell.cmd("cat \"$path\" 2>/dev/null").exec()
        if (result.isSuccess && result.out.isNotEmpty()) {
            result.out.joinToString("\n")
        } else {
            null
        }
    }.getOrNull()

    fun exists(path: String): Boolean = runCatching {
        Shell.cmd("test -e \"$path\" && echo 1").exec().out.any { it.trim() == "1" }
    }.getOrDefault(false)
}
