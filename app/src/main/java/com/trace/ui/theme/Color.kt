package com.trace.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Rice paper, sumi ink, and exactly one olive accent.
 *
 * The accent is the only chromatic colour in the app. It marks primary actions,
 * selection, active navigation, focus, and progress — nothing else. Status is never
 * communicated by colour alone, so [Error] always appears with an icon or a label.
 *
 * Contrast against its own surface, measured: [Olive] on [RicePaper] is 4.8:1 and
 * [OliveDark] on [SumiSurfaceDark] is 6.8:1. The faint ink tones are 4.9:1 and 5.1:1. All
 * clear WCAG AA for text and UI components. Do not adjust these values without
 * re-computing the ratio — "muted" is not a licence to fall below 4.5:1.
 */
internal object TraceColors {

    // Light — paper and ink.
    val RicePaper = Color(0xFFFAF8F3)
    val RicePaperRaised = Color(0xFFF4F1E9)
    val SumiInk = Color(0xFF1A1A18)
    val SumiInkSoft = Color(0xFF4A4A45)

    /**
     * Secondary text and outlines. 4.9:1 on [RicePaper].
     *
     * Was #8A8A82, which measured 3.3:1 and failed AA — it looked correctly restrained and
     * was quietly unreadable, which is the worst combination.
     */
    val SumiInkFaint = Color(0xFF6E6E66)
    val Hairline = Color(0xFFE0DCD2)
    val Olive = Color(0xFF6B7248)
    val OliveWash = Color(0xFFE8EADF)
    val Error = Color(0xFF8C3A2B)

    // Dark — the same palette after sundown, kept warm rather than blue-black.
    val SumiSurfaceDark = Color(0xFF14140F)
    val SumiSurfaceRaisedDark = Color(0xFF1F1F18)
    val RicePaperInkDark = Color(0xFFF0EDE4)
    val RicePaperInkSoftDark = Color(0xFFB8B4A8)

    /** Secondary text and outlines. 5.1:1 on [SumiSurfaceDark]. Was #7A776D, at 4.1:1. */
    val RicePaperInkFaintDark = Color(0xFF8A877E)
    val HairlineDark = Color(0xFF2E2E26)
    val OliveDark = Color(0xFF9AA36E)
    val OliveWashDark = Color(0xFF2A2E1F)
    val ErrorDark = Color(0xFFC97A66)
}

/**
 * Material 3 components read their colours from here, so a stock [androidx.compose.material3]
 * button is already a Trace button. Only tokens Material 3 has no slot for — the spacing
 * scale — live outside this scheme.
 */
internal val TraceLightColorScheme = lightColorScheme(
    primary = TraceColors.Olive,
    onPrimary = TraceColors.RicePaper,
    primaryContainer = TraceColors.OliveWash,
    onPrimaryContainer = TraceColors.SumiInk,
    secondary = TraceColors.SumiInkSoft,
    onSecondary = TraceColors.RicePaper,
    background = TraceColors.RicePaper,
    onBackground = TraceColors.SumiInk,
    surface = TraceColors.RicePaper,
    onSurface = TraceColors.SumiInk,
    surfaceVariant = TraceColors.RicePaperRaised,
    onSurfaceVariant = TraceColors.SumiInkFaint,
    outline = TraceColors.SumiInkFaint,
    outlineVariant = TraceColors.Hairline,
    error = TraceColors.Error,
    onError = TraceColors.RicePaper,
)

internal val TraceDarkColorScheme = darkColorScheme(
    primary = TraceColors.OliveDark,
    onPrimary = TraceColors.SumiSurfaceDark,
    primaryContainer = TraceColors.OliveWashDark,
    onPrimaryContainer = TraceColors.RicePaperInkDark,
    secondary = TraceColors.RicePaperInkSoftDark,
    onSecondary = TraceColors.SumiSurfaceDark,
    background = TraceColors.SumiSurfaceDark,
    onBackground = TraceColors.RicePaperInkDark,
    surface = TraceColors.SumiSurfaceDark,
    onSurface = TraceColors.RicePaperInkDark,
    surfaceVariant = TraceColors.SumiSurfaceRaisedDark,
    onSurfaceVariant = TraceColors.RicePaperInkFaintDark,
    outline = TraceColors.RicePaperInkFaintDark,
    outlineVariant = TraceColors.HairlineDark,
    error = TraceColors.ErrorDark,
    onError = TraceColors.SumiSurfaceDark,
)
