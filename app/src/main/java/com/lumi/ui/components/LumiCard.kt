package com.lumi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.lumi.ui.theme.LumiShape
import com.lumi.ui.theme.LumiSize
import com.lumi.ui.theme.spacing

/**
 * The shared card surface, per `DESIGN_LANGUAGE.md` §2: cards live on [MaterialTheme]'s `surface`
 * at §4's default 16dp radius, with the same hairline edge the glass surfaces carry.
 *
 * This is the container only — screens compose their own content inside it, because a routine row,
 * a search result, and an audit entry share the surface but nothing else. [LumiCardRow] covers the
 * common title-and-detail case.
 *
 * @param onClick when non-null the whole card is tappable. The card does not draw its own ripple
 *   affordance: the tap target is the card, and the content decides how selection is shown.
 */
@Composable
fun LumiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = LumiShape.default
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .hairlineBorder(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        content()
    }
}

/**
 * The common card shape for list rows: leading glyph, title, optional detail, optional trailing
 * control. Reminders, todos, routines, and audit entries are all this with different words.
 *
 * One component rather than per-screen rows so the spacing and truncation cannot drift between
 * screens — a row that wraps its title differently on the routine screen than on the reminder
 * screen reads as two apps.
 *
 * @param icon decorative by default; pass [contentDescription] only when the glyph is the row's
 *   sole label for its action.
 * @param trailing a control that acts on just this row — a switch, a check, a dismiss. Kept a slot
 *   rather than a parameter because phases 3-5 each need a different one.
 */
@Composable
fun LumiCardRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(LumiShape.default)
            .background(MaterialTheme.colorScheme.surface, LumiShape.default)
            .hairlineBorder(LumiShape.default)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(MaterialTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(LumiSize.icon),
            )
            Spacer(Modifier.width(MaterialTheme.spacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(MaterialTheme.spacing.md))
            trailing()
        }
    }
}
