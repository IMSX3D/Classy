package com.imsx3d.classy.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/** Exactly two visual states. Combine reasons BEFORE rendering; never dim a dimmed color.
 * Surface, title, metadata and indicator must all start from their normal-state colors.
 * Opaque conflict layers blend against the same outside backdrop, not an already dimmed card.
 */
object CourseDimming {
    private const val DIM_OPACITY = 0.45f
    fun isDimmed(completed: Boolean, holiday: Boolean = false): Boolean = completed || holiday
    fun opacity(completed: Boolean, holiday: Boolean = false): Float =
        if (isDimmed(completed, holiday)) DIM_OPACITY else 1f

    fun color(base: Color, backdrop: Color, completed: Boolean, holiday: Boolean = false): Color =
        if (isDimmed(completed, holiday)) lerp(backdrop, base.compositeOver(backdrop), DIM_OPACITY) else base
}
