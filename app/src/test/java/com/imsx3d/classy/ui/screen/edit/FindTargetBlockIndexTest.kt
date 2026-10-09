package com.imsx3d.classy.ui.screen.edit

import androidx.compose.runtime.mutableStateListOf
import com.imsx3d.classy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FindTargetBlockIndexTest {
    private fun block(id: Long, step: Int, week: Int) = MeetingBlockDraft(
        id = id.toInt(), days = mutableStateListOf(1), startNode = 1, step = step,
        startTime = "08:00", endTime = "09:00", startWeek = week, sourceIds = listOf(id),
    )
    private val course = CourseEntity(id = 20, groupId = "same", tableId = 1,
        courseName = "数学", day = 1, startNode = 1, step = 1, startWeek = 2, endWeek = 10, color = "")

    @Test fun sameDayAndStartButDifferentLengthAndWeeksFindExactSource() {
        assertEquals(1, findTargetBlockIndex(listOf(block(10, 2, 1), block(20, 1, 2)), course))
    }
    @Test fun missingOrNewCourseDoesNotJump() {
        assertEquals(-1, findTargetBlockIndex(listOf(block(10, 2, 1)), course))
        assertEquals(-1, findTargetBlockIndex(listOf(block(10, 2, 1)), null))
    }
}
