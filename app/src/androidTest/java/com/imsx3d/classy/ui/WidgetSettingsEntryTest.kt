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

    @Test fun scheduleNamePreferenceSurvivesReentryAndCanBeRestored() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val original = com.imsx3d.classy.util.AppPrefs.isWidgetShowTableName(context)
        var visible by mutableStateOf(true)
        val title = context.getString(com.imsx3d.classy.R.string.widget_show_table_name)
        try {
            com.imsx3d.classy.util.AppPrefs.setWidgetShowTableName(context, true)
            compose.setContent {
                androidx.compose.material3.MaterialTheme {
                    if (visible) com.imsx3d.classy.ui.screen.widget.WidgetManagementScreen({}, {})
                }
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(title))
            compose.onNodeWithText(title).assertIsOn().performClick()
            compose.runOnIdle {
                assertFalse(com.imsx3d.classy.util.AppPrefs.isWidgetShowTableName(context))
                visible = false
            }
            compose.waitForIdle()
            compose.runOnIdle { visible = true }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(title))
            compose.onNodeWithText(title).assertIsOff().performClick()
            compose.runOnIdle { assertTrue(com.imsx3d.classy.util.AppPrefs.isWidgetShowTableName(context)) }
        } finally {
            com.imsx3d.classy.util.AppPrefs.setWidgetShowTableName(context, original)
        }
    }

}
