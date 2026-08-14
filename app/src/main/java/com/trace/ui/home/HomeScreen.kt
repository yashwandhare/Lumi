package com.trace.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.trace.ui.theme.spacing

/**
 * The primary surface. Deliberately almost empty.
 *
 * No feature grid, no dashboard cards, no shortcut row, no statistics. The user should open
 * Trace and immediately state an intention. Every element added here has to earn its place
 * against that.
 *
 * Two pieces are still missing and belong to Dev B in Phase 1: the mascot, which is the
 * visual anchor between the wordmark and the question, and the natural-language input with
 * its Type and Speak affordances. The layout below already reserves their space.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(MaterialTheme.spacing.xxxl))

        Text(
            text = "TRACE",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The centre stays sparse. Mascot goes here.
        Spacer(Modifier.weight(1f))

        Text(
            text = "What can I do?",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        // Input row goes here.
        Spacer(Modifier.weight(1f))
    }
}
