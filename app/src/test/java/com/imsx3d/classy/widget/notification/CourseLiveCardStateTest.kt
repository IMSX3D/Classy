package com.imsx3d.classy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseLiveCardStateTest {
    @Test
    fun `progress follows OPPO window from zero to one hundred`() {
        assertEquals(0, progressPercent(1_000L, 11_000L, 1_000L))
        assertEquals(50, progressPercent(1_000L, 11_000L, 6_000L))
        assertEquals(100, progressPercent(1_000L, 11_000L, 11_000L))
    }

    @Test
    fun `progress clamps before and after window`() {
        assertEquals(0, progressPercent(1_000L, 11_000L, 0L))
        assertEquals(100, progressPercent(1_000L, 11_000L, 20_000L))
    }

    @Test
    fun `state exposes active lifecycle and shared detail text`() {
        val state = CourseLiveCardState(
            courseName = "高等数学",
            room = "A301",
            teacher = "张老师",
            startTime = "08:00",
            notifyEpoch = 1_000L,
            classEpoch = 11_000L,
            nowEpoch = 6_000L,
            updateSequence = 3
        )

        assertTrue(state.isActive)
        assertEquals(50, state.progress)
        assertEquals("高等数学", state.primaryText)
        assertEquals("08:00  ·  A301  ·  张老师", state.detailText)
    }

    @Test
    fun `state ends at class epoch`() {
        val state = CourseLiveCardState(
            courseName = "英语",
            room = "B202",
            teacher = "",
            startTime = "10:00",
            notifyEpoch = 1_000L,
            classEpoch = 11_000L,
            nowEpoch = 11_000L
        )

        assertFalse(state.isActive)
        assertEquals(100, state.progress)
        assertEquals("10:00  ·  B202", state.detailText)
    }
}
