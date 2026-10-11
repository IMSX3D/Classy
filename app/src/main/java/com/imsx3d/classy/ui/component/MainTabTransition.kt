package com.imsx3d.classy.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex

/** Peer destinations share a fade, without the scale/depth of child navigation. */
@Composable
fun <T> MainTabTransition(selected: T, content: @Composable (T) -> Unit) {
    Crossfade(
        targetState = selected,
        modifier = Modifier.fillMaxSize(),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "Main tab content"
    ) { visibleTab ->
        val active = visibleTab == selected
        Box(
            Modifier.fillMaxSize().zIndex(if (active) 1f else 0f).then(
                if (active) Modifier else Modifier.clearAndSetSemantics { }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        ) { content(visibleTab) }
    }
}
