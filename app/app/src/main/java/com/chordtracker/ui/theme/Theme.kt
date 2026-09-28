package com.chordtracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ChordColorScheme = darkColorScheme(
    primary = ChordPrimary,
    onPrimary = ChordBackground,
    secondary = ChordPrimaryDim,
    background = ChordBackground,
    onBackground = ChordText,
    surface = ChordSurface,
    onSurface = ChordText,
    surfaceVariant = ChordSurfaceAlt,
    onSurfaceVariant = ChordMuted,
    outline = ChordBorder,
    error = ChordError,
)

private val ChordTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
    )
}

val WordmarkStyle = TextStyle(
    fontSize = 22.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.3.sp,
)

// Always dark, regardless of system theme, to match the web dashboard.
@Composable
fun ChordTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChordColorScheme,
        typography = ChordTypography,
        content = content,
    )
}
