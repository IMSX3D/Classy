package com.imsx3d.classy.util

import com.imsx3d.classy.data.entity.CourseEntity
import org.junit.Assert.*
import org.junit.Test

class CourseMovePlannerTest {
    private val source = CourseEntity(id = 9, groupId = "math", tableId = 2, courseName = "数学",
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF009900", teacher = "老师", room = "A101")
    private val target = GridSelection(3, 3, 4)
    private fun plan(c: CourseEntity = source, from: Int = 6, to: Int = 6,
        scope: CourseMoveScope = CourseMoveScope.THIS_WEEK) =
        CourseMovePlanner.plan(c, from, to, target, scope, 20, TimeTableUtils.DEFAULT_TIME_JSON)
    @Test fun oneWeekMoveRetainsAllOtherOccurrencesAndMetadata() {
        val rows = plan()
        assertEquals(3, rows.size)
        for (week in 1..16) {
            val active = rows.filter { it.inWeek(week) }
            assertEquals(1, active.size)
            assertEquals(if (week == 6) 3 else 1, active.single().day)
        }
        assertTrue(rows.all { it.teacher == source.teacher && it.room == source.room && it.groupId == source.groupId })
        assertEquals(1, rows.count { it.id == 9L })
    }
    @Test fun futureMovePreservesOddWeeksAndEarlierLessons() {
        val rows = plan(source.copy(type = 1), from = 5, to = 5, scope = CourseMoveScope.THIS_AND_FUTURE)
        for (week in 1..16) {
            val active = rows.filter { it.inWeek(week) }
            assertEquals(if (week % 2 == 1) 1 else 0, active.size)
            if (active.isNotEmpty()) assertEquals(if (week >= 5) 3 else 1, active.single().day)
        }
    }
    @Test fun crossWeekMovesOnlyTheChosenOccurrence() {
        val rows = plan(to = 8)
        assertTrue(rows.none { it.inWeek(6) })
        assertEquals(2, rows.count { it.inWeek(8) })
        assertEquals(1, rows.count { it.inWeek(8) && it.day == 3 })
    }
    @Test fun futureWeekShiftChangesParityWithoutInventingLessons() {
        val rows = plan(source.copy(type = 1), from = 5, to = 6, scope = CourseMoveScope.THIS_AND_FUTURE)
        val changed = rows.filter { it.day == 3 }
        assertEquals(listOf(6, 8, 10, 12, 14, 16), (1..20).filter { w -> changed.any { it.inWeek(w) } })
    }
    @Test(expected = IllegalArgumentException::class) fun inactiveSourceWeekRejected() { plan(source.copy(type = 1)) }
    @Test(expected = IllegalArgumentException::class) fun outOfSemesterFutureMoveRejected() {
        plan(to = 15, scope = CourseMoveScope.THIS_AND_FUTURE)
    }
    @Test fun customDurationPreserved() {
        val rows = plan(source.copy(ownTime = true, isIrregularTime = true, startTime = "08:05", endTime = "09:00"))
        val moved = rows.single { it.day == 3 }
        assertTrue(moved.ownTime)
        assertEquals(55, java.time.Duration.between(java.time.LocalTime.parse(moved.startTime),
            java.time.LocalTime.parse(moved.endTime)).toMinutes().toInt())
    }
    @Test fun crossWeekCollisionWithRetainedOccurrenceIsReported() {
        val plan = CourseMovePlanner.details(source, 6, 8, GridSelection(1, 1, 2),
            CourseMoveScope.THIS_WEEK, 20, TimeTableUtils.DEFAULT_TIME_JSON)
        val conflicts = ConflictDetailReporter.draftConflictDetails(plan.moved, plan.retained,
            arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"))
        assertFalse(conflicts.isEmpty())
    }
    @Test fun movingFirstOrLastWeekDoesNotLeaveEmptyRows() {
        for (week in listOf(1, 16)) {
            val rows = plan(from = week, to = week)
            assertEquals(2, rows.size)
            assertTrue(rows.all { it.startWeek <= it.endWeek })
            assertEquals(16, (1..16).sumOf { w -> rows.count { it.inWeek(w) } })
        }
    }
    @Test(expected = IllegalArgumentException::class) fun hugeNodeRangeRejectedBeforeEnumeration() {
        CourseMovePlanner.plan(source, 6, 6, GridSelection(1, 1, Int.MAX_VALUE), CourseMoveScope.THIS_WEEK,
            20, TimeTableUtils.DEFAULT_TIME_JSON)
    }
    @Test fun navigationPrefillSurvivesSerialization() {
        val prefill = CourseGridPrefill(9, 5, 3, 3, 20)
        val json = kotlinx.serialization.json.Json.encodeToString(CourseGridPrefill.serializer(), prefill)
        assertEquals(prefill, kotlinx.serialization.json.Json.decodeFromString(CourseGridPrefill.serializer(), json))
    }
}
