package com.imsx3d.classy.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.R
import com.imsx3d.classy.data.entity.PeriodTableEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.ui.component.PeriodTableShareSheet
import com.imsx3d.classy.ui.component.ShareScheduleSheet
import com.imsx3d.classy.ui.screen.mine.exportAndShare
import com.imsx3d.classy.ui.screen.mine.shareText
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ShareResultFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private class ShareContext(base: Context, val fail: Boolean) : ContextWrapper(base) {
        var chooser: Intent? = null
        override fun startActivity(intent: Intent) {
            chooser = intent
            if (fail) throw ActivityNotFoundException("Controlled missing share activity")
        }
    }

    @Test fun textShareReportsLaunchFailureAndCanRetryWithoutClaimingSent() = runBlocking {
        val failed = ShareContext(context, true)
        var result = ""
        shareText(failed, "Synthetic schedule", "Round2", { result = it })
        assertEquals(context.getString(R.string.export_share_failed), result)
        val success = ShareContext(context, false)
        result = ""
        shareText(success, "Synthetic schedule", "Round2", { result = it })
        assertEquals("Opening the chooser must not claim sharing succeeded", "", result)
        assertEquals(Intent.ACTION_CHOOSER, success.chooser?.action)
    }

    @Test fun savedFileSurvivesShareFailureAndContainsExpectedContent() = runBlocking {
        val ctx = ShareContext(context, true)
        val name = "classy-round2-test-${System.nanoTime()}.txt"
        var result = ""
        try {
            exportAndShare(ctx, name, "text/plain", "Synthetic schedule", "Round2", { result = it })
            assertEquals(context.getString(R.string.export_saved_share_failed, name), result)
            @Suppress("DEPRECATION")
            val send = ctx.chooser!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            @Suppress("DEPRECATION")
            val uri = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("Synthetic schedule", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        } finally {
            context.contentResolver.delete(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "_display_name = ?", arrayOf(name))
        }
    }

    @Test fun scheduleSheetShowsFailureAndRetainsChoices() {
        compose.setContent {
            SleepyThemeProvider {
                CompositionLocalProvider(LocalContext provides ShareContext(LocalContext.current, true)) {
                    ShareScheduleSheet(TimeTableEntity(name = "Round2", startDate = "2026-10-12"), emptyList(), {})
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.export_share_title)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.export_share_failed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.export_json_title)).assertExists()
    }

    @Test fun periodSheetShowsFailureAndRetainsChoices() {
        compose.setContent {
            SleepyThemeProvider {
                CompositionLocalProvider(LocalContext provides ShareContext(LocalContext.current, true)) {
                    PeriodTableShareSheet(PeriodTableEntity(name = "Round2"), {})
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.period_table_share_native_title)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.export_share_failed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.period_table_share_json_title)).assertExists()
    }
}
