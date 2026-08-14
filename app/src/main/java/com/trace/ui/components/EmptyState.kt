package com.trace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.trace.ui.theme.spacing

/**
 * The shared empty state.
 *
 * An empty state says what the user can do next. It is not an error and must not read like
 * one: "No routines yet. Tell Trace what you want automated." rather than "Nothing found".
 *
 * @param headline what is empty, in serif — the calm statement.
 * @param invitation the next action, in the softer body colour.
 */
@Composable
fun EmptyState(
    headline: String,
    invitation: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Opaque on purpose. Navigation cross-fades two destinations at once, and a
            // transparent screen lets the outgoing one show through — two centred headlines
            // overlapping mid-transition reads as a rendering bug.
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = MaterialTheme.spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = invitation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.md),
        )
    }
}
