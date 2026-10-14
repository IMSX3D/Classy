package com.imsx3d.classy.util

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScheduleHolidaySnapshotTest {
    private val sunday = LocalDate.of(2026, 10, 11)

    @Test fun transferDestinationIsNotDimmedButKeepsHolidayMarker() {
        val snapshot = ScheduleHolidaySnapshot(holidays = setOf(sunday))
        assertEquals("休", snapshot.marker(sunday))
        assertFalse(snapshot.grey(sunday, listOf(HolidayTransferEntry(sunday.minusDays(3), sunday, "test"))))
        assertTrue(snapshot.grey(sunday, emptyList()))
    }

    @Test fun workdayExemptsWeekendAndMarkerDoesNotDependOnDimming() {
        val snapshot = ScheduleHolidaySnapshot(workdays = setOf(sunday))
        assertEquals("补", snapshot.marker(sunday))
        assertFalse(snapshot.grey(sunday, emptyList()))
        assertFalse(snapshot.copy(ignoreWorkday = false).grey(sunday, emptyList()))
        assertEquals("补", snapshot.copy(greyWeekend = false).marker(sunday))
    }

    @Test fun missingNetworkDataNeverInventsCancelledWeekendClasses() {
        assertFalse(ScheduleHolidaySnapshot().grey(sunday, emptyList()))
        assertFalse(ScheduleHolidaySnapshot(greyWeekend = false).grey(sunday, emptyList()))
        assertNull(ScheduleHolidaySnapshot().marker(sunday))
    }
}
