package com.imsx3d.classy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.screen.imports.ImportDraft
import com.imsx3d.classy.ui.screen.imports.ImportDraftSheet
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportDraftFlowTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun deletingRequiresConfirmationAndFailureRemainsRetryable() {
        var attempts = 0
        compose.setContent {
            SleepyThemeProvider {
                ImportDraftSheet(listOf(ImportDraft("1", "Semester draft", "24 arrangements")), {}, {}, {
                    attempts++
                    if (attempts == 1) error("Simulated storage failure")
                })
            }
        }
        compose.onNodeWithContentDescription(label(R.string.import_draft_delete)).performClick()
        compose.onNodeWithText(label(R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(0, attempts) }
        compose.onNodeWithContentDescription(label(R.string.import_draft_delete)).performClick()
        compose.onAllNodesWithText(label(R.string.import_draft_delete)).onLast().performClick()
        compose.onNodeWithText(label(R.string.editor_action_failed)).assertIsDisplayed()
        compose.onAllNodesWithText(label(R.string.import_draft_delete)).onLast().performClick()
        compose.runOnIdle { assertEquals(2, attempts) }
        compose.onNodeWithText(label(R.string.import_draft_delete_confirm)).assertDoesNotExist()
    }
}
