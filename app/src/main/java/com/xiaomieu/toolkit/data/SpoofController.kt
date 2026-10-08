package com.xiaomieu.toolkit.data

import com.topjohnwu.superuser.Shell

/**
 * Root access to the Zygisk module's config file (`Xiaomi-eu-ToolKit-Zygisk`).
 *
 * The module reads `/data/adb/xiaomi_eu_toolkit_zygisk/apps.conf` from its own root companion
 * process; this app only ever writes that file. Format — one rule per line, `#` starts a comment:
 *
 * ```
 * com.taobao.taobao              # this package, all of its processes
 * com.taobao.idlefish|:push      # only that one process
 * -com.example.app               # explicitly excluded
 * ```
 *
 * The UI here manages the plain `package` form. Other rules for the same package are dropped when
 * its switch is flipped, so a switch always means exactly what it says.
 */
object SpoofController {

    const val CONFIG_DIR = "/data/adb/xiaomi_eu_toolkit_zygisk"

    /** The file the Zygisk module reads. */
    const val CONFIG_PATH = "$CONFIG_DIR/apps.conf"

    private const val MODULE_PROP = "/data/adb/modules/xiaomi_eu_toolkit_zygisk/module.prop"

    /** True when the module is installed (its directory in `/data/adb/modules` exists). */
    fun isModuleInstalled(): Boolean = RootShell.exists(MODULE_PROP)

    /** Packages that currently have an allow rule (plain or process-scoped). */
    fun readEnabled(): Set<String> = parseEnabled(RootShell.readText(CONFIG_PATH))

    /** Rewrites the whole file so it enables exactly [enabled]. */
    fun writeEnabled(enabled: Set<String>): Boolean {
        val body = buildString {
            append("# 由 Xiaomi-eu-ToolKit 写入：一行一个包名，# 开头为注释\n")
            enabled.sorted().forEach { append(it).append('\n') }
        }
        return writeFile(body)
    }

    private fun writeFile(content: String): Boolean = runCatching {
        val quoted = content.replace("'", "'\"'\"'")
        val command = buildString {
            append("mkdir -p \"$CONFIG_DIR\" && ")
            append("printf '%s' '$quoted' > \"$CONFIG_PATH\" && ")
            append("chown 0:0 \"$CONFIG_PATH\" 2>/dev/null; ")
            append("chmod 600 \"$CONFIG_PATH\" 2>/dev/null; ")
            append("restorecon \"$CONFIG_PATH\" 2>/dev/null; ")
            append("echo XTK_DONE")
        }
        val result = Shell.cmd(command).exec()
        result.isSuccess && result.out.any { it.trim() == "XTK_DONE" }
    }.getOrDefault(false)

    /** Kept internal so the parsing rules can be unit-tested without root. */
    internal fun parseEnabled(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .filterNot { it.startsWith("profile=") || it.startsWith("observe=") || it.startsWith("auto_scan=") }
            .filterNot { it.startsWith("-") }
            .map { it.substringBefore('|').trim() }
            .filter { it.isNotEmpty() }
            .toCollection(linkedSetOf())
    }
}
