package com.imsx3d.classy.widget

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPreviewRegistrarTest {

    @Test
    fun `pre Android 15 uses XML preview fallback without registration`() {
        assertEquals(false, WidgetPreviewRegistrar.shouldRegisterGeneratedPreview(34))
        assertEquals(true, WidgetPreviewRegistrar.shouldRegisterGeneratedPreview(35))
    }

    @Test
    fun `provider matrix remains complete for generated previews`() {
        assertTrue(ALL_WIDGET_VARIANTS.size >= 13)
        ALL_WIDGET_VARIANTS.forEach { assertTrue(it.receiverClass.name.isNotBlank()) }
    }
}
