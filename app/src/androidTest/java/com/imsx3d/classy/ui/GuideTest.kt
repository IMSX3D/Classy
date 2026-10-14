package com.imsx3d.classy.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.screen.mine.GuideTopicScreen
import com.imsx3d.classy.ui.screen.mine.GuideWelcomeScreen
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GuideTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun label(id: Int) = context.getString(id)

    private fun node(id: Int): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(id)))
        return compose.onNodeWithText(label(id))
    }

    @Test fun allTopicsCanFinishWithoutChangingRealPreferences() {
        var topic by mutableIntStateOf(0)
        var finished = 0
        val prefs = context.getSharedPreferences("sleepy_prefs", 0)
        val before = prefs.all.toMap()
        compose.setContent { SleepyThemeProvider(darkTheme = false) {
            key(topic) { GuideTopicScreen(topic, onBack = {}, onOpen = {}, onDone = { finished++ }) }
        } }
        for (i in 0..7) {
            compose.runOnIdle { topic = i }
            File(context.getExternalFilesDir(null), "guide-$i.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            repeat(2) { node(R.string.guide_next).performClick() }
            node(R.string.guide_done).performClick()
        }
        compose.runOnIdle { assertEquals(8, finished); assertEquals(before, prefs.all) }
    }

    @Test fun exampleEditsSurviveRecreationWithoutSavingACourse() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { SleepyThemeProvider(darkTheme = true) {
            GuideTopicScreen(2, onBack = {}, onOpen = {})
        } }
        node(R.string.guide_example_title).performTextReplacement("Sample edit")
        node(R.string.guide_save).performClick()
        restoration.emulateSavedInstanceStateRestore()
        node(R.string.guide_saved).assertIsDisplayed()
        compose.onAllNodesWithText("Sample edit").assertCountEquals(2)
    }

    @Test fun welcomeCanBeSkippedWithoutCompletingLessons() {
        var skipped = false
        compose.setContent { SleepyThemeProvider(darkTheme = false) {
            GuideWelcomeScreen(onBack = { skipped = true }, onTry = {}, onImport = {})
        } }
        node(R.string.guide_skip).performClick()
        compose.runOnIdle { assertTrue(skipped) }
    }
}
