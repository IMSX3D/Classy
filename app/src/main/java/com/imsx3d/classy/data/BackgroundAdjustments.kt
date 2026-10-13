package com.imsx3d.classy.data

import android.content.Context

/** Values affect the wallpaper layer only. Older installs retain their original appearance. */
data class BackgroundAdjustments(
    val visibility: Float = .18f,
    val softness: Float = 0f,
    val saturation: Float = 1f,
    val horizontal: Float = .5f,
    val vertical: Float = .5f,
) {
    fun bounded() = BackgroundAdjustments(
        visibility.valid(.18f, 0f, .65f), softness.valid(0f, 0f, 1f),
        saturation.valid(1f, 0f, 1.5f), horizontal.valid(.5f, 0f, 1f), vertical.valid(.5f, 0f, 1f))

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("schedule_background_adjustments", Context.MODE_PRIVATE)
        fun read(context: Context): BackgroundAdjustments {
            val p = prefs(context)
            return BackgroundAdjustments(p.getFloat("visibility", .18f), p.getFloat("softness", 0f),
                p.getFloat("saturation", 1f), p.getFloat("horizontal", .5f), p.getFloat("vertical", .5f)).bounded()
        }
        fun save(context: Context, value: BackgroundAdjustments) {
            val v = value.bounded()
            prefs(context).edit().putFloat("visibility", v.visibility).putFloat("softness", v.softness)
                .putFloat("saturation", v.saturation).putFloat("horizontal", v.horizontal)
                .putFloat("vertical", v.vertical).apply()
            ScheduleBackgroundStore.revision.value++
        }
    }
}

private fun Float.valid(fallback: Float, min: Float, max: Float) = if (isFinite()) coerceIn(min, max) else fallback
