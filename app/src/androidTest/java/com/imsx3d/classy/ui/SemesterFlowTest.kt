package com.imsx3d.classy.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.ViewModelStore
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.data.entity.*
import com.imsx3d.classy.ui.screen.schedule.ScheduleViewModel
import com.imsx3d.classy.ui.screen.mine.SemesterScreen
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SemesterFlowTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun courseSelectionShowsActualIrregularTimeAndRoom() = runBlocking {
        val repo = SleepyApp.get().repository
        val previous = repo.getAllTables().firstOrNull { it.isDefault }?.id
        val table = repo.insertTable(TimeTableEntity(name = "Semester fixture", startDate = "2026-09-07", maxWeek = 20))
        val id = repo.insertCourse(CourseEntity(groupId = "semester-fixture", tableId = table,
            courseName = "Semester Algebra", room = "Lab 42", teacher = "Teacher A", day = 1,
            startNode = 1, step = 2, startWeek = 1, endWeek = 20, color = "",
            ownTime = true, startTime = "18:13", endTime = "19:27"))
        val store = ViewModelStore()
        lateinit var vm: ScheduleViewModel
        var selected by mutableStateOf<Long?>(null)
        try {
            compose.runOnIdle { vm = ScheduleViewModel(); store.put("semester-fixture", vm); vm.selectTable(table) }
            compose.waitUntil(5000) { !vm.state.value.loading && vm.state.value.courses.any { it.id == id } }
            val restoration = StateRestorationTester(compose)
            restoration.setContent { SleepyThemeProvider {
                key(selected) { SemesterScreen(onBack = { selected = null }, onSelect = { selected = it }, courseId = selected, vm = vm) }
            } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Semester Algebra").fetchSemanticsNodes().isNotEmpty() }
            capture("semester-overview")
            compose.onNodeWithText("Semester Algebra").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("18:13-19:27", substring = true).fetchSemanticsNodes().isNotEmpty() }
            capture("semester-calendar")
            compose.onNodeWithText("Lab 42", substring = true).performScrollTo().assertIsDisplayed()
            val first = LocalDate.of(2026, 9, 7)
            val dates = (0 until 20).map { first.plusWeeks(it.toLong()) }
            val initial = dates.firstOrNull { !it.isBefore(LocalDate.now()) } ?: first
            val emptyDay = YearMonth.from(initial).atDay(1).let { if (it.dayOfWeek.value == 1) it.plusDays(1) else it }
            compose.onNodeWithTag("semester-date-$emptyDay").performScrollTo().performClick()
            compose.onAllNodesWithText("Lab 42", substring = true).assertCountEquals(0)
            restoration.emulateSavedInstanceStateRestore()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("semester-date-$emptyDay").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("semester-date-$emptyDay").assertIsSelected()
            compose.onNodeWithTag("semester-date-$initial").performScrollTo().performClick()
            compose.onNodeWithText("Lab 42", substring = true).performScrollTo().assertIsDisplayed()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val nextLabel = context.getString(com.imsx3d.classy.R.string.semester_next_month)
            compose.onNodeWithContentDescription(nextLabel).performScrollTo().performClick()
            val nextMonth = YearMonth.from(initial).plusMonths(1)
            compose.onNodeWithTag("semester-date-${nextMonth.atDay(1)}").assertExists()
            compose.runOnIdle { assertEquals(id, selected) }
        } finally {
            compose.runOnIdle { store.clear() }
            repo.deleteTable(table)
            previous?.let { repo.setDefault(it) }
        }
    }
}
