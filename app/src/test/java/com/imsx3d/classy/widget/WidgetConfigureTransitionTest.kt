package com.imsx3d.classy.widget

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class WidgetConfigureTransitionTest {
    @Test fun `only a visible reedit installs task animations`() {
        val source = File("src/main/java/com/imsx3d/classy/widget/WidgetConfigureActivity.kt").readText()
        val firstAdd = source.indexOf("if (WidgetConfigureCore.isFirstAdd(existingBinding))")
        val install = source.indexOf("TaskActivityTransitions.install(this)")
        assertTrue(firstAdd >= 0 && install > firstAdd)
        assertTrue(source.substring(firstAdd, install).contains("return"))
        assertTrue(source.contains("if (showsEditor) com.imsx3d.classy.ui.nav.TaskActivityTransitions.afterFinish(this)"))
    }
}
