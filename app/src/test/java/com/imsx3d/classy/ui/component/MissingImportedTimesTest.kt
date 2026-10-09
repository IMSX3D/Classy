package com.imsx3d.classy.ui.component

import com.imsx3d.classy.util.TimeTableUtils.TimeSlotRow
import com.imsx3d.classy.data.entity.RoutinePlan
import org.junit.Assert.*
import org.junit.Test

class MissingImportedTimesTest {
    @Test fun `eleven imported blank periods can enter automatic arrangement`() {
        val rows = (1..11).map { TimeSlotRow(it, "", "") }
        assertNull(resolveAutoPeriodConfig(rows, null))
        val seed = missingTimeAutoSeed(rows)!!
        assertEquals(11, seed.derive().size)
        assertEquals("08:55", seed.derive()[1].start)
        assertEquals(seed.derive(), RoutinePlan.from(seed).toConfig().derive())
        assertEquals(seed.derive(), mergeAutoRows(rows, seed.derive()))
    }
    @Test fun `partial times offer seed without mutating imported rows`() {
        val rows = listOf(TimeSlotRow(1, "08:30", "09:15"), TimeSlotRow(2, "", ""))
        assertEquals("08:30", missingTimeAutoSeed(rows)!!.startTime)
        assertEquals("", rows[1].start)
    }
    @Test fun `complete overlap and disconnected numbering are not treated as missing times`() {
        assertNull(missingTimeAutoSeed(listOf(TimeSlotRow(1, "08:00", "09:00"), TimeSlotRow(2, "08:30", "09:30"))))
        assertNull(missingTimeAutoSeed(listOf(TimeSlotRow(1, "", ""), TimeSlotRow(3, "", ""))))
        assertNull(missingTimeAutoSeed(emptyList()))
    }
}
