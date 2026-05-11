package com.samwise.unshelved.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Tonal palette seeded from the Unshelved icon green (#5d8355)
private val primary = Color(0xFF3D6B35)
private val onPrimary = Color(0xFFFFFFFF)
private val primaryContainer = Color(0xFFBEF0AD)
private val onPrimaryContainer = Color(0xFF002200)

private val secondary = Color(0xFF52634D)
private val onSecondary = Color(0xFFFFFFFF)
private val secondaryContainer = Color(0xFFD5E8CC)
private val onSecondaryContainer = Color(0xFF111F0E)

private val tertiary = Color(0xFF38656A)
private val onTertiary = Color(0xFFFFFFFF)
private val tertiaryContainer = Color(0xFFBCEBF0)
private val onTertiaryContainer = Color(0xFF002023)

private val error = Color(0xFFBA1A1A)
private val onError = Color(0xFFFFFFFF)
private val errorContainer = Color(0xFFFFDAD6)
private val onErrorContainer = Color(0xFF410002)

private val background = Color(0xFFF7FBF1)
private val onBackground = Color(0xFF181D17)
private val surface = Color(0xFFF7FBF1)
private val onSurface = Color(0xFF181D17)
private val surfaceVariant = Color(0xFFDEE5D8)
private val onSurfaceVariant = Color(0xFF424940)
private val outline = Color(0xFF72796F)
private val outlineVariant = Color(0xFFC2C9BC)
private val inverseSurface = Color(0xFF2D322C)
private val inverseOnSurface = Color(0xFFEEF2E8)
private val inversePrimary = Color(0xFFA3D393)

// Dark variants
private val primaryDark = Color(0xFFA3D393)
private val onPrimaryDark = Color(0xFF0A3908)
private val primaryContainerDark = Color(0xFF265220)
private val onPrimaryContainerDark = Color(0xFFBEF0AD)

private val secondaryDark = Color(0xFFB9CCB1)
private val onSecondaryDark = Color(0xFF253422)
private val secondaryContainerDark = Color(0xFF3B4B37)
private val onSecondaryContainerDark = Color(0xFFD5E8CC)

private val tertiaryDark = Color(0xFFA0CFD4)
private val onTertiaryDark = Color(0xFF00373C)
private val tertiaryContainerDark = Color(0xFF1E4D52)
private val onTertiaryContainerDark = Color(0xFFBCEBF0)

private val errorDark = Color(0xFFFFB4AB)
private val onErrorDark = Color(0xFF690005)
private val errorContainerDark = Color(0xFF93000A)
private val onErrorContainerDark = Color(0xFFFFDAD6)

private val backgroundDark = Color(0xFF101510)
private val onBackgroundDark = Color(0xFFE0E4DA)
private val surfaceDark = Color(0xFF101510)
private val onSurfaceDark = Color(0xFFE0E4DA)
private val surfaceVariantDark = Color(0xFF424940)
private val onSurfaceVariantDark = Color(0xFFC2C9BC)
private val outlineDark = Color(0xFF8C9388)
private val outlineVariantDark = Color(0xFF424940)
private val inverseSurfaceDark = Color(0xFFE0E4DA)
private val inverseOnSurfaceDark = Color(0xFF2D322C)
private val inversePrimaryDark = Color(0xFF3D6B35)

val LightColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    inversePrimary = inversePrimary,
)

val DarkColorScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
)
