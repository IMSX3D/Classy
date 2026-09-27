package com.imsx3d.classy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * per-widget 滚动开关纯逻辑 (2026-09-14: 滚动从全局一档改为小组件各自控制)。
 * 锁死: ①key 命名空间; ②read/write/delete 往返; ③迁移语义 = 显式值优先,
 * 从未设置才继承旧全局 (老用户升级行为不变, 拨过一次即彻底脱钩)。
 */
class WidgetScrollCoreTest {

    @Test
    fun `key is namespaced per widget id`() {
        assertEquals("scroll_7", WidgetScrollCore.key(7))
        assertEquals("scroll_-1", WidgetScrollCore.key(-1))
    }

    @Test
    fun `read write delete roundtrip`() {
        val raw = mutableMapOf<String, Boolean>()
        assertNull(WidgetScrollCore.read(raw, 1))
        WidgetScrollCore.write(raw, 1, true)
        WidgetScrollCore.write(raw, 2, false)
        assertEquals(true, WidgetScrollCore.read(raw, 1))
        assertEquals(false, WidgetScrollCore.read(raw, 2))
        WidgetScrollCore.delete(raw, 1)
        assertNull(WidgetScrollCore.read(raw, 1))
        assertEquals(false, WidgetScrollCore.read(raw, 2))
    }

    @Test
    fun `explicit value wins over legacy global`() {
        assertTrue(WidgetScrollCore.resolve(true, false))
        assertFalse(WidgetScrollCore.resolve(false, true))
    }

    @Test
    fun `unset inherits legacy global for migration`() {
        assertTrue(WidgetScrollCore.resolve(null, true))
        assertFalse(WidgetScrollCore.resolve(null, false))
    }
}
