package com.imsx3d.classy.ui

import com.imsx3d.classy.ui.component.BrandFireworks
import org.junit.Assert.*
import org.junit.Test

class BrandFireworksTest {
    @Test fun fasterInputAddsMorePaperAndDoesNotResetExistingFlight() {
        val state = BrandFireworks()
        state.emit(0, 120, true, 0)
        val first = state.bursts.first()
        state.emit(200, 700, false, 1)
        val slow = state.bursts.last().count
        state.emit(300, 100, false, 2)
        assertTrue(state.bursts.last().count > slow)
        assertEquals(first, state.bursts.first())
        assertEquals(3, state.bursts.size)
    }
    @Test fun sustainedInputIsBoundedAndStopsAfterLastLifetime() {
        val state = BrandFireworks()
        repeat(1000) {
            state.emit(it * 20L, 20, it == 0, it % 3)
            assertTrue(state.bursts.sumOf { b -> b.count } <= BrandFireworks.MAX_PARTICLES)
        }
        state.expire(19980 + BrandFireworks.LIFETIME_MS - 1)
        assertTrue(state.bursts.isNotEmpty())
        state.expire(19980 + BrandFireworks.LIFETIME_MS)
        assertTrue(state.bursts.isEmpty())
    }
}
