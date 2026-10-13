package com.imsx3d.classy.ui

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.R
import com.imsx3d.classy.debug.EditorLifecycleTestActivity
import com.imsx3d.classy.ui.screen.edit.CourseEditorOperations
import com.imsx3d.classy.ui.screen.edit.CourseDeleteConfirmation
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EditorWriteLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun saveSurvivesRealActivityRecreationAndCompletesOnce() = exercise(false)
    @Test fun failedDeleteSurvivesRealActivityRecreationAndRetriesOnce() = exercise(true)

    private fun exercise(delete: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gate = CompletableDeferred<Unit>()
        lateinit var operations: CourseEditorOperations
        var completions = 0
        var writes = 0
        EditorLifecycleTestActivity.content = {
            operations = viewModel()
            val current = operations
            LaunchedEffect(current.result?.first) {
                current.result?.let { (id, _) -> completions++; current.consume(id) }
            }
            SleepyThemeProvider {
                if (delete) CourseDeleteConfirmation("Lifecycle fixture", current.deletion, {}, {
                    current.delete { writes++; if (writes == 1) { gate.await(); error("synthetic") } }
                }) else Text(if (current.save.busy) "Saving fixture" else "Ready")
            }
        }
        val scenario = ActivityScenario.launch<EditorLifecycleTestActivity>(Intent(context, EditorLifecycleTestActivity::class.java))
        try {
            compose.waitForIdle()
            val original = operations
            if (delete) compose.onNodeWithText(context.getString(R.string.delete)).performClick()
            else compose.runOnIdle { original.save { writes++; gate.await(); emptyList() } }
            scenario.recreate()
            compose.waitForIdle()
            compose.runOnIdle {
                assertSame(original, operations)
                assertEquals(1, writes)
                if (delete) operations.delete { writes++ } else operations.save { writes++; emptyList() }
                assertEquals(1, writes)
            }
            if (delete) {
                compose.onNodeWithText(context.getString(R.string.cancel)).assertIsNotEnabled()
                compose.onNodeWithText(context.getString(R.string.course_deleting)).assertIsDisplayed()
            } else compose.onNodeWithText("Saving fixture").assertIsDisplayed()
            compose.runOnIdle { gate.complete(Unit) }
            if (delete) {
                compose.waitUntil(5000) { operations.deletion.error != null }
                scenario.recreate()
                compose.onNodeWithText(context.getString(R.string.course_delete_failed)).assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.delete)).performClick()
            }
            compose.waitUntil(5000) { completions == 1 }
            scenario.recreate()
            compose.runOnIdle {
                operations.recoverCommittedCreation()
                operations.save { writes++; emptyList() }
                operations.delete { writes++ }
                assertNull(operations.result)
                assertEquals(1, completions)
                assertEquals(if (delete) 2 else 1, writes)
            }
        } finally { scenario.close(); EditorLifecycleTestActivity.content = null }
    }
}
