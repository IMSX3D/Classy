package com.imsx3d.classy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.*
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InteractionStageTwoTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun dateCancelDoesNotCommitAndReopenUsesCurrentField() {
        var value by mutableStateOf("2026-10-11")
        var writes = 0
        compose.setContent {
            SleepyThemeProvider {
                DatePickerField(value, { value = it; writes++ }, "Date", Modifier.padding(top = 64.dp))
            }
        }
        compose.onNodeWithContentDescription(label(R.string.select_date)).performClick()
        compose.onNodeWithText(label(R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(0, writes); value = "2026-10-20" }
        compose.onNodeWithContentDescription(label(R.string.select_date)).performClick()
        compose.onNodeWithText(label(R.string.ok)).performClick()
        compose.runOnIdle { assertEquals("2026-10-20", value); assertEquals(1, writes) }
    }

    @Test fun timeCancelAndReopenUseCurrentValue() {
        var value by mutableStateOf("08:15")
        var writes = 0
        compose.setContent {
            SleepyThemeProvider { TimePickerField(value, { value = it; writes++ }, "Start", Modifier.padding(top = 64.dp)) }
        }
        compose.onNodeWithText("08:15").performClick()
        compose.onNodeWithText(label(R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(0, writes); value = "14:35" }
        compose.onNodeWithText("14:35").performClick()
        compose.waitUntil(3000) {
            compose.onAllNodesWithText(label(R.string.select_time)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(label(R.string.ok)).performClick()
        compose.runOnIdle { assertEquals("14:35", value); assertEquals(1, writes) }
    }

    @Test fun openPickerSurvivesSavedStateRestorationWithoutWriting() {
        var writes = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            var value by rememberSaveable { mutableStateOf("09:25") }
            SleepyThemeProvider { TimePickerField(value, { value = it; writes++ }, "Start", Modifier.padding(top = 64.dp)) }
        }
        compose.onNodeWithText("09:25").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(label(R.string.select_time)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, writes) }
        compose.onNodeWithText(label(R.string.cancel)).performClick()
        compose.onNodeWithText("09:25").assertIsDisplayed()
    }

    @Test fun largeFontFieldsReflowWithinNarrowBounds() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                SleepyThemeProvider {
                    Box(Modifier.width(280.dp)) {
                        AdaptiveTimeRangeFields("08:00", "09:00", "Start", "End", {}, {})
                    }
                }
            }
        }
        val first = compose.onNodeWithText("08:00").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("09:00").fetchSemanticsNode().boundsInRoot
        assertTrue("Second time must wrap below the first", second.top >= first.bottom)
        compose.onNodeWithText("08:00").assertIsDisplayed()
        compose.onNodeWithText("09:00").assertIsDisplayed()
    }
}
