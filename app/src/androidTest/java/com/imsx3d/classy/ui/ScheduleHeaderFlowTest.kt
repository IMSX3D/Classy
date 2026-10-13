package com.imsx3d.classy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.SemesterOverviewCard
import com.imsx3d.classy.ui.screen.schedule.ScheduleHeader
import com.imsx3d.classy.ui.screen.schedule.ViewMode
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleHeaderFlowTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun longTitleKeepsActionsReachableAndRefreshClosesMenu() {
        var refreshes = 0
        compose.setContent {
            SleepyThemeProvider {
                Column(Modifier.widthIn(max = 320.dp)) {
                    ScheduleHeader("A very long timetable title that must not cover actions", 3, 20,
                        "2026-08-31", null, {}, false, {}, {}, {}, {}, {}, { refreshes++ }, ViewMode.Cards, {})
                }
            }
        }
        compose.onNodeWithContentDescription(label(R.string.schedule_actions)).assertIsDisplayed().performClick()
        compose.onNodeWithText(label(R.string.schedule_refresh_school)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
        compose.onNodeWithText(label(R.string.schedule_refresh_school)).assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.schedule_switch_table)).assertIsDisplayed()
    }

    @Test fun overviewExpandsLocallyAndSecondClickClosesDetails() {
        compose.setContent {
            SleepyThemeProvider {
                SemesterOverviewCard("Example semester", "2026-08-31") { Text("12 courses") }
            }
        }
        compose.onNodeWithText("Example semester").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.overview_title)).performClick()
        compose.onNodeWithText("Example semester").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.overview_title)).performClick()
        compose.onNodeWithText("Example semester").assertDoesNotExist()
    }

    @Test fun initialGuideDismissesAndCanBeReopenedFromMenu() {
        var dismissals = 0
        compose.setContent {
            SleepyThemeProvider {
                ScheduleHeader("Example", 3, 20, "2026-08-31", null,
                    {}, false, {}, {}, {}, {}, {}, {}, ViewMode.Cards, {},
                    showInitialGuide = true, onGuideDismissed = { dismissals++ })
            }
        }
        compose.onNodeWithText(label(R.string.schedule_usage_body)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.schedule_usage_done)).performClick()
        compose.runOnIdle { assertEquals(1, dismissals) }
        compose.onNodeWithText(label(R.string.schedule_usage_body)).assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.schedule_actions)).performClick()
        compose.onNodeWithText(label(R.string.schedule_usage_title)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.schedule_usage_body)).assertIsDisplayed()
    }
}
