package com.imsx3d.classy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-09-15: 三天窗口档位 per-widget 化 (管理页外卡 → 「· 小」二级编辑页)。
 * 锁三件事: 键隔离、迁移回退语义、族门控。
 *
 * UI-21a（2026-09-27）：尺寸档整体退场后，编辑页的 `WidgetEditCompactWindowSection`
 * 与其 `appliesTo` 族门控一起删除 —— 对应的用例随之移除（`WidgetCompactWindowCore`
 * 本身仍被 WeekList/WeekView 渲染路径使用，事务语义照旧锁）。
 */
class WidgetCompactWindowCoreTest {

    @Test
    fun `read returns null until explicitly written`() {
        assertNull(WidgetCompactWindowCore.read(emptyMap(), 7))
    }

    @Test
    fun `write then read round-trips per widget id`() {
        val raw = mutableMapOf<String, Boolean>()
        WidgetCompactWindowCore.write(raw, 1, todayFirst = false)
        WidgetCompactWindowCore.write(raw, 2, todayFirst = true)
        assertEquals(false, WidgetCompactWindowCore.read(raw, 1))
        assertEquals(true, WidgetCompactWindowCore.read(raw, 2))
        assertNull(WidgetCompactWindowCore.read(raw, 3))
    }

    @Test
    fun `delete clears only the target widget`() {
        val raw = mutableMapOf<String, Boolean>()
        WidgetCompactWindowCore.write(raw, 1, todayFirst = false)
        WidgetCompactWindowCore.write(raw, 2, todayFirst = false)
        WidgetCompactWindowCore.delete(raw, 1)
        assertNull(WidgetCompactWindowCore.read(raw, 1))
        assertEquals(false, WidgetCompactWindowCore.read(raw, 2))
    }

    @Test
    fun `resolve prefers explicit and falls back to legacy global`() {
        assertTrue(WidgetCompactWindowCore.resolve(explicit = null, legacyGlobal = true))
        assertFalse(WidgetCompactWindowCore.resolve(explicit = null, legacyGlobal = false))
        assertTrue(WidgetCompactWindowCore.resolve(explicit = true, legacyGlobal = false))
        assertFalse(WidgetCompactWindowCore.resolve(explicit = false, legacyGlobal = true))
    }
}

/** 渲染器接线契约: compact 脸必须读本实例档位, 不得回退全局 AppPrefs。 */
class WidgetCompactWindowWiringTest {

    private fun source(name: String): String =
        java.io.File("src/main/java/com/imsx3d/classy/widget/$name").readText()

    @Test
    fun `week list and week view build compact window from per-widget store`() {
        for (f in listOf("WeekListWidget.kt", "WeekViewWidget.kt")) {
            val fn = source(f)
            assertTrue(
                "$f must read WidgetCompactWindowStore.isTodayFirst(context, appWidgetId)",
                fn.contains("WidgetCompactWindowStore.isTodayFirst(context, appWidgetId)")
            )
            assertFalse(
                "$f must not read the legacy global pref directly",
                fn.contains("isCompactWindowTodayFirst(context)")
            )
            assertTrue(
                "$f must clear the per-widget entry in onDeleted",
                fn.contains("WidgetCompactWindowStore.remove(context, id)")
            )
        }
    }
}
