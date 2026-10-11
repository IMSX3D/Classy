package com.imsx3d.classy.ui.screen.edit

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wiring guard for the visible top-then-scroll flash. Not a frame/animation test. */
class CourseEditorInitialPositionTest {
    @Test fun `editor waits for group rows before creating its initially positioned list`() {
        val source = File("src/main/java/com/imsx3d/classy/ui/screen/edit/AddCourseScreen.kt").readText()
        val gate = source.indexOf("if (editingCourse != null && !blocksLoaded)")
        val list = source.indexOf("val editListState =")
        assertTrue(gate >= 0 && list > gate)
        assertTrue(source.substring(gate, list).contains("return"))
        assertTrue(source.substring(list).contains("initialFirstVisibleItemIndex ="))
        assertFalse(source.contains("editListState.scrollToItem"))
        assertFalse(source.contains("editListState.animateScrollToItem"))
    }
}
