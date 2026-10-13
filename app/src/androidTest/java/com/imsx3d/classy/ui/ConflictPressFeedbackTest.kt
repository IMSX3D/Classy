package com.imsx3d.classy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.component.ConflictClusterCard
import com.imsx3d.classy.ui.component.TimeSlot
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import com.imsx3d.classy.util.ConflictCluster
import java.time.LocalTime
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ConflictPressFeedbackTest {
    @get:Rule val compose = createComposeRule()
    @Test fun stackPressCancelAndThreeLayerCycle() = verify("stack")
    @Test fun railPressCancelAndThreeLayerCycle() = verify("rail")
    @Test fun foldPressCancelAndThreeLayerCycle() = verify("fold")

    @Test fun dimmedStackKeepsFeedback() = verify("stack", true)
    @Test fun dimmedRailKeepsFeedback() = verify("rail", true)
    @Test fun dimmedFoldKeepsFeedback() = verify("fold", true)

    private fun verify(style: String, dimmed: Boolean = false) {
        var hiddenTitle by mutableStateOf("Course 2")
        val courses = (1..3).map { i -> CourseEntity(id = i.toLong(), tableId = 99000,
            groupId = "feedback-$i", courseName = "Course $i", day = 1,
            startNode = 1, step = 2, startWeek = 1, endWeek = 16,
            color = listOf("#AACCEE", "#EECBAA", "#BBDEAA")[i - 1]) }
        val slots = (1..4).map { n ->
            val start = LocalTime.of(7 + n, 0)
            TimeSlot("$n", start, start.plusMinutes(45), start.toString(), start.plusMinutes(45).toString(), n, n)
        }
        var step by mutableIntStateOf(0)
        var opens = 0
        var writes = 0
        compose.setContent { SleepyThemeProvider {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                ConflictClusterCard(cluster = ConflictCluster(1, courses.map { if (it.id == 2L) it.copy(courseName = hiddenTitle) else it }), style = style,
                    topOverrideId = null, onPickTop = { writes++ }, onCourseClick = { opens++ },
                    colW = 120.dp, rowH = 64.dp, maxNode = 4, timeW = 0.dp,
                    gapW = 0.dp, gapH = 4.dp, timeSlots = slots, isGrey = dimmed,
                    rotationStep = step, onRotate = { step++ }, modifier = Modifier.testTag("cluster"))
            }
        } }
        val node = compose.onNodeWithTag("cluster")
        // Changing a fully covered course must not alter any pixel in the front content.
        val original = node.captureToImage().asAndroidBitmap()
        compose.runOnIdle { hiddenTitle = "XXXXXXXXXXXX" }
        val replaced = node.captureToImage().asAndroidBitmap()
        for (x in original.width / 4 until original.width * 3 / 4)
            for (y in original.height / 4 until original.height * 3 / 4)
                assertEquals("hidden text leaked through dimmed front", original.getPixel(x,y), replaced.getPixel(x,y))
        compose.runOnIdle { hiddenTitle = "Course 2" }
        for (layer in 0..2) {
            val before = node.captureToImage().asAndroidBitmap()
            node.performTouchInput {
                down(Offset(width - 8f, if (style == "fold") 10f else height - 8f))
            }
            compose.waitForIdle()
            val held = node.captureToImage().asAndroidBitmap()
            assertFalse("$style layer $layer needs visible feedback", before.sameAs(held))
            var changed = 0
            for (px in 0 until before.width) for (py in 0 until before.height)
                if (before.getPixel(px, py) != held.getPixel(px, py)) changed++
            val minimumFraction = if (style == "fold") 0.001 else 0.01
            assertTrue("feedback must cover the visible lower surface, not just antialiased hidden edges: $changed",
                changed > before.width * before.height * minimumFraction)
            // Transparent overlays must not paint the outer inset or the front card.
            for (x in listOf(0, before.width - 1)) for (y in 0 until before.height)
                assertEquals("$style escaped card bounds ($x,$y)", before.getPixel(x,y), held.getPixel(x,y))
            val x = (before.width * 0.75f).toInt()
            val y = (before.height * 0.75f).toInt()
            assertEquals("front course must not receive switch feedback", before.getPixel(x,y), held.getPixel(x,y))
            val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "conflict-feedback").apply { mkdirs() }
            File(dir, "$style-$dimmed-$layer-held.png").outputStream().use { held.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            node.performTouchInput { cancel() }
            compose.waitForIdle()
            assertTrue("cancel clears feedback", before.sameAs(node.captureToImage().asAndroidBitmap()))
            compose.runOnIdle { assertEquals(layer, step); assertEquals(0, opens); assertEquals(0, writes) }
            node.performTouchInput { click(Offset(width - 8f, if (style == "fold") 10f else height - 8f)) }
            compose.runOnIdle { assertEquals(layer + 1, step); assertEquals(0, opens); assertEquals(0, writes) }
        }
        node.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, opens) }
    }
}
