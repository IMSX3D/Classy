package com.imsx3d.classy.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class WidgetCompletionBoundaryTest {
    private val now = LocalDateTime.of(2026, 10, 9, 9, 59, 59)

    @Test fun `schedule exact lesson end without extra minute`() {
        assertEquals(now.withHour(10).withMinute(0).withSecond(0),
            WidgetBoundaryScheduler.nextRefreshAt(listOf(LocalTime.of(10, 0)), now))
    }

    @Test fun `no more lessons still schedules midnight`() {
        assertEquals(LocalDateTime.of(2026, 10, 10, 0, 0),
            WidgetBoundaryScheduler.nextRefreshAt(emptyList(), now))
        assertEquals(LocalDateTime.of(2026, 10, 10, 0, 0),
            WidgetBoundaryScheduler.nextRefreshAt(listOf(LocalTime.of(9, 0)), now))
    }

    @Test fun `already finished boundary is skipped to avoid alarm loop`() {
        val ten = now.withHour(10).withMinute(0).withSecond(0)
        assertEquals(ten.plusHours(1), WidgetBoundaryScheduler.nextRefreshAt(
            listOf(LocalTime.of(10, 0), LocalTime.of(11, 0)), ten))
    }
}
