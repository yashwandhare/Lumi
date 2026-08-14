package com.trace.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One spacing scale for the whole app.
 *
 * Material 3 has no slot for spacing, so it is provided separately and read as
 * `MaterialTheme.spacing`. Use these values rather than literal dp: a screen that
 * invents its own padding is the first step to a screen that looks like it came from a
 * different application.
 *
 * [screen] is the standard horizontal inset for screen content. [gutter] is the vertical
 * rhythm between unrelated blocks. Both are deliberately generous — negative space is a
 * design requirement here, not leftover room.
 */
@Suppress("unused")
data class TraceSpacing(
    val none: Dp = 0.dp,
    val hairline: Dp = 1.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    val screen: Dp = 24.dp,
    val gutter: Dp = 32.dp,
    /** Minimum interactive size. Never ship a tap target smaller than this. */
    val touchTarget: Dp = 48.dp,
)

val LocalTraceSpacing = staticCompositionLocalOf { TraceSpacing() }

/**
 * Restrained radii. Simple geometric forms, not pill shapes.
 *
 * [Shapes.extraLarge] is the only generous one and belongs to the natural-language
 * input field, which is the single most important control in the app.
 */
internal val TraceShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
