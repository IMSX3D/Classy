package com.imsx3d.classy.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetDiscoveryTest {
    private val expected = setOf("Today", "TwoDay", "WeekGrid")
    @Test fun `query failure is unknown not missing`() {
        assertEquals(WidgetDiscoveryState.UNKNOWN, classifyWidgetDiscovery(expected, null))
    }
    @Test fun `empty or partial provider registration requires investigation`() {
        assertEquals(WidgetDiscoveryState.MISSING, classifyWidgetDiscovery(expected, emptySet()))
        assertEquals(WidgetDiscoveryState.MISSING, classifyWidgetDiscovery(expected, setOf("Today")))
    }
    @Test fun `equal count with different providers is not a successful registration`() {
        assertEquals(WidgetDiscoveryState.MISSING,
            classifyWidgetDiscovery(expected, setOf("Today", "TwoDay", "OldGrid")))
    }
    @Test fun `registration is checked by identity not order`() {
        assertEquals(WidgetDiscoveryState.REGISTERED,
            classifyWidgetDiscovery(expected, setOf("WeekGrid", "TwoDay", "Today")))
    }
}
