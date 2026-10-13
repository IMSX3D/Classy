package com.imsx3d.classy.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.R
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.ui.screen.edit.AddCourseScreen
import com.imsx3d.classy.ui.screen.schedule.ScheduleViewModel
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourseEditorFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun discardLeavesDatabaseUntouchedAndSaveCommitsOnce() = runBlocking {
        val app = SleepyApp.get()
        val repo = app.repository
        val previous = repo.getAllTables().firstOrNull { it.isDefault }?.id
        val table = repo.insertTable(TimeTableEntity(name = "Core flow fixture", startDate = "2026-08-31"))
        val row = CourseEntity(tableId = table, groupId = "core-flow", courseName = "Fixture Algebra", day = 2,
            startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#123456")
        val id = repo.insertCourse(row)
        val edited = row.copy(id = id)
        val store = ViewModelStore()
        lateinit var vm: ScheduleViewModel
        var entry by mutableIntStateOf(0)
        var visible by mutableStateOf(true)
        var loads = 0
        var saves = 0
        var backs = 0
        try {
            compose.runOnIdle { vm = ScheduleViewModel(); store.put("fixture", vm); vm.selectTable(table) }
            compose.waitUntil(5000) { vm.state.value.effectiveCurrentTable?.id == table }
            compose.setContent { SleepyThemeProvider { if (visible) key(entry) {
                AddCourseScreen(onBack = { backs++; visible = false }, onSaved = { saves++; visible = false },
                    editingCourse = edited, viewModel = vm, loadGroupCourses = { tableId, groupId ->
                        loads++
                        if (loads == 1) error("synthetic read failure")
                        repo.getGroupCourses(tableId, groupId)
                    })
            } } }
            fun rename(name: String) {
                compose.waitUntil(5000) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
                compose.onNode(hasScrollAction()).performScrollToNode(hasSetTextAction() and hasText("Fixture Algebra"))
                compose.onNode(hasSetTextAction() and hasText("Fixture Algebra")).performTextReplacement(name)
            }
            compose.waitUntil(5000) { compose.onAllNodesWithText(app.getString(R.string.course_load_failed)).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            compose.onNodeWithText(app.getString(R.string.update_retry)).performClick()
            rename("Discarded name")
            compose.onNode(hasScrollAction()).performScrollToIndex(0)
            compose.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
            compose.onNodeWithText(app.getString(R.string.unsaved_keep)).performClick()
            assertEquals("Fixture Algebra", repo.getCourse(id)!!.courseName)
            compose.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
            compose.onNodeWithText(app.getString(R.string.unsaved_discard)).performClick()
            compose.runOnIdle { assertEquals(1, backs); assertEquals(0, saves); entry++; visible = true }
            rename("Saved name")
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(app.getString(R.string.save_course)))
            compose.onNodeWithText(app.getString(R.string.save_course)).performClick()
            compose.waitUntil(5000) { saves == 1 }
            assertEquals(listOf("Saved name"), repo.getCourses(table).map { it.courseName })
        } finally {
            compose.runOnIdle { visible = false; store.clear() }
            repo.deleteTable(table)
            previous?.let { repo.setDefault(it) }
        }
    }
}
