package com.imsx3d.classy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.component.CardsGridView
import com.imsx3d.classy.ui.component.TimeSlot
import com.imsx3d.classy.util.GridSelection
import com.imsx3d.classy.util.TimeTableUtils
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GridCourseGestureTest {
    @get:Rule val compose = createComposeRule()
    private val slots = (1..6).map { node ->
        val start = LocalTime.of(8 + node, 0)
        TimeSlot("$node", start, start.plusMinutes(45), start.toString(), start.plusMinutes(45).toString(), node, node)
    }
    @Test fun emptyGridClickResizeAndConfirmPrefillsSelectedRange() {
        var added: GridSelection? = null
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(350.dp).height(620.dp).testTag("grid")) {
                    CardsGridView(emptyList(), timeSlots = slots, visibleDays = setOf(1, 2, 3),
                        onCourseClick = {}, onQuickAdd = { added = it })
                }
            }
        }
        // Derive coordinates from actual visible time labels, independent of density/row scaling.
        val third = compose.onNodeWithText("3", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val fifth = compose.onNodeWithText("5", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bounds = compose.onNodeWithTag("grid").fetchSemanticsNode().boundsInRoot
        val x = bounds.width * .22f
        compose.onNodeWithTag("grid").performTouchInput { click(Offset(x, third.center.y - bounds.top)) }
        compose.onNodeWithContentDescription("新增课程，第 3–3 节").assertExists()
        compose.onNodeWithContentDescription("新增课程，第 3–3 节").performTouchInput {
            down(Offset(center.x, height * .75f))
            moveBy(Offset(0f, fifth.center.y - third.center.y), 500)
            up()
        }
        compose.onNodeWithContentDescription("新增课程，第 3–5 节").performClick()
        compose.runOnIdle { assertEquals(GridSelection(1, 3, 5), added) }
    }
    @Test fun cardLongPressDragRequestsMoveWithoutOpeningDetails() {
        val course = CourseEntity(id = 4, groupId = "g", tableId = 1, courseName = "Drag me",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "#123456")
        var clicked = false
        var target: GridSelection? = null
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(350.dp).height(620.dp)) {
                    CardsGridView(listOf(course), timeSlots = slots, visibleDays = setOf(1, 2, 3),
                        onCourseClick = { clicked = true }, onMoveCourse = { _, selected -> target = selected })
                }
            }
        }
        val first = compose.onNodeWithText("1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithText("3", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Drag me", useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(0f, third.center.y - first.center.y), 300)
            up()
        }
        compose.runOnIdle { assertFalse(clicked); assertEquals(GridSelection(1, 3, 3), target) }
    }
}
