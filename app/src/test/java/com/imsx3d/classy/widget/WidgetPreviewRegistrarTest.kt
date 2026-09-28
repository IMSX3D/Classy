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
        assertTrue(ALL_WIDGET_VARIANTS.size >= 3)
        ALL_WIDGET_VARIANTS.forEach { variant ->
            assertTrue(variant.receiverClass.name.isNotBlank())
            // UI-7：API 35+ 的生成式预览取这个布局。为空 = 选择器里一张空白卡
            //（正是用户 2026-09-28 报的「组件视图太简陋」两个根因之一）。
            assertTrue(
                "${variant.receiverClass.simpleName} 没配 previewLayoutRes",
                variant.previewLayoutRes != 0
            )
        }
    }
}
