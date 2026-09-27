package com.imsx3d.classy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 出厂默认: 课程时间显示「时间」而非「节次」 (用户 2026-09-14 指令)。
 *
 * 锁死两件事 (纯 JVM, 与 AppPrefsIsolationTest 同模式):
 * 1. AppPrefs.getDisplayMode 缺省值字面量 = "time" — 源码扫描 (getter 无 Context
 *    不可直接 JVM 调, 缺省字面量锁源码行);
 * 2. KEY_DISPLAY_MODE key 字面量稳定 = "display_mode" (跨版本不漂移)。
 *
 * 设置页两项仍可改 (通用页 节次/时间二选一（现为 SettingsRowSegmented）);
 * 已手动设置过的老用户不受影响 (已存值优先 — getString 命中已存 key)。
 */
class DisplayModeDefaultTest {

    private fun src(path: String): String =
        File("src/main/java/com/imsx3d/classy/$path").readText()

    @Test
    fun `display_mode key literal is stable`() {
        assertEquals(
            "KEY_DISPLAY_MODE 字面量必须固定为 display_mode (跨版本稳定, 老用户已存值可读)",
            "display_mode", com.imsx3d.classy.util.AppPrefs.KEY_DISPLAY_MODE
        )
    }

    @Test
    fun `factory default is time not node`() {
        val s = src("util/AppPrefs.kt")
        // getter 缺省字面量锁死: getString(KEY_DISPLAY_MODE, "time") ?: "time"
        val getter = Regex(
            """fun getDisplayMode\(ctx: Context\): String =\s*\n\s*sp\(ctx\)\.getString\(KEY_DISPLAY_MODE, "time"\) \?: "time""""
        )
        assertTrue(
            "出厂默认必须是 \"time\" (用户指令: 默认显示时间段而非节次)。当前 AppPrefs.getDisplayMode 缺省值不是 time:\n$s",
            getter.containsMatchIn(s)
        )
    }

    @Test
    fun `widget renderers read prefs directly not a stale default`() {
        // 渲染侧直读 AppPrefs.getDisplayMode (WidgetContent displayMode 死字段已删的延续)
        // — 不得再引入硬编码缺省的旁路副本
        val renderer = src("widget/WidgetBitmapRenderers.kt")
        assertTrue(
            "WidgetBitmapRenderers 须直读 AppPrefs.getDisplayMode(context)",
            renderer.contains("AppPrefs.getDisplayMode(context)")
        )
        val weekGrid = src("widget/WeekGridWidgetProvider.kt")
        assertTrue(
            "WeekGrid 左栏时间行渲染不得硬编码节次文案旁路 (时间行画 slot 时间串, 无 displayMode 分叉)",
            !weekGrid.contains("getDisplayMode")
        )
    }
}
