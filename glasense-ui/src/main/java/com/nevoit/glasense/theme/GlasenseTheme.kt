package com.nevoit.glasense.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.nevoit.glasense.core.interaction.DimIndication
import com.nevoit.glasense.core.interaction.overscroll.rememberOffsetOverscrollFactory
import com.nevoit.glasense.theme.local.LocalColors
import com.nevoit.glasense.theme.local.LocalContentColor
import com.nevoit.glasense.theme.local.LocalDarkTheme
import com.nevoit.glasense.theme.local.LocalSpecs
import com.nevoit.glasense.theme.local.LocalTextStyle
import com.nevoit.glasense.theme.local.LocalType

object GlasenseTheme {
    val colors: GlasenseColors
        @Composable get() = LocalColors.current

    val specs: GlasenseSpecs
        @Composable get() = LocalSpecs.current

    val type: GlasenseType
        @Composable get() = LocalType.current

    val darkTheme: Boolean
        @Composable get() = LocalDarkTheme.current

    @Composable
    operator fun invoke(
        darkTheme: Boolean = resolveDarkTheme(ThemeMode.SYSTEM),
        colors: GlasenseColors = if (darkTheme) GlasenseDarkPalette else GlasenseLightPalette,
        specs: GlasenseSpecs = GlasenseSpecsStandard,
        type: GlasenseType = GlasenseTypeStandard,
        content: @Composable () -> Unit
    ) {
        val overscrollFactory = rememberOffsetOverscrollFactory()
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalSpecs provides specs,
            LocalType provides type,
            LocalTextStyle provides type.body,
            LocalContentColor provides colors.content,
            LocalDarkTheme provides darkTheme,
            LocalIndication provides DimIndication(),
            LocalOverscrollFactory provides overscrollFactory
        ) {
            content()
        }
    }
}