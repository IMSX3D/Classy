package com.imsx3d.classy.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Source-level regression for the 4dp unselected band at the rounded card edges. */
class WidgetSelectionShapeTest {
    @Test fun `grouped options use the shared clipped shell without outer row padding`() {
        for (name in listOf("WidgetEditScheduleSection", "WidgetEditAliasSection")) {
            val s = File("src/main/java/com/imsx3d/classy/ui/screen/widget/$name.kt").readText()
            assertTrue(name, s.contains("SettingsGroupCard {"))
            assertTrue(name, s.contains("Column(modifier = Modifier.selectableGroup())"))
            assertFalse(name, s.contains("Column(modifier = Modifier.padding"))
            assertTrue(name, s.contains("onPrimaryContainer"))
        }
    }
}
