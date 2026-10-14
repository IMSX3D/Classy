package com.imsx3d.classy.util

import org.junit.Assert.*
import org.junit.Test

class GuidePrefsTest {
    @Test fun welcomeOnlyInterruptsANewLauncherInstallOnce() {
        assertTrue(GuidePrefs.shouldOffer(false, 100, 100, false))
        assertFalse(GuidePrefs.shouldOffer(true, 100, 100, false))
        assertFalse(GuidePrefs.shouldOffer(false, 100, 200, false))
        assertFalse(GuidePrefs.shouldOffer(false, 100, 100, true))
        assertFalse(GuidePrefs.shouldOffer(false, 0, 0, false))
    }
}
