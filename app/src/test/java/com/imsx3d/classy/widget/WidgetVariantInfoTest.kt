package com.imsx3d.classy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [WidgetVariantInfo] — shared metadata describing the
 * **3 live widget variants**（UI-4c 起：今日课程 / 最近两天 / 本周课表（网格）；
 * 尺寸档整体退场，被摘掉的类与 info xml 都还在，但要恢复必须先在 [ALL_WIDGET_VARIANTS] 登记）。 The list is the single source of truth for both the
 * refresh broadcast in [WidgetUpdater] and the management screen UI; this
 * test pins that contract.
 */
class WidgetVariantInfoTest {

    @Test
    fun `ALL_WIDGET_VARIANTS has exactly 3 entries`() {
        assertEquals(
            "组件家族收敛到 3 个（UI-4c）；加回尺寸档要先改这条闸",
            3, ALL_WIDGET_VARIANTS.size
        )
    }

    @Test
    fun `every variant has a unique receiver class`() {
        val classes = ALL_WIDGET_VARIANTS.map { it.receiverClass }
        assertEquals("duplicate receiver class in metadata", classes.size, classes.toSet().size)
    }

    @Test
    fun `all entries carry a positive name resource id`() {
        ALL_WIDGET_VARIANTS.forEach { v ->
            assertTrue("displayNameRes must be non-zero for ${v.receiverClass.simpleName}", v.displayNameRes != 0)
        }
    }

    @Test
    fun `WidgetUpdater receiver list matches ALL_WIDGET_VARIANTS`() {
        // The refresh broadcast must not silently drop or duplicate a variant;
        // it must mirror the metadata list 1:1.
        val infoClasses = ALL_WIDGET_VARIANTS.map { it.receiverClass }.toSet()
        val updaterClasses = WidgetUpdater.remoteViewsReceiverClasses.toSet()
        assertEquals(infoClasses, updaterClasses)
    }

    @Test
    fun `metadata holds exactly one entry per live family`() {
        // UI-4c 收敛后只剩三个家族：Today / TwoDay / WeekGrid（各自一条，不再分尺寸档）。
        // WeekList / WeekView 的实现仍在（内部调试页与回滚用），但**不注册** —— 它们出现在这里
        // 就说明有人把它们加回了用户可见列表，必须先想清楚尺寸档策略。
        val byKind = ALL_WIDGET_VARIANTS.map { v ->
            v.receiverClass.simpleName.removeSuffix("SmallWidgetProvider")
                .removeSuffix("SmallWidgetReceiver")
                .removeSuffix("WideWidgetReceiver")
                .removeSuffix("WidgetProvider")
                .removeSuffix("WidgetReceiver")
        }
        val counts = byKind.groupingBy { it }.eachCount()
        val expected = mapOf("Today" to 1, "TwoDay" to 1, "WeekGrid" to 1)
        assertEquals("家族集合必须恰好是这三家", expected.keys, counts.keys)
        counts.forEach { (kind, count) ->
            assertEquals("kind=$kind 只能有一条登记", expected.getValue(kind), count)
        }
        listOf("WeekList", "WeekView").forEach { gone ->
            assertTrue("$gone 是已摘掉的家族，不该再出现在登记表里", counts[gone] == null)
        }
    }
}
