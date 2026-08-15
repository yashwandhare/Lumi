package com.trace.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF151515)
val DarkSurface = Color(0xFF20201F)
val DarkSurfaceVariant = Color(0xFF2C2C2B)
val TextLight = Color(0xFFF0EFEC)
val SlimeBlue = Color(0xFF4DB6AC)
val SlimeBlueLight = Color(0xFF80CBC4)

internal val TraceDarkColorScheme = darkColorScheme(
    primary = SlimeBlue,
    onPrimary = DarkBackground,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = TextLight,
    secondary = SlimeBlueLight,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = SlimeBlue,
    background = DarkBackground,
    onBackground = TextLight,
    surface = DarkSurface,
    onSurface = TextLight,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextLight,
    outline = DarkSurfaceVariant,
    outlineVariant = DarkSurfaceVariant,
    error = Color(0xFFCF6679),
    onError = DarkBackground,
)

val LightBackground = Color(0xFFFAFBF7)
val LightSurfaceVariant = Color(0xFFE4E7DF)
val TextDark = Color(0xFF171717)
val SumiGreen = Color(0xFF2A4032)
val SumiRed = Color(0xFF5C2A2A)
val ZenIndigo = Color(0xFF25354A)

internal val TraceLightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = SlimeBlue,
    onPrimary = LightBackground,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = TextDark,
    secondary = TextDark,
    onSecondary = LightBackground,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = TextDark,
    background = LightBackground,
    onBackground = TextDark,
    surface = LightBackground,
    onSurface = TextDark,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextDark,
    outline = LightSurfaceVariant,
    outlineVariant = LightSurfaceVariant,
    error = SumiRed,
    onError = LightBackground,
)
