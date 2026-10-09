package com.imsx3d.classy.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guard the upgrade path: stopping registration alone leaves old previews cached. */
class WidgetPreviewRegistrarTest {
    private val javaRoot = sequenceOf(
        File("app/src/main/java/com/imsx3d/classy"),
        File("src/main/java/com/imsx3d/classy")
    ).first { it.isDirectory }

    @Test
    fun `upgrade removes generated previews without registering replacements`() {
        val source = File(javaRoot, "widget/WidgetPreviewRegistrar.kt").readText()
        assertTrue(source.contains("manager.removeWidgetPreview("))
        assertFalse(source.contains("manager.setWidgetPreview("))
        assertTrue(source.contains("ALL_WIDGET_VARIANTS.forEach"))
        assertTrue(source.contains("WIDGET_CATEGORY_HOME_SCREEN"))
        assertTrue(source.contains("catch (error: RuntimeException)"))
        val app = File(javaRoot, "SleepyApp.kt").readText()
        assertTrue(app.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM"))
        assertTrue(app.contains("WidgetPreviewRegistrar.clearLegacyGeneratedPreviews(this@SleepyApp)"))
    }
}
