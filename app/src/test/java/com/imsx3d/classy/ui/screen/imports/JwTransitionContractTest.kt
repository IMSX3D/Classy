package com.imsx3d.classy.ui.screen.imports

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Structural guards for the previously empty success frame. Native animation needs a device. */
class JwTransitionContractTest {
    private val source = File("src/main/java/com/imsx3d/classy/ui/screen/imports/JwImportActivity.kt").readText()
    @Test fun `success closes the activity without replacing the confirmation content`() {
        assertTrue(source.contains("LaunchedEffect(importFinished) { if (importFinished) finish() }"))
        assertFalse(source.contains("importFinished ->"))
        assertTrue(source.contains("confirmEnabled = !importActions.busy && !importFinished"))
        assertTrue(source.contains("if (importActions.busy || importFinished) return@DialogActionButtons"))
    }
    @Test fun `stage drawing uses retained transition target and activity owns finish animation`() {
        assertTrue(source.contains("targetState = stage"))
        assertTrue(source.contains("visibleStage is Stage.ConfigureConfirm"))
        assertTrue(source.contains("visibleStage is Stage.WebViewLogin"))
        assertTrue(source.contains("visibleStage is Stage.SelectSchool"))
        assertTrue(source.contains("TaskActivityTransitions.install(this)"))
        assertTrue(source.contains("TaskActivityTransitions.afterFinish(this)"))
    }
}
