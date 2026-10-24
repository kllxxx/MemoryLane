package com.kllxx.memorylane.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand colors
val BritishRacingGreen = Color(0xFF054A29)
val DarkSpringGreen = Color(0xFF137547)
val Emerald = Color(0xFF5BBA6F)
val Licorice = Color(0xFF230903)
val BabyPowder = Color(0xFFFCFFFC)
val RedCrayola = Color(0xFFEE204E)
val EmptyTile = Color(0xFFEAEAEA)
val LightGray = Color.LightGray.copy(red = 0.6f, blue = 0.6f, green = 0.6f)

// M3 generated (seed #137547)
val Green10 = Color(0xFF002110)
val Green20 = Color(0xFF00391F)
val Green30 = Color(0xFF0C5130)
val Green40 = Color(0xFF2B6A46)
val Green80 = Color(0xFF94D5A9)
val Green90 = Color(0xFFAFF2C4)
val DarkBackground = Color(0xFF0F1511)
val DarkSurface = Color(0xFF1C211D)

internal val LightColorScheme = lightColorScheme(
    primary = DarkSpringGreen,
    onPrimary = BabyPowder,
    secondary = Emerald,
    onSecondary = BabyPowder,
    tertiary = BritishRacingGreen,
    onTertiary = BabyPowder,
    error = RedCrayola,
    onError = BabyPowder,
    background = DarkSpringGreen,
    onBackground = LightGray,
    surface = BabyPowder,
    onSurface = Licorice,
    primaryContainer = Color(0xFFE8F5EC),
    onPrimaryContainer = BritishRacingGreen,
    surfaceVariant = Color(0xFFF2F7F3),
    onSurfaceVariant = Color(0xFF5A7060),
    outlineVariant = Color(0xFFD0E4D4),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Color(0xFFB5CCBA),
    onSecondary = Color(0xFF213528),
    tertiary = Color(0xFFA3CDDB),
    onTertiary = Color(0xFF033641),
    error = RedCrayola,
    onError = Color(0xFF690005),
    background = DarkBackground,
    onBackground = Color(0xFFDFE4DD),
    surface = DarkBackground,
    onSurface = Color(0xFFDFE4DD),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outlineVariant = Color(0xFF5A645B),
)
