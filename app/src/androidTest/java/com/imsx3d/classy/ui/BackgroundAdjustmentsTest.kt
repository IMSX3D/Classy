package com.imsx3d.classy.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.size.Size
import com.imsx3d.classy.R
import com.imsx3d.classy.data.BackgroundAdjustments
import com.imsx3d.classy.data.BackgroundSoftening
import com.imsx3d.classy.ui.component.BackgroundAdjustmentControls
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundAdjustmentsTest {
    @get:Rule val compose = createComposeRule()
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun persistenceBoundsAndResetPreserveLegacyDefaults() {
        val name = "background-test-${System.nanoTime()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(n: String, mode: Int) = base.getSharedPreferences(name, mode)
        }
        try {
            assertEquals(BackgroundAdjustments(), BackgroundAdjustments.read(context))
            val adjusted = BackgroundAdjustments(.45f, .8f, .3f, 0f, 1f)
            BackgroundAdjustments.save(context, adjusted)
            assertEquals(adjusted, BackgroundAdjustments.read(context))
            BackgroundAdjustments.save(context, BackgroundAdjustments(Float.NaN, 8f, -3f, -1f, 2f))
            assertEquals(BackgroundAdjustments(.18f, 1f, 0f, 0f, 1f), BackgroundAdjustments.read(context))
            BackgroundAdjustments.save(context, BackgroundAdjustments())
            assertEquals(BackgroundAdjustments(), BackgroundAdjustments.read(context))
        } finally { base.deleteSharedPreferences(name) }
    }

    @Test fun cachedBlurIsBoundedAndDoesNotAlterSource() = runBlocking {
        val source = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(1600 * 800) { if (it % 1600 < 800) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        source.setPixels(pixels, 0, 1600, 0, 0, 1600, 800)
        val blurred = BackgroundSoftening().transform(source, Size.ORIGINAL)
        try {
            assertEquals(512, blurred.width)
            assertEquals(256, blurred.height)
            val middle = android.graphics.Color.red(blurred.getPixel(256, 128))
            assertTrue(middle in 20..235)
            assertEquals(android.graphics.Color.BLACK, source.getPixel(799, 400))
            assertEquals(android.graphics.Color.WHITE, source.getPixel(800, 400))
            assertEquals(android.graphics.Color.BLACK, blurred.getPixel(0, 128))
            assertEquals(android.graphics.Color.WHITE, blurred.getPixel(511, 128))
        } finally { source.recycle(); blurred.recycle() }
    }

    @Test fun settingsPreviewExpandsWithReadableControls() {
        val file = com.imsx3d.classy.data.ScheduleBackgroundStore.file(base)
        val previous = file.takeIf { it.exists() }?.readBytes()
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(600 * 900) { i -> android.graphics.Color.rgb(
            70 + (i % 600) / 4, 70 + (i / 600) / 6, 160) }
        bitmap.setPixels(pixels, 0, 600, 0, 0, 600, 900)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            com.imsx3d.classy.data.ScheduleBackgroundStore.revision.value++
            compose.setContent {
                SleepyThemeProvider {
                    Column(Modifier.widthIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        com.imsx3d.classy.ui.component.ScheduleBackgroundSettings()
                    }
                }
            }
            compose.onNodeWithText(base.getString(R.string.background_adjust)).performScrollTo().performClick()
            compose.onNodeWithText(base.getString(R.string.background_visibility)).performScrollTo().assertIsDisplayed()
            compose.waitForIdle()
            compose.onRoot().captureToImage().asAndroidBitmap().let { shot ->
                java.io.File(base.getExternalFilesDir(null), "background-adjust-preview.png").outputStream().use {
                    shot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.onNodeWithText(base.getString(R.string.background_remove)).performScrollTo().assertIsDisplayed()
        } finally {
            bitmap.recycle()
            if (previous == null) file.delete() else file.writeBytes(previous)
            com.imsx3d.classy.data.ScheduleBackgroundStore.revision.value++
        }
    }

    @Test fun controlsRemainReachableAndResetAllValues() {
        var current by mutableStateOf(BackgroundAdjustments())
        var saved = current
        compose.setContent {
            SleepyThemeProvider {
                Column(Modifier.widthIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    BackgroundAdjustmentControls(current, { current = it }, { saved = current }, {
                        current = BackgroundAdjustments(); saved = current
                    })
                }
            }
        }
        val sliders = compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo))
        sliders[0].performScrollTo().performTouchInput { swipe(center, androidx.compose.ui.geometry.Offset(width - 20f, center.y)) }
        compose.runOnIdle { assertTrue("current=$current saved=$saved", saved.visibility > .5f) }
        for (i in 1..4) sliders[i].performScrollTo().performTouchInput { swipe(center, androidx.compose.ui.geometry.Offset(width - 20f, center.y)) }
        compose.runOnIdle { assertTrue(saved.softness > .9f); assertTrue(saved.vertical > .9f) }
        compose.onNodeWithText(base.getString(R.string.background_reset)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(BackgroundAdjustments(), current); assertEquals(current, saved) }
    }
}

