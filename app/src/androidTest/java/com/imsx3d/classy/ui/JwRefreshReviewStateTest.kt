package com.imsx3d.classy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.R
import com.imsx3d.classy.data.AppDatabase
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.data.jw.JwSchoolInfo
import com.imsx3d.classy.data.repository.ScheduleRepository
import com.imsx3d.classy.ui.screen.imports.JwRefreshReviewPage
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class JwRefreshReviewStateTest {
    @get:Rule val compose = createComposeRule()
    @Test fun deselectedAdditionAndTermConfirmationSurviveSavedStateRestoration(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val id = db.timeTableDao().insert(TimeTableEntity(name = "Review fixture", startDate = "2026-08-31"))
            val rows = listOf(CourseEntity(tableId = id, groupId = "", courseName = "Algebra",
                day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "#123456"))
            val school = JwSchoolInfo("t", "School", "https://example.org", "test")
            val restore = StateRestorationTester(compose)
            restore.setContent {
                SleepyThemeProvider {
                    JwRefreshReviewPage(id, school, rows, {}, {}, ScheduleRepository(db))
                }
            }
            val addition = context.getString(R.string.jw_refresh_add, "Algebra")
            val term = context.getString(R.string.jw_refresh_term, "School", "2026-08-31")
            compose.waitUntil(5000) { compose.onAllNodesWithText("Review fixture").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(addition))
            compose.onNodeWithText(addition).performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(term))
            compose.onNodeWithText(term).performClick()
            compose.onNodeWithText(context.getString(R.string.jw_refresh_apply)).assertIsEnabled()
            restore.emulateSavedInstanceStateRestore()
            compose.waitUntil(5000) { compose.onAllNodesWithText(term).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(term).assertIsOn()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(addition))
            compose.onNodeWithText(addition).assertIsOff()
            compose.onNodeWithText(context.getString(R.string.jw_refresh_apply)).assertIsEnabled()
        } finally { db.close() }
    }
}
