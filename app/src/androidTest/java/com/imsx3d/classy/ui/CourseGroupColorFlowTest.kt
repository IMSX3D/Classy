package com.imsx3d.classy.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.ViewModelStore
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.R
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.ui.screen.edit.AddCourseScreen
import com.imsx3d.classy.ui.screen.schedule.ScheduleViewModel
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourseGroupColorFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pendingColorAndDraftRestoreFailureCanRetryAndSaveKeepsColor() = runBlocking {
        val app = SleepyApp.get()
        val repo = app.repository
        val previous = repo.getAllTables().firstOrNull { it.isDefault }?.id
        val table = repo.insertTable(TimeTableEntity(name = "Color flow fixture", startDate = "2026-08-31"))
        val row = CourseEntity(tableId = table, groupId = "color-flow", courseName = "Color Algebra", day = 2,
            startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF123456")
        val edited = row.copy(id = repo.insertCourse(row))
        val store = ViewModelStore()
        lateinit var vm: ScheduleViewModel
        var visible by mutableStateOf(true)
        var attempts = 0
        var saves = 0
        val gate = CompletableDeferred<Unit>()
        val restore = StateRestorationTester(compose)
        fun label(id: Int) = app.getString(id)
        try {
            compose.runOnIdle { vm = ScheduleViewModel(); store.put("fixture", vm); vm.selectTable(table) }
            compose.waitUntil(5000) { vm.state.value.effectiveCurrentTable?.id == table }
            restore.setContent { SleepyThemeProvider { if (visible) {
                AddCourseScreen(onBack = { visible = false }, onSaved = { saves++; visible = false },
                    editingCourse = edited, viewModel = vm, writeGroupColor = { tid, gid, hex ->
                        attempts++
                        if (attempts == 1) { gate.await(); error("synthetic failure") }
                        repo.setGroupSourceColor(tid, gid, hex)
                    })
            } } }
            compose.waitUntil(5000) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollAction()).performScrollToNode(hasSetTextAction() and hasText("Color Algebra"))
            compose.onNode(hasSetTextAction() and hasText("Color Algebra")).performTextReplacement("Color draft")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(label(R.string.change_group_color)))
            compose.onNodeWithText(label(R.string.change_group_color)).performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasText(label(R.string.ok)) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText(label(R.string.ok)) and hasAnyAncestor(isDialog())).performClick()
            restore.emulateSavedInstanceStateRestore()
            compose.onNode(hasText(label(R.string.group_color_confirm_title)) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNodeWithText(label(R.string.action_confirm)).performClick()
            compose.onNodeWithText(label(R.string.move_saving)).assertIsNotEnabled()
            compose.onNodeWithText(label(R.string.cancel)).assertIsNotEnabled()
            restore.emulateSavedInstanceStateRestore()
            compose.onNodeWithText(label(R.string.move_saving)).assertIsNotEnabled()
            compose.runOnIdle { assertEquals(1, attempts); gate.complete(Unit) }
            compose.waitUntil(5000) { compose.onAllNodesWithText(label(R.string.group_color_failed)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(label(R.string.group_color_failed)).assertIsDisplayed()
            compose.onNodeWithText(label(R.string.action_confirm)).assertIsDisplayed()
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureEvidence") == "true") {
                val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                java.io.File(app.getExternalFilesDir(null), "editor-group-failure.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            compose.onNodeWithText(label(R.string.action_confirm)).performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasText(label(R.string.group_color_confirm_title)) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isEmpty() }
            assertEquals(2, attempts)
            val committed = repo.getCourse(edited.id)!!.color
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(label(R.string.save_course)))
            compose.onNodeWithText(label(R.string.save_course)).performClick()
            compose.waitUntil(5000) { saves == 1 }
            assertEquals("Color draft", repo.getCourses(table).single().courseName)
            assertEquals(committed, repo.getCourses(table).single().color)
        } finally {
            compose.runOnIdle { visible = false; store.clear() }
            repo.deleteTable(table)
            previous?.let { repo.setDefault(it) }
        }
    }
}
