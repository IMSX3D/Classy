package com.imsx3d.classy.ui.screen.edit

import org.junit.Assert.*
import org.junit.Test

class CourseDraftFingerprintTest {
    private fun block() = MeetingBlockDraft(1, androidx.compose.runtime.mutableStateListOf(1), 1, 2, "08:00", "09:40")
    private fun fingerprint(block: MeetingBlockDraft, name: String = "Math") =
        courseDraftFingerprint(name, "", 1, 16, listOf(block), emptyList(), emptyList())

    @Test fun openingAdvancedOptionsIsNotAnEdit() {
        val block = block()
        val before = fingerprint(block)
        block.irregularOptionsExpanded = !block.irregularOptionsExpanded
        block.clamped = !block.clamped
        assertEquals(before, fingerprint(block))
    }
    @Test fun partialTextAndRoomChangesAreDetectedAndRevertingIsClean() {
        val block = block()
        val before = fingerprint(block)
        val time = block.startTime
        block.startTime = "08:"
        assertNotEquals(before, fingerprint(block))
        block.startTime = time
        assertEquals(before, fingerprint(block))
        block.roomState = "B201"
        assertNotEquals(before, fingerprint(block))
    }
    @Test fun courseNameAndEdgeEditsAreDetected() {
        val block = block()
        assertNotEquals(fingerprint(block), fingerprint(block, "Physics"))
        assertNotEquals(fingerprint(block), courseDraftFingerprint("Math", "", 1, 16,
            listOf(block), emptyList(), listOf(EdgeSlotEdit(0, "07:00", "07:45"))))
    }
}
