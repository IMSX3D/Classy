package com.imsx3d.classy.ui.screen.imports

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import com.imsx3d.classy.ui.nav.ForwardDuration
import com.imsx3d.classy.ui.nav.ForwardFadeIn

/** Match page navigation timing without changing the app-wide expressive motion scheme. */
internal object ImportSheetMotion : MotionScheme by MotionScheme.expressive() {
    // Material3 BottomSheet uses defaultSpatial for opening and drag settling.
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = ForwardDuration, easing = FastOutSlowInEasing)

    // Material3 BottomSheet uses fastEffects for hiding, despite the spatial movement.
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = ForwardDuration, easing = FastOutSlowInEasing)

    // The scrim should appear promptly rather than outlast the sheet's movement.
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = ForwardFadeIn, easing = FastOutSlowInEasing)
}
