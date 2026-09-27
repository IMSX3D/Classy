package com.nevoit.glasense.theme.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import com.nevoit.glasense.theme.GlasenseLightPalette
import com.nevoit.glasense.theme.GlasenseTypeStandard

val LocalContentColor = compositionLocalOf { GlasenseLightPalette.content }

val LocalTextStyle = compositionLocalOf { GlasenseTypeStandard.body }

val LocalType = staticCompositionLocalOf { GlasenseTypeStandard }

@Composable
fun ProvideTextStyle(
    value: TextStyle,
    content: @Composable () -> Unit
) {
    val mergedStyle = LocalTextStyle.current.merge(value)
    CompositionLocalProvider(
        LocalTextStyle provides mergedStyle,
        content = content
    )
}