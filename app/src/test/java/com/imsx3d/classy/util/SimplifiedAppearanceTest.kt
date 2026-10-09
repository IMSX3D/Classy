package com.imsx3d.classy.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SimplifiedAppearanceTest {
    @Test fun autoFitDoesNotMultiplyByOldManualZoom() {
        for (saved in listOf(0.7f, 1f, 1.8f)) {
            assertEquals(1f, TimetableViewportPolicy.effectiveRowScale(true, saved), 0f)
            assertEquals(saved, TimetableViewportPolicy.effectiveRowScale(false, saved), 0f)
        }
    }

    @Test fun legacyCombinationsResolveToOneChoice() {
        assertEquals(CourseAppearance.BAR, CourseAppearance.fromLegacy("bar", false))
        assertEquals(CourseAppearance.FILL, CourseAppearance.fromLegacy("fill", false))
        assertEquals(CourseAppearance.NEUTRAL, CourseAppearance.fromLegacy("bar", true))
        assertEquals(CourseAppearance.NEUTRAL, CourseAppearance.fromLegacy("fill", true))
        assertEquals(CourseAppearance.FILL, CourseAppearance.fromLegacy(null, false))
        assertEquals(CourseAppearance.FILL, CourseAppearance.fromLegacy("unknown", false))
    }

    @Test fun selectionsRoundTripThroughLegacyStorage() {
        CourseAppearance.entries.forEach {
            assertEquals(it, CourseAppearance.fromLegacy(it.style, it.colorless))
        }
    }

}
