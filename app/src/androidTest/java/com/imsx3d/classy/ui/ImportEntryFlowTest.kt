package com.imsx3d.classy.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.screen.imports.ImportSheet
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportEntryFlowTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @OptIn(ExperimentalMaterial3Api::class)
    private fun open() {
        compose.setContent {
            SleepyThemeProvider {
                ImportSheet(rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    onDismiss = {}, onJwImportRequested = {}, onImported = {})
            }
        }
    }

    @Test fun sourceChoicesStayCompactAndHelpReturnsToChoices() {
        open()
        val bounds = compose.onNodeWithTag("import-source-choices").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(bounds.bottom - bounds.top <= 480.dp)
        compose.onNodeWithText(label(R.string.import_jw)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.import_file)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.format_csv)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.import_supported_formats)).performClick()
        compose.onNodeWithText(label(R.string.format_csv)).assertIsDisplayed()
        compose.onAllNodesWithContentDescription(label(R.string.back)).onLast().performClick()
        compose.onNodeWithText(label(R.string.import_file)).assertIsDisplayed()
    }

    @Test fun validPreviewBackKeepsInputWithoutApplying() {
        open()
        compose.onNodeWithText(label(R.string.import_paste)).performClick()
        val sample = "#classy-v1\nTPreview Test|2026-03-02|20|12\nNd\nCTest Course|1|1-2|1-16|Teacher|A101||||"
        compose.onNode(hasSetTextAction()).performTextInput(sample)
        compose.onNodeWithText(label(R.string.import_preview)).performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText(label(R.string.import_preview_title)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(label(R.string.import_preview_title)).assertIsDisplayed()
        compose.onAllNodesWithContentDescription(label(R.string.back)).onLast().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains(sample)
    }

    @Test fun textFailureAndCancelKeepInputWithoutImporting() {
        open()
        compose.onNodeWithText(label(R.string.import_paste)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("invalid-import-for-ui-test")
        compose.onNodeWithText(label(R.string.import_preview)).performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText(label(R.string.jw_error_dialog_title)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(label(R.string.jw_error_dialog_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.jw_err_dismiss)).performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("invalid-import-for-ui-test")
        compose.onNodeWithText(label(R.string.cancel)).performClick()
        compose.onNodeWithText(label(R.string.import_paste)).performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("invalid-import-for-ui-test")
    }
}

