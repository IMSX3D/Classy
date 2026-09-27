package com.imsx3d.classy

import com.imsx3d.classy.util.AppPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * v1.0.56 T3 捏放缩放→实验室开关(纯 JVM 字面量契约, 本仓库无 Robolectric)。
 *
 * 行为契约(默认关=手势不挂)由 CourseTableView 参数化 + 模拟器人工验证锁;
 * 此处锁键存在性/字面量唯一/默认值语义常量。
 */
class GridPinchZoomKeyTest {

    @Test
    fun pinch_zoom_key_is_exact_string() {
        assertEquals("grid_pinch_zoom", AppPrefs.KEY_GRID_PINCH_ZOOM)
    }

    @Test
    fun pinch_zoom_key_not_colliding_with_row_scale_key() {
        assertNotEquals(AppPrefs.KEY_GRID_PINCH_ZOOM, AppPrefs.KEY_GRID_ROW_SCALE)
    }

    @Test
    fun default_off_constant_is_false() {
        // 实验室功能默认全关(2026-09-16 用户令): 关 = 双指捏放不可用
        assertEquals(false, AppPrefs.DEFAULT_GRID_PINCH_ZOOM)
    }
}
