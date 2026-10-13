package com.imsx3d.classy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.imsx3d.classy.ui.component.PillNavigationBar
import com.imsx3d.classy.ui.component.PillNavItemSpec
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PressShapeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun floatingFirstMiddleLastKeepFeedbackInsidePill() = verify(true)
    @Test fun dockedFirstMiddleLastKeepFeedbackInsideIndicator() = verify(false)
    private fun verify(dock: Boolean) {
        var selected by mutableIntStateOf(0)
        compose.setContent { SleepyThemeProvider {
            Box(Modifier.fillMaxWidth().padding(12.dp)) {
                PillNavigationBar(List(4) { PillNavItemSpec(Icons.Outlined.Home, "Tab$it") },
                    selected, { selected = it }, dock = dock)
            }
        } }
        for (i in listOf(0, 1, 3)) {
            val item = compose.onNodeWithText("Tab$i")
            val before = item.captureToImage().asAndroidBitmap()
            item.performTouchInput { down(center) }
            compose.waitForIdle()
            val during = item.captureToImage().asAndroidBitmap()
            assertFalse("press must be visible", before.sameAs(during))
            // Corners of the full hit target must never acquire rectangular feedback.
            for (x in listOf(0, before.width - 1)) for (y in listOf(0, before.height - 1))
                assertEquals("feedback escaped rounded footprint", before.getPixel(x, y), during.getPixel(x, y))
            item.performTouchInput { cancel() }
            compose.waitForIdle()
            assertTrue("cancel must clear feedback", before.sameAs(item.captureToImage().asAndroidBitmap()))
            item.performClick()
            compose.runOnIdle { assertEquals(i, selected) }
        }
    }
}
