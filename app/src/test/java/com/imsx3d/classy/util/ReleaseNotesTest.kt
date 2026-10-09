package com.imsx3d.classy.util

import com.imsx3d.classy.BuildConfig
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ReleaseNotesTest {
    @Test fun `fresh install does not show an update`() {
        assertFalse(ReleaseNotesPolicy.shouldShow(9, null, false))
    }
    @Test fun `upgrade from old app without marker shows notes`() {
        assertTrue(ReleaseNotesPolicy.shouldShow(9, null, true))
    }
    @Test fun `acknowledged version stays dismissed on reopen and recreation`() {
        assertFalse(ReleaseNotesPolicy.shouldShow(9, 9, true))
    }
    @Test fun `every later upgrade including skipped versions shows notes`() {
        assertTrue(ReleaseNotesPolicy.shouldShow(10, 9, true))
        assertTrue(ReleaseNotesPolicy.shouldShow(12, 9, true))
    }
    @Test fun `downgrade does not show upgrade notes`() {
        assertFalse(ReleaseNotesPolicy.shouldShow(8, 9, true))
    }
    private fun json() = File(System.getProperty("sleepy.test.root"), "app/src/main/assets/release-notes.json").readText()
    @Test fun `bundled notes match release and cover all supported languages`() {
        listOf("zh-CN", "zh-TW", "en", "es", "ja").forEach {
            val notes = ReleaseNotes.parse(json(), Locale.forLanguageTag(it))
            assertEquals(BuildConfig.VERSION_CODE.toLong(), notes.versionCode)
            assertEquals(BuildConfig.VERSION_NAME.removeSuffix("-debug"), notes.versionName)
            assertTrue(notes.items.isNotEmpty())
            assertTrue(notes.items.all(String::isNotBlank))
        }
    }
    @Test fun `traditional Chinese and fallback language select readable notes`() {
        assertEquals(ReleaseNotes.parse(json(), Locale.TAIWAN).items,
            ReleaseNotes.parse(json(), Locale.forLanguageTag("zh-Hant-HK")).items)
        assertEquals(ReleaseNotes.parse(json(), Locale.ENGLISH).items,
            ReleaseNotes.parse(json(), Locale.FRENCH).items)
        assertNotEquals(ReleaseNotes.parse(json(), Locale.CHINA).items,
            ReleaseNotes.parse(json(), Locale.TAIWAN).items)
    }
}
