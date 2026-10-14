package com.imsx3d.classy.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class CourseDimmingTest {
    @Test fun allReasonCombinationsHaveExactlyTwoStates() {
        assertEquals(1f, CourseDimming.opacity(false, false), 0f)
        val dim = CourseDimming.opacity(true, false)
        assertTrue(dim > 0f && dim < 1f)
        assertEquals(dim, CourseDimming.opacity(false, true), 0f)
        assertEquals(dim, CourseDimming.opacity(true, true), 0f)
    }
    @Test fun opaqueRenderingDoesNotCompoundReasonsOrDependOnDimmedCardColor() {
        for (backdrop in listOf(Color.White, Color(0xff28282a))) {
            for (base in listOf(Color.Black, Color.White, Color.Red, Color(0xff888888))) {
                assertEquals(base, CourseDimming.color(base, backdrop, false, false))
                val completed = CourseDimming.color(base, backdrop, true, false)
                assertEquals(completed, CourseDimming.color(base, backdrop, false, true))
                assertEquals(completed, CourseDimming.color(base, backdrop, true, true))
                assertEquals(1f, completed.alpha, 0f)
            }
        }
    }
}
