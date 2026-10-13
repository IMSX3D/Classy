package com.imsx3d.classy.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.imsx3d.classy.widget.WidgetManagementViewModel
import com.imsx3d.classy.widget.rememberWidgetManagementViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WidgetSettingsEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun widgetListSurvivesLeavingAndReenteringComposition() {
        var visible by mutableStateOf(true)
        var current: WidgetManagementViewModel? = null
        compose.setContent {
            if (visible) {
                val vm = rememberWidgetManagementViewModel()
                SideEffect { current = vm }
            }
        }
        compose.waitUntil(5000) { current?.state?.value != null }
        val first = current
        val snapshot = first!!.state.value
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        compose.runOnIdle { visible = true }
        compose.runOnIdle {
            assertSame("Navigation must reuse the Activity-owned model", first, current)
            assertEquals(snapshot, current!!.state.value)
            current!!.reload()
            current!!.reload()
            assertEquals("Refreshing must not clear the visible list", snapshot, current!!.state.value)
        }
    }

}
