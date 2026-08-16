package com.lumi.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The slider every settings row draws.
 *
 * Material's default slider already resolves the track to the theme's primary, but leaving it
 * default means each screen restyles it separately and the colours drift the moment one screen
 * tweaks a thumb. One component, one set of tokens: accent thumb and active track, a faint
 * monochrome inactive track — the accent stays reserved for the part that moves.
 *
 * Keep the wrapper around [Slider] thin. The label, the live value, and the plain-words
 * explanation belong to the row that owns the setting — DESIGN_LANGUAGE §10 — not to the control.
 */
@Composable
fun LumiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    Slider(
        value = value.coerceIn(valueRange),
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_TRACK_ALPHA),
        ),
    )
}

private const val INACTIVE_TRACK_ALPHA = 0.12f
