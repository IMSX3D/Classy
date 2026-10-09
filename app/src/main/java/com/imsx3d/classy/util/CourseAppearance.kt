package com.imsx3d.classy.util

/** The three user-facing choices, decoded from the existing preferences without clearing them. */
enum class CourseAppearance(val style: String, val colorless: Boolean) {
    BAR("bar", false), FILL("fill", false), NEUTRAL("fill", true);

    companion object {
        fun fromLegacy(style: String?, colorless: Boolean): CourseAppearance = when {
            colorless -> NEUTRAL
            style == "bar" -> BAR
            else -> FILL
        }
    }
}
