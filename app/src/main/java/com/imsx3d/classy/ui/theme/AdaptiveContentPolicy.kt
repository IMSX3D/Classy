package com.imsx3d.classy.ui.theme

/** Uses available content bounds, after navigation and system insets. */
object AdaptiveContentPolicy {
    // Reserve readable HH:mm and label space for each field, plus the shared gap.
    fun showTimeFieldsSideBySide(widthDp: Float, fontScale: Float): Boolean =
        widthDp >= 2f * 136f * fontScale.coerceAtLeast(1f) + 12f

    fun showCourseDetailPane(widthDp: Float, heightDp: Float, fontScale: Float): Boolean {
        val scale = fontScale.coerceAtLeast(1f)
        return widthDp >= maxOf(840f, 720f * scale) && heightDp >= 480f
    }
}
