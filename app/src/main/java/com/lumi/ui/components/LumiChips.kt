package com.lumi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lumi.ui.theme.spacing

/**
 * The chip shape every screen draws its tags with.
 *
 * §3 puts labels in sans-serif, and chips are labels, so the text is `labelMedium`. The pill radius
 * is deliberately larger than any §4 surface radius — a chip is metadata, not a container, and it
 * should not read as a miniature card.
 *
 * **Selection is never colour alone.** A selected chip gets the accent fill *and* heavier text
 * weight, so a user who cannot distinguish Slime Blue from the surface still sees the difference —
 * `for_devb.md`'s "never convey information by colour alone" rule.
 *
 * @param selected fills the chip with the accent. Reserved for true toggle semantics — a filter chip
 *   the user can switch on and off. Do not use it to decorate.
 * @param icon optional leading glyph, decorative unless [contentDescription] says otherwise.
 * @param onRemove when non-null the chip shows a trailing remove glyph. Used by the attachment chip
 *   in Phase 4 — the chip *is* the confirmation surface there, so removing it is the undo.
 */
@Composable
fun LumiChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    val fill = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .hairlineBorder(shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics { role = Role.Button }
                } else {
                    Modifier
                },
            )
            .defaultMinSize(minHeight = CHIP_MIN_HEIGHT)
            .padding(horizontal = MaterialTheme.spacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = foreground,
                modifier = Modifier.size(CHIP_ICON_SIZE),
            )
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            maxLines = 1,
        )
        if (onRemove != null) {
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove $text",
                tint = foreground.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(shape)
                    .clickable(onClick = onRemove)
                    .size(CHIP_ICON_SIZE),
            )
        }
    }
}

private val CHIP_MIN_HEIGHT = 28.dp
private val CHIP_ICON_SIZE = 16.dp
