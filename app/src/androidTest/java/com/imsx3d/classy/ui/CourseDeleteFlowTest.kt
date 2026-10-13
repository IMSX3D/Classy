package com.imsx3d.classy.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.EditorActionState
import com.imsx3d.classy.ui.screen.edit.CourseDeleteConfirmation
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourseDeleteFlowTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun failureKeepsConfirmationAndRetrySerializesClicks() {
        val first = CompletableDeferred<Unit>()
        val retry = CompletableDeferred<Unit>()
        var attempts = 0
        var finished = 0
        var dismissed = 0
        lateinit var action: EditorActionState
        lateinit var confirm: () -> Unit
        compose.setContent {
            val scope = rememberCoroutineScope()
            action = remember { EditorActionState(text(R.string.course_delete_failed)) }
            confirm = { action.launch(scope) {
                attempts++
                if (attempts == 1) { first.await(); error("synthetic database failure") }
                retry.await(); finished++
            } }
            SleepyThemeProvider { CourseDeleteConfirmation("Fixture", action, { dismissed++ }, confirm) }
        }
        compose.onNodeWithText(text(R.string.delete)).performClick()
        compose.onNodeWithText(text(R.string.course_deleting)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.cancel)).assertIsNotEnabled()
        androidx.test.espresso.Espresso.pressBackUnconditionally()
        compose.onNodeWithText(text(R.string.course_deleting)).assertIsDisplayed()
        compose.runOnIdle { confirm(); assertEquals(1, attempts); first.complete(Unit) }
        compose.waitUntil { action.error != null }
        compose.onNodeWithText(text(R.string.course_delete_failed)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, finished); assertEquals(0, dismissed) }
        compose.onNodeWithText(text(R.string.delete)).performClick()
        compose.runOnIdle { confirm(); assertEquals(2, attempts); retry.complete(Unit) }
        compose.waitUntil { finished == 1 }
        compose.runOnIdle { assertNull(action.error); assertFalse(action.busy) }
    }

    @Test fun cancelDoesNotDelete() {
        var deletes = 0
        var cancels = 0
        compose.setContent {
            val action = remember { EditorActionState("failure") }
            SleepyThemeProvider { CourseDeleteConfirmation("Fixture", action, { cancels++ }, { deletes++ }) }
        }
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(1, cancels); assertEquals(0, deletes) }
    }
}
