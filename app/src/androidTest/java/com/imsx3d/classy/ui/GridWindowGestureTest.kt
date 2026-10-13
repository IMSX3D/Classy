package com.imsx3d.classy.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.debug.EditorLifecycleTestActivity
import com.imsx3d.classy.ui.component.CardsGridView
import com.imsx3d.classy.ui.component.TimeSlot
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import com.imsx3d.classy.util.TimeTableUtils
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GridWindowGestureTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun widthChangeCancelsUncommittedDrag() = exercise(false, true)
    @Test fun heightChangeCancelsUncommittedDrag() = exercise(false, false)
    @Test fun activityRecreationCancelsUncommittedDrag() = exercise(true, false)

    private fun exercise(recreate: Boolean, resizeWidth: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val course = CourseEntity(id = 97001, groupId = "window-drag", tableId = 97000,
            courseName = "Window drag", day = 1, startNode = 1, step = 1,
            startWeek = 1, endWeek = 16, color = "#AACCEE")
        val slots = (1..6).map { n ->
            val start = LocalTime.of(7+n,0)
            TimeSlot("$n",start,start.plusMinutes(45),start.toString(),start.plusMinutes(45).toString(),n,n)
        }
        var width by mutableStateOf(350.dp)
        var height by mutableStateOf(600.dp)
        var active = false
        var moves = 0
        var opens = 0
        EditorLifecycleTestActivity.content = { SleepyThemeProvider {
            Box(Modifier.width(width).height(height)) {
                CardsGridView(listOf(course), timeSlots=slots, timeJson=TimeTableUtils.DEFAULT_TIME_JSON,
                    visibleDays=setOf(1,2,3), onCourseClick={ opens++ },
                    onMoveCourse={ _,_ -> moves++ }, onGestureActive={ active=it })
            }
        } }
        val scenario = ActivityScenario.launch<EditorLifecycleTestActivity>(Intent(context, EditorLifecycleTestActivity::class.java))
        try {
            val card = compose.onNodeWithText("Window drag", useUnmergedTree=true)
            card.assertIsDisplayed()
            compose.mainClock.autoAdvance=false
            card.performTouchInput { down(center); advanceEventTime(800); moveBy(Offset(0f,60f),300) }
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle { assertTrue("test must enter real dragging state",active) }
            if (recreate) scenario.recreate()
            else compose.runOnIdle { if (resizeWidth) width=280.dp else height=440.dp }
            compose.mainClock.advanceTimeBy(64)
            if (resizeWidth) {
                // Keep the pointer held while repeatedly changing the same window geometry.
                for (next in listOf(310.dp, 260.dp, 340.dp, 280.dp)) {
                    compose.runOnIdle { width=next }
                    compose.mainClock.advanceTimeBy(32)
                }
            }
            compose.waitForIdle()
            compose.runOnIdle { assertFalse(active); assertEquals(0,moves); assertEquals(0,opens) }
            // A late release must not commit the target calculated using the old geometry.
            if (!recreate) card.performTouchInput { up() }
            compose.mainClock.autoAdvance=true
            compose.runOnIdle { assertEquals(0,moves); assertEquals(0,opens) }
            card.performTouchInput { click(center) }
            compose.runOnIdle { assertEquals(1,opens); assertEquals(0,moves) }
        } finally {
            compose.mainClock.autoAdvance=true
            scenario.close()
            EditorLifecycleTestActivity.content=null
        }
    }
}
