package com.nevoit.glasense.core.modifier

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.nevoit.glasense.core.interaction.DimIndication

fun Modifier.nullClickable(
    enabled: Boolean = true
): Modifier {
    if (!enabled) return Modifier
    return composed {
        this.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = true,
            onClick = {}
        )
    }
}

fun Modifier.clickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = DimIndication(),
        enabled = enabled,
        onClick = onClick
    )
}