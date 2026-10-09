package com.imsx3d.classy.util

import org.junit.Assert.*
import org.junit.Test

class GridCourseGeometryTest {
    @Test fun hiddenDaysAndGuttersDoNotCreateWrongWeekdays() {
        assertEquals(3, GridCourseGeometry.dayAt(140f, listOf(1, 3, 5), 40f, 80f, 10f))
        assertNull(GridCourseGeometry.dayAt(125f, listOf(1, 3, 5), 40f, 80f, 10f))
        assertNull(GridCourseGeometry.dayAt(30f, listOf(1, 3, 5), 40f, 80f, 10f))
        assertNull(GridCourseGeometry.dayAt(400f, listOf(1, 3, 5), 40f, 80f, 10f))
    }
    @Test fun weightedGapsAreNotRealPeriods() {
        val rows = listOf(GridHitRow(3, 0f, 100f), GridHitRow(null, 100f, 120f), GridHitRow(4, 120f, 200f))
        assertNull(GridCourseGeometry.nodeAt(110f, rows))
        assertEquals(4, GridCourseGeometry.nodeAt(130f, rows))
        assertEquals(3, GridCourseGeometry.nodeAt(99f, rows))
        assertNull(GridCourseGeometry.nodeAt(200f, rows))
    }
    @Test fun reverseDragNormalizesAndMissingPeriodsCannotBeInvented() {
        assertEquals(GridSelection(2, 3, 5), GridCourseGeometry.range(2, 5, 3, (1..12).toSet()))
        assertNull(GridCourseGeometry.range(2, 3, 5, setOf(3, 5)))
        assertNull(GridCourseGeometry.range(2, 0, 2, (0..12).toSet()))
    }
}
