package com.imsx3d.classy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ensures data-change broadcasts cover every registered widget variant.
 *
 * UI-4c（2026-09-26）把组件收敛到 3 个、尺寸档整体退场后，清单一律以
 * [ALL_WIDGET_VARIANTS] 为单一事实源：新登记一个组件就必须同步进广播清单，
 * 否则「改了课表但桌面不刷新」——这正是本用例要挡的回归。
 */
class WidgetUpdaterWiringTest {
    @Test
    fun `refresh receiver list contains all registered widget variants`() {
        val receivers = WidgetUpdater.remoteViewsReceiverClasses
        val expected = ALL_WIDGET_VARIANTS.map { it.receiverClass }

        assertEquals("广播清单与已登记变体数量必须一致", expected.size, receivers.size)
        expected.forEach { receiver ->
            assertTrue("missing ${receiver.simpleName}", receiver in receivers)
        }
        assertEquals("当前上架组件数（今日 / 最近两天 / 本周课表）", 3, expected.size)
    }
}
