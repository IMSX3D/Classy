package com.imsx3d.classy.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportedLanguagesTest {
    @Test fun `old Chinese preferences use simplified Chinese`() {
        listOf(null, "zh-CN", "zh-TW", "zh-Hant-HK").forEach {
            assertEquals("zh-CN", SupportedLanguages.normalize(it))
        }
    }

    @Test fun `removed and unknown non Chinese languages use English`() {
        listOf("en", "en-US", "ja", "es", "fr").forEach {
            assertEquals("en", SupportedLanguages.normalize(it))
        }
    }
}
