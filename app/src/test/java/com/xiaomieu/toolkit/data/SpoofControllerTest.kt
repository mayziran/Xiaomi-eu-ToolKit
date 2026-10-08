package com.xiaomieu.toolkit.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 纯解析逻辑，不碰 root，可直接在 JVM 上跑。 */
class SpoofControllerTest {

    @Test
    fun parsesPlainPackageLines() {
        val raw = """
            # 注释行应被忽略
            com.taobao.taobao

              com.taobao.idlefish

            profile=miui14
            observe=true
            auto_scan=true
        """.trimIndent()

        assertEquals(
            setOf("com.taobao.taobao", "com.taobao.idlefish"),
            SpoofController.parseEnabled(raw),
        )
    }

    @Test
    fun denyLinesAreNotEnabled() {
        assertEquals(emptySet<String>(), SpoofController.parseEnabled("-com.example.app"))
    }

    @Test
    fun processScopedLinesCountAsEnabledForThatPackage() {
        assertEquals(
            setOf("com.taobao.taobao"),
            SpoofController.parseEnabled("com.taobao.taobao|:push"),
        )
    }

    @Test
    fun blankOrMissingConfigIsEmpty() {
        assertEquals(emptySet<String>(), SpoofController.parseEnabled(null))
        assertEquals(emptySet<String>(), SpoofController.parseEnabled(""))
        assertEquals(emptySet<String>(), SpoofController.parseEnabled("  \n\n#\n"))
    }
}
