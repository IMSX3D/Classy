package com.imsx3d.classy.util

import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SemesterScheduleTest {
    private val table = TimeTableEntity(id = 1, name = "Fixture", startDate = "2026-09-07", maxWeek = 4)
    private fun course(id: Long = 1) = CourseEntity(id = id, groupId = "math", tableId = 1,
        courseName = "Math", teacher = "A", day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 4, color = "")

    @Test fun oddAndEvenWeeksHaveExactDates() {
        val rows = listOf(course().copy(type = 1), course(2).copy(type = 2))
        val result = SemesterSchedule.build(table, rows, emptyList()).single()
        assertEquals(listOf("2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28"),
            result.occurrences.map { it.date.toString() })
        assertEquals(listOf(1L, 2L, 1L, 2L), result.occurrences.map { it.course.id })
    }

    @Test fun movedClassOutsideSemesterAppearsOnceAtDestination() {
        val transfer = HolidayTransferEntry(LocalDate.parse("2026-09-07"), LocalDate.parse("2026-10-12"), "fixture")
        val result = SemesterSchedule.build(table, listOf(course()), listOf(transfer)).single().occurrences
        assertEquals(4, result.size)
        assertFalse(result.any { it.date == transfer.sourceDate })
        assertEquals(transfer.targetDate, result.last().date)
    }

    @Test fun identityAndTeacherPreventAccidentalMerging() {
        val rows = listOf(course(), course(2).copy(teacher = "B"), course(3).copy(groupId = "other"))
        assertEquals(3, SemesterSchedule.build(table, rows, emptyList()).size)
    }

    @Test fun irregularTimesAndRoomsAreRetainedWithoutMutatingRows() {
        val c = course().copy(ownTime = true, startTime = "18:13", endTime = "19:27", room = "Lab 3")
        val result = SemesterSchedule.build(table, listOf(c), emptyList()).single()
        assertTrue(result.occurrences.all { it.time == "18:13-19:27" && it.course.room == "Lab 3" })
        assertSame(c, result.rows.single())
    }

    @Test fun missingRoutineDoesNotInventClockTimes() {
        assertNull(SemesterSchedule.build(table.copy(timeJson = "[]"), listOf(course()), emptyList())
            .single().occurrences.first().time)
    }

    @Test fun projectionMatchesWeeklyResolverForEveryDay() {
        val rows = listOf(course(), course(2).copy(day = 3, type = 2), course(3).copy(startWeek = 3, endWeek = 3))
        val transfers = listOf(HolidayTransferEntry(LocalDate.parse("2026-09-16"), LocalDate.parse("2026-09-19"), "fixture"))
        val result = SemesterSchedule.build(table, rows, transfers).flatMap { it.occurrences }
        repeat(28) {
            val date = LocalDate.parse(table.startDate).plusDays(it.toLong())
            assertEquals(CourseDateResolver.coursesOn(date, table.startDate, table.maxWeek, rows, transfers).map { it.id }.sorted(),
                result.filter { it.date == date }.map { it.course.id }.sorted())
        }
    }
}
