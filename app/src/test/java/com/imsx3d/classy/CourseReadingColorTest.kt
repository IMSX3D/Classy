package com.imsx3d.classy

import androidx.compose.ui.graphics.Color
import com.imsx3d.classy.util.CourseColorUtil
import org.junit.Assert.*
import org.junit.Test

class CourseReadingColorTest {
    @Test fun normalTextRetainsContrastAcrossSaturatedAndNeutralSources() {
        for (dark in listOf(false, true)) for (r in listOf(0f, .25f, .5f, .75f, 1f))
            for (g in listOf(0f, .25f, .5f, .75f, 1f)) for (b in listOf(0f, .25f, .5f, .75f, 1f)) {
                val source = Color(r, g, b, .5f)
                val fill = CourseColorUtil.displayFill(source, dark)
                val fg = CourseColorUtil.textColorOn(fill, dark, if (dark) Color(0xFFECECF0) else Color(0xFF202126))
                assertEquals(1f, fill.alpha, 0f)
                assertTrue("dark=$dark rgb=$r,$g,$b", CourseColorUtil.contrastRatio(fill, fg) >= 4.5f)
                assertEquals("Source alpha must remain unchanged", .5f, source.alpha, .01f)
            }
    }
    @Test fun midToneBackgroundChoosesContrastRatherThanBrightnessThreshold() {
        val bg = Color(0xFF888888)
        val fg = CourseColorUtil.textColorOn(bg, false, Color(0xFF555555))
        assertEquals(Color.Black, fg)
        assertTrue(CourseColorUtil.contrastRatio(bg, fg) >= 4.5f)
    }
}
