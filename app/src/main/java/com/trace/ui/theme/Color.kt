package com.trace.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Dark palette — DESIGN_LANGUAGE.md §2.
val DarkBackground = Color(0xFF151515)
val DarkSurface = Color(0xFF20201F)
val DarkSurfaceVariant = Color(0xFF2C2C2B)
val TextLight = Color(0xFFF0EFEC)
val DarkError = Color(0xFFCF6679)

/**
 * The one accent, used in both modes. §2: "The Slime Blue is the **only** accent colour."
 *
 * It appears on the mascot, the Send button fill, the selected drawer row, and the launcher icon.
 * Nowhere else. The app is otherwise monochrome.
 */
val SlimeBlue = Color(0xFF4DB6AC)
val SlimeBlueLight = Color(0xFF80CBC4)

/**
 * The mascot's body, per `DESIGN_LANGUAGE.md` §6.
 *
 * §6 specifies a *reversed* radial gradient: a deep teal core fading outwards through the primary
 * cyan to a glowing light-mint edge. Reversed because the usual glossy sphere is lit from outside
 * in; lighting it from the edge inwards is what makes it read as a translucent water bubble rather
 * than a plastic ball.
 *
 * These are mascot anatomy, not palette entries, and they are the one place in the app outside the
 * accent rule — §2 permits it because the mascot is where the accent lives.
 */
val MascotCore = Color(0xFF00838F)
val MascotEdge = Color(0xFFE0F7FA)

/** §6 anger state. The body turns deep red and shakes. */
val MascotAngry = Color(0xFFCF6679)
val MascotAngryCore = Color(0xFF7A1C1C)
val MascotAngryEdge = Color(0xFFE57373)

/** §6: two small white eyes, in both themes. Not `onSurface` — the eyes are always white. */
val MascotEye = Color(0xFFFFFFFF)

// Light palette — DESIGN_LANGUAGE.md §2. Surface and Surface Variant share one value there.
val LightBackground = Color(0xFFFAFBF7)
val LightSurface = Color(0xFFE4E7DF)
val LightSurfaceVariant = Color(0xFFE4E7DF)
val TextDark = Color(0xFF171717)
val LightError = Color(0xFF5C2A2A)

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
    outline = TextLight.copy(alpha = 0.30f),
    outlineVariant = TextLight.copy(alpha = 0.12f),
    error = DarkError,
    onError = DarkBackground,
)

internal val TraceLightColorScheme = lightColorScheme(
    primary = SlimeBlue,
    /**
     * Deviates from §2's light table, which specifies `#FAFBF7` here.
     *
     * White on the accent measures 2.35:1; WCAG AA needs 4.5:1 for text and 3:1 for a graphical
     * object, so a white arrow on the cyan Send button is unreadable to a low-vision user.
     * `#171717` on the same fill measures 7.35:1. The accent hue itself is untouched.
     * See `decisions_devb.md`.
     */
    onPrimary = TextDark,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = TextDark,
    secondary = SlimeBlueLight,
    onSecondary = TextDark,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = TextDark,
    background = LightBackground,
    onBackground = TextDark,
    surface = LightSurface,
    onSurface = TextDark,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextDark,
    /**
     * Not `surfaceVariant`, which is what this used to be.
     *
     * An outline the same colour as the surface it sits on measured 1.20:1 — a stock
     * `OutlinedTextField` was drawing a border nobody could see. An alpha of the on-colour keeps
     * the app monochrome while staying visible against every surface in the palette.
     */
    outline = TextDark.copy(alpha = 0.30f),
    outlineVariant = TextDark.copy(alpha = 0.12f),
    error = LightError,
    onError = LightBackground,
)
