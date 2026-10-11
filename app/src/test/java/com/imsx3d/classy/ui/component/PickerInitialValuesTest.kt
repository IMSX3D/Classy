package com.imsx3d.classy.ui.component

import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class PickerInitialValuesTest {
    @Test fun calendarDayUsesUtcMidnight() {
        assertEquals(0L, PickerInitialValues.dateMillis("1970-01-01"))
        assertEquals(1709164800000L, PickerInitialValues.dateMillis("2024-02-29"))
    }

    @Test fun invalidDatesRemainUnselected() {
        for (value in listOf("", "2025-02-29", "2026-13-01", "0000-01-01", "2101-01-01", "invalid")) {
            assertNull(PickerInitialValues.dateMillis(value))
        }
    }

    @Test fun timeUsesCurrentFieldValueIncludingDayBoundaries() {
        assertEquals(LocalTime.MIDNIGHT, PickerInitialValues.time("00:00"))
        assertEquals(LocalTime.of(23, 59), PickerInitialValues.time("23:59"))
        assertEquals(LocalTime.of(14, 35), PickerInitialValues.time("14:35"))
    }

    @Test fun malformedTimeCannotCrashPickerConstruction() {
        for (value in listOf("", "25:00", "08:99", "-1:30", "invalid")) {
            assertEquals(LocalTime.of(8, 0), PickerInitialValues.time(value))
        }
    }
}
