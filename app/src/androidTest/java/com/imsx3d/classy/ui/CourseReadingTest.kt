package com.imsx3d.classy.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.component.CourseOverlayCard
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CourseReadingTest {
    @get:Rule val compose = createComposeRule()
    @Test fun barsRemainReadableOnShortAndTallCards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val old = com.imsx3d.classy.util.AppPrefs.getCourseColorStyle(context)
        com.imsx3d.classy.util.AppPrefs.setCourseColorStyle(context, "bar")
        var dark by mutableStateOf(false)
        val course = CourseEntity(id = 999, groupId = "marker", tableId = -10,
            courseName = "Math", room = "Room 2", day = 1, startNode = 1,
            step = 2, startWeek = 1, endWeek = 4, color = "#FF66BB88")
        try {
            compose.setContent { SleepyThemeProvider(darkTheme = dark) {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp)) {
                    CourseOverlayCard(course, {}, Modifier.width(90.dp).height(160.dp))
                    CourseOverlayCard(course.copy(courseName = "Short"), {}, Modifier.width(70.dp).height(44.dp))
                }
            } }
            for (theme in listOf(false, true)) {
                compose.runOnIdle { dark = theme }
                compose.onNodeWithText("Math").assertIsDisplayed()
                compose.onNodeWithText("Short").assertIsDisplayed()
                compose.onNodeWithText("Room 2").assertIsDisplayed()
                File(context.getExternalFilesDir(null), "marker-$theme.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally { com.imsx3d.classy.util.AppPrefs.setCourseColorStyle(context, old) }
    }
    @Test fun titlePositionUpdatesImmediatelyAndDoesNotMoveMetadataOrWidgetPreference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = com.imsx3d.classy.util.AppPrefs
        val old = prefs.isCourseTitleCentered(context)
        val widget = prefs.isCourseTitleCentered(context, widget = true)
        val info = prefs.getGridSubInfo(context)
        val course = CourseEntity(id = 998, groupId = "position", tableId = -10,
            courseName = "Title", room = "Room", day = 1, startNode = 1,
            step = 2, startWeek = 1, endWeek = 4, color = "#FF66BB88")
        try {
            prefs.setCourseTitleCentered(context, false)
            prefs.setGridSubInfo(context, "room")
            compose.setContent { SleepyThemeProvider(darkTheme = false) {
                CourseOverlayCard(course, {}, Modifier.width(90.dp).height(180.dp))
            } }
            val top = compose.onNodeWithText("Title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            val room = compose.onNodeWithText("Room", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            compose.runOnIdle { prefs.setCourseTitleCentered(context, true) }
            val centered = compose.onNodeWithText("Title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("top=$top centered=$centered room=$room pref=${prefs.isCourseTitleCentered(context)}", centered.top > top + 10f)
            assertTrue(centered.bottom < room)
            assertEquals(room, compose.onNodeWithText("Room", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top, 1f)
            assertEquals(widget, prefs.isCourseTitleCentered(context, widget = true))
        } finally {
            prefs.setCourseTitleCentered(context, old)
            prefs.setGridSubInfo(context, info)
        }
    }

    @Test fun titlesKeepSameTopOriginWithAndWithoutMetadata() {
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        val course = CourseEntity(id = 1, groupId = "fixture", tableId = -10,
            courseName = "Math", day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 4, color = "#FFFF00FF")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oldStyle = com.imsx3d.classy.util.AppPrefs.getCourseColorStyle(context)
        val oldInfo = com.imsx3d.classy.util.AppPrefs.getGridSubInfo(context)
        com.imsx3d.classy.util.AppPrefs.setCourseColorStyle(context, "fill")
        com.imsx3d.classy.util.AppPrefs.setGridSubInfo(context, "room")
        try {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, font)) {
                SleepyThemeProvider(darkTheme = dark) {
                    Row(Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp)) {
                        CourseOverlayCard(course, {}, Modifier.width(90.dp).height(180.dp))
                        CourseOverlayCard(course.copy(room = "Room 2"), {}, Modifier.width(90.dp).height(180.dp))
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            compose.runOnIdle { dark = theme; font = scale }
            val names = compose.onAllNodesWithText("Math").fetchSemanticsNodes()
            assertEquals(2, names.size)
            assertEquals(names[0].boundsInRoot.top, names[1].boundsInRoot.top, 1f)
            compose.onNodeWithText("Room 2").assertIsDisplayed()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            File(ctx.getExternalFilesDir(null), "reading-$theme-$scale.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        } finally {
            com.imsx3d.classy.util.AppPrefs.setCourseColorStyle(context, oldStyle)
            com.imsx3d.classy.util.AppPrefs.setGridSubInfo(context, oldInfo)
        }
    }
}
