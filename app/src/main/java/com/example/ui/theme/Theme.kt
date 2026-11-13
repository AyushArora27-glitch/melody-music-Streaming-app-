package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MelodyDarkColorScheme =
  darkColorScheme(
    primary = MelodyLime,
    onPrimary = MelodyBg,
    primaryContainer = MelodyHeroBg,
    onPrimaryContainer = MelodyTextPrimary,
    secondary = MelodyPurple,
    onSecondary = MelodyBg,
    secondaryContainer = MelodyPanelElevated,
    onSecondaryContainer = MelodyTextPrimary,
    tertiary = MelodyCyan,
    onTertiary = MelodyBg,
    background = MelodyBg,
    onBackground = MelodyTextPrimary,
    surface = MelodyPanel,
    onSurface = MelodyTextPrimary,
    surfaceVariant = MelodyPanelElevated,
    onSurfaceVariant = MelodyTextMuted,
    outline = MelodyLine,
    outlineVariant = MelodyLine,
    error = MelodyError,
    onError = MelodyBg,
  )

val MelodyShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun MelodyTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = MelodyDarkColorScheme,
    typography = MelodyTypography,
    shapes = MelodyShapes,
    content = content,
  )
}
