package com.imsx3d.classy.ui.theme

/** Uses available content bounds, after navigation and system insets. */
object AdaptiveContentPolicy {
    fun showCourseDetailPane(widthDp: Float, heightDp: Float, fontScale: Float): Boolean {
        val scale = fontScale.coerceAtLeast(1f)
        return widthDp >= maxOf(840f, 720f * scale) && heightDp >= 480f
    }
}
