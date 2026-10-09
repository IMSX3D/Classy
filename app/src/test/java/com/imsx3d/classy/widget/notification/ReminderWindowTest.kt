package com.imsx3d.classy.widget.notification

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReminderWindowTest {
    @Test fun preMidnightReminderChecksTomorrowsCourseDate() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val start = java.time.LocalDateTime.parse("2026-10-10T00:10").atZone(zone).toInstant().toEpochMilli()
        val notify = start - 30 * 60_000
        assertEquals(LocalDate.parse("2026-10-09"), ReminderWindow.classDate(notify, zone))
        assertEquals(LocalDate.parse("2026-10-10"), ReminderWindow.classDate(start, zone))
    }
    @Test fun windowCrossesYearAndContainsExactlySevenDates() {
        val dates = ReminderWindow.dates(LocalDate.parse("2026-12-29"))
        assertEquals(7, dates.size)
        assertEquals(LocalDate.parse("2027-01-04"), dates.last())
        assertEquals(7, dates.toSet().size)
    }

    @Test fun transferredOccurrenceCannotOverwriteSameCourseOnAnotherDay() {
        val first = ReminderWindow.key(42, LocalDate.parse("2026-10-09"))
        val second = ReminderWindow.key(42, LocalDate.parse("2026-10-10"))
        assertNotEquals(first, second)
        assertEquals(42L, ReminderWindow.courseId(first))
        assertEquals(42L, ReminderWindow.courseId(second))
    }

    @Test fun longCourseIdsDoNotCollideThroughIntTruncation() {
        val date = LocalDate.parse("2026-10-09")
        assertNotEquals(ReminderWindow.key(1, date), ReminderWindow.key(4294967297, date))
    }
}
