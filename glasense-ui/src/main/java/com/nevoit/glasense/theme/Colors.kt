package com.nevoit.glasense.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.nevoit.glasense.theme.tokens.Blue500
import com.nevoit.glasense.theme.tokens.Green500
import com.nevoit.glasense.theme.tokens.Red500
import com.nevoit.glasense.theme.tokens.Yellow500

@Immutable
data class GlasenseColors(
    val background: Color,

    // for switches
    val activeTrack: Color,
    val inactiveTrack: Color,
    val activeThumb: Color,
    val inactiveThumb: Color,

    // scrim
    val scrimLight: Color,
    val scrimNormal: Color,
    val scrimMedium: Color,
    val scrimBold: Color,

    // for hierarchical cards
    val pageBackground: Color,
    val cardBackground: Color,

    // for bottom sheets
    val elevatedPageBackground: Color,
    val elevatedCardBackground: Color,

    val primary: Color,
    val onPrimary: Color,

    // main font colors
    val content: Color,
    val contentVariant: Color,

    val highlightText: Color,

    // error color
    val error: Color,
    val onError: Color,

    // for segmented control
    val segmentedControlBackground: Color = scrimNormal,
    val onSegmentedControlBackground: Color = contentVariant,
    val segmentedControlIndicator: Color,
    val onSegmentedControlIndicator: Color = content,
    val shadow: Color
)

val GlasenseLightPalette = GlasenseColors(
    background = Color.White,
    activeTrack = Green500,
    inactiveTrack = Color(0xFF787880).copy(.25f),
    activeThumb = Color.White,
    inactiveThumb = Color.White,
    pageBackground = Color(0xFFF3F4F6),
    cardBackground = Color.White,
    elevatedPageBackground = Color(0xFFF3F4F6),
    elevatedCardBackground = Color.White,
    scrimLight = Color.Black.copy(alpha = 0.025f),
    scrimNormal = Color.Black.copy(alpha = 0.05f),
    scrimMedium = Color.Black.copy(alpha = 0.1f),
    scrimBold = Color.Black.copy(alpha = 0.2f),
    primary = Blue500,
    onPrimary = Color.White,
    content = Color.Black,
    contentVariant = Color.Black.copy(.5f),
    highlightText = Yellow500,
    error = Red500,
    onError = Color.White,
    segmentedControlIndicator = Color.White,
    shadow = Color.Black.copy(.5f)
)

val GlasenseDarkPalette = GlasenseColors(
    background = Color.Black,
    activeTrack = Green500,
    inactiveTrack = Color(0xFF787880).copy(.25f),
    activeThumb = Color.White,
    inactiveThumb = Color.White,
    pageBackground = Color.Black,
    cardBackground = Color(0xFF1B1C1D),
    elevatedPageBackground = Color(0xFF1C1C1E),
    elevatedCardBackground = Color(0xFF2C2C2E),
    scrimLight = Color.White.copy(alpha = 0.05f),
    scrimNormal = Color.White.copy(alpha = 0.1f),
    scrimMedium = Color.White.copy(alpha = 0.2f),
    scrimBold = Color.White.copy(alpha = 0.4f),
    primary = Blue500,
    onPrimary = Color.White,
    content = Color.White,
    contentVariant = Color.White.copy(.5f),
    highlightText = Yellow500,
    error = Red500,
    onError = Color.White,
    segmentedControlIndicator = Color(0xFF636366),
    shadow = Color.Black
)