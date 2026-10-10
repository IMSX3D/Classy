package com.imsx3d.classy.ui.screen.edit

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.SaverScope
import com.imsx3d.classy.util.TimeTableUtils
import org.junit.Assert.*
import org.junit.Test

class CourseDraftSaversTest {
    private val scope = SaverScope { true }

    @Test fun editedMeetingRestoresIdentityPartialTimesAndPerMeetingFields() {
        val block = MeetingBlockDraft(7, mutableStateListOf(1, 5), 0, 1, "07:", "",
            isIrregularNode = true, selectedEdgeNode = 0, isIrregularTime = true,
            startWeek = 3, endWeek = 17, weekType = 2, room = "新教室", teacher = "新教师",
            note = "未完成\n草稿", color = "#FF123456", colorMode = 2, sourceIds = listOf(18L, 29L))
        block.durationText = "9"
        block.irregularOptionsExpanded = false
        block.clamped = true
        val saved = with(MeetingBlocksSaver) { scope.save(mutableStateListOf(block)) }!!
        val restored = MeetingBlocksSaver.restore(saved)!!.single()
        assertEquals(7, restored.id)
        assertEquals(listOf(1, 5), restored.days.toList())
        assertEquals(listOf(18L, 29L), restored.sourceIds)
        assertEquals("07:", restored.startTime)
        assertEquals("", restored.endTime)
        assertEquals("9", restored.durationText)
        assertEquals("新教室", restored.roomState)
        assertEquals("新教师", restored.teacherState)
        assertEquals("未完成\n草稿", restored.noteState)
        assertEquals("#FF123456", restored.colorState)
        assertEquals(2, restored.colorModeState)
        assertEquals(3, restored.startWeek)
        assertEquals(17, restored.endWeek)
        assertEquals(2, restored.weekType)
        assertTrue(restored.isIrregularNode)
        assertTrue(restored.isIrregularTime)
        assertFalse(restored.irregularOptionsExpanded)
        assertTrue(restored.clamped)
    }

    @Test fun pendingEdgeChangesRetainOwnerAndPartialText() {
        val insert = PendingEdgeInsert(TimeTableUtils.EdgeClass.Before, 0, "07:", "", 7)
        val inserts = with(EdgeInsertsSaver) { scope.save(mutableStateListOf(insert)) }!!
        assertEquals(insert, EdgeInsertsSaver.restore(inserts)!!.single())
        val edit = EdgeSlotEdit(13, "21:00", "21:")
        val edits = with(EdgeEditsSaver) { scope.save(mutableStateListOf(edit)) }!!
        assertEquals(edit, EdgeEditsSaver.restore(edits)!!.single())
    }
}
