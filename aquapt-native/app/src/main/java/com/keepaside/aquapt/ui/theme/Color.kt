package com.keepaside.aquapt.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val BrandSeed = Color(0xFF2D8FFF)

val AquaPTLightColorScheme = lightColorScheme(
    primary = BrandSeed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF56606F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F4),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF705575),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFAD8FD),
    onTertiaryContainer = Color(0xFF28132F),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E)
)

val AquaPTDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468A),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6D8),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4757),
    onSecondaryContainer = Color(0xFFDAE2F4),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2846),
    tertiaryContainer = Color(0xFF573E5D),
    onTertiaryContainer = Color(0xFFFAD8FD),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6CF)
)
