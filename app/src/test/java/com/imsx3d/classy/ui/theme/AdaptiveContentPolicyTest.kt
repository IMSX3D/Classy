package com.imsx3d.classy.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveContentPolicyTest {
    @Test fun phoneAndNarrowSplitKeepSinglePane() {
        for (width in listOf(320f, 600f, 839f)) {
            assertFalse(AdaptiveContentPolicy.showCourseDetailPane(width, 900f, 1f))
        }
    }

    @Test fun expandedWindowCanShowBothPanes() {
        assertTrue(AdaptiveContentPolicy.showCourseDetailPane(840f, 600f, 1f))
    }

    @Test fun largeTextRequiresSpaceForBothPanes() {
        assertFalse(AdaptiveContentPolicy.showCourseDetailPane(840f, 800f, 1.5f))
        assertTrue(AdaptiveContentPolicy.showCourseDetailPane(1080f, 800f, 1.5f))
    }

    @Test fun shortLandscapeWindowKeepsSinglePane() {
        assertFalse(AdaptiveContentPolicy.showCourseDetailPane(1280f, 479f, 1f))
        assertTrue(AdaptiveContentPolicy.showCourseDetailPane(1280f, 480f, 1f))
    }

    @Test fun smallFontDoesNotForceTwoPanesOnPhone() {
        assertFalse(AdaptiveContentPolicy.showCourseDetailPane(720f, 800f, 0.85f))
    }
    @Test fun timeFieldsReflowAtReadableWidth() {
        assertFalse(AdaptiveContentPolicy.showTimeFieldsSideBySide(283f, 1f))
        assertTrue(AdaptiveContentPolicy.showTimeFieldsSideBySide(284f, 1f))
        assertFalse(AdaptiveContentPolicy.showTimeFieldsSideBySide(320f, 1.5f))
        assertTrue(AdaptiveContentPolicy.showTimeFieldsSideBySide(420f, 1.5f))
        assertFalse(AdaptiveContentPolicy.showTimeFieldsSideBySide(260f, 0.85f))
    }
    @Test fun descriptiveRoutineFieldsReserveMoreLabelSpace() {
        assertFalse(AdaptiveContentPolicy.showTimeFieldsSideBySide(320f, 1f, 168f))
        assertTrue(AdaptiveContentPolicy.showTimeFieldsSideBySide(348f, 1f, 168f))
        assertFalse(AdaptiveContentPolicy.showTimeFieldsSideBySide(500f, 1.5f, 168f))
        assertTrue(AdaptiveContentPolicy.showTimeFieldsSideBySide(516f, 1.5f, 168f))
    }
}
