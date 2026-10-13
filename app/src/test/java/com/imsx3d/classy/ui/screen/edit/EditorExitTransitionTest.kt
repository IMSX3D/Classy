package com.imsx3d.classy.ui.screen.edit

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Wiring regressions for outgoing form reset and unconditional back interception. */
class EditorExitTransitionTest {
    private fun read(path: String) = File("src/main/java/com/imsx3d/classy/ui/$path").readText()
    @Test fun `outgoing course snapshot does not track session clearing`() {
        val host = read("nav/SleepyNavHost.kt")
        assertTrue(host.contains("remember(editing, courseId)"))
        assertFalse(host.contains("remember(editing, courseId, session.editingCourse?.id)"))
        assertTrue(host.contains("DisposableEffect(key)"))
    }
    @Test fun `clean editor exits are left to navigation`() {
        val course = read("screen/edit/AddCourseScreen.kt")
        assertTrue(course.contains("BackHandler(enabled = saving || deletion.busy || draftFingerprint() != originalDraft)"))
        for (name in listOf("EditTableScreen", "PeriodTableEditScreen")) {
            val source = read("screen/mine/$name.kt")
            assertTrue(source.contains("enabled = actions.busy || draft != originalDraft ||"))
        }
    }
    @Test fun `theme overlay has an exit transition and retained identity`() {
        val source = read("screen/mine/AppearanceScreen.kt")
        assertTrue(source.contains("visible = showEditor"))
        assertTrue(source.contains("exit = com.imsx3d.classy.ui.nav.sleepyPopExit()"))
        assertTrue(source.contains("val editorTheme = remember { editingTheme }"))
    }
}
