package com.imsx3d.classy.util

import com.imsx3d.classy.data.entity.CourseEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CourseCompletionTest {
    private val friday = LocalDate.of(2026, 10, 9)
    private val management = CourseEntity(
        groupId = "management", tableId = 1, courseName = "管理学", day = 5,
        startNode = 1, step = 2, startWeek = 1, endWeek = 20, color = "#FF6750A4",
        ownTime = true, startTime = "09:00", endTime = "10:00"
    )
    private fun done(course: CourseEntity = management, date: LocalDate = friday,
                     at: String = "2026-10-09T10:10", json: String = "") =
        CourseCompletion.isCompleted(course, date, json, LocalDateTime.parse(at))

    @Test fun `management dims at 10 and music stays normal at 1010`() {
        assertFalse(done(at = "2026-10-09T09:59:59"))
        assertTrue(done(at = "2026-10-09T10:00"))
        assertTrue(done())
        assertFalse(done(management.copy(startTime = "10:10", endTime = "11:00")))
    }

    @Test fun `Thursday and every day of last week are completed on Friday`() {
        assertTrue(done(date = friday.minusDays(1)))
        for (day in 1..7) {
            assertTrue(done(date = DateUtils.dateOfWeek("2026-08-31", 5, day)))
        }
    }

    @Test fun `same weekday in next week is not completed`() {
        assertFalse(done(date = friday.plusWeeks(1)))
        assertFalse(done(date = friday.plusDays(1)))
    }

    @Test fun `ongoing PE at 1116 is normal until 1230`() {
        val pe = management.copy(startTime = "11:00", endTime = "12:30")
        assertFalse(done(pe, at = "2026-10-09T11:16"))
        assertTrue(done(pe, at = "2026-10-09T12:30"))
    }

    @Test fun `multi period course ends at last slot not first slot`() {
        val json = TimeTableUtils.buildTimeJsonFromRows(listOf(
            TimeTableUtils.TimeSlotRow(1, "09:00", "09:45"),
            TimeTableUtils.TimeSlotRow(2, "09:50", "10:35")
        ))
        val regular = management.copy(ownTime = false)
        assertFalse(done(regular, json = json))
        assertTrue(done(regular, at = "2026-10-09T10:35", json = json))
        assertTrue(done(management, json = json)) // personal times take priority
    }

    @Test fun `invalid or missing times leave today normal but past days still dim`() {
        for (course in listOf(management.copy(endTime = "bad"),
            management.copy(startTime = "11:00"), management.copy(ownTime = false))) {
            assertFalse(done(course))
            assertTrue(done(course, date = friday.minusDays(1)))
        }
        assertFalse(done(management.copy(ownTime = false), json = "bad json"))
        assertFalse(done(management.copy(ownTime = false), json = "[]"))
    }

    @Test fun `display occurrence date wins over original transferred weekday`() {
        val transferred = management.copy(day = 1)
        assertFalse(done(transferred, date = friday.plusDays(1)))
        assertTrue(done(transferred, date = friday.minusDays(1)))
    }

    @Test fun `midnight dims previous day but not new day`() {
        assertTrue(done(date = friday, at = "2026-10-10T00:00"))
        assertFalse(done(date = friday.plusDays(1), at = "2026-10-10T00:00"))
    }
}
