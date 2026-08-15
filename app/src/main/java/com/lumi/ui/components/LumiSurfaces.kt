package com.lumi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.lumi.ui.theme.LumiShape
import com.lumi.ui.theme.LumiSize

/**
 * The frosted-glass surface from `DESIGN_LANGUAGE.md` §4.
 *
 * §4's recipe is `surfaceVariant` at partial alpha behind a hairline border, and it was written out
 * by hand in eight places — the input field, the web-search row, each attachment tile, the New Chat
 * row, and both sidebars. Eight copies of a recipe is eight chances for one of them to drift by an
 * alpha point, which is exactly how an app stops looking like one app.
 *
 * §4 also asks for the content behind to blur. Compose has no backdrop-blur primitive — `blur()`
 * blurs a composable's own content, not what is behind it — so the frosting is alpha and the border
 * only. Revisit if a backdrop API lands; do not fake it with a screenshot.
 *
 * @param alpha how much of the surface below shows through. §4's default is 0.70.
 */
@Composable
fun LumiGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = LumiShape.default,
    alpha: Float = GLASS_ALPHA,
    bordered: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha), shape)
            .then(if (bordered) Modifier.hairlineBorder(shape) else Modifier),
    ) {
        content()
    }
}

/**
 * The hairline that edges every glass surface. §4: 0.5dp at 10% of the on-colour.
 *
 * A modifier rather than a parameter because two things need it that are not glass panels — both
 * sidebars, which are opaque, and the divider at the top of the attachment sheet.
 */
@Composable
fun Modifier.hairlineBorder(shape: Shape = LumiShape.default): Modifier =
    border(LumiSize.hairline, MaterialTheme.colorScheme.onSurface.copy(alpha = BORDER_ALPHA), shape)

/**
 * A circular icon button. `DESIGN_LANGUAGE.md` §7.
 *
 * §7 gives three of these — Attach, Audio, Send — differing only in fill and glyph, so they are one
 * component with a fill parameter rather than three hand-rolled `Box`es.
 *
 * **Known accessibility gap:** §7 specifies a 42dp diameter and that is what this draws, but
 * Android's minimum touch target is 48dp. Expanding the touch area without moving the visual needs
 * a layout change to the surrounding gaps, so it is deferred to the accessibility pass in
 * `todo.md` Phase 5 rather than being quietly fudged here.
 *
 * @param enabled false greys the glyph and swallows the click, for Send with an empty field.
 */
@Composable
fun LumiIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Color.Unspecified,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    bordered: Boolean = false,
    enabled: Boolean = true,
    iconSize: Dp = LumiSize.icon,
) {
    val fill = if (container == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTLE_FILL_ALPHA)
    } else {
        container
    }
    Box(
        modifier = modifier
            .size(LumiSize.iconButton)
            .background(fill, LumiShape.control)
            .then(if (bordered) Modifier.hairlineBorder(LumiShape.control) else Modifier)
            .clip(LumiShape.control)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** §4's frosted-glass alpha. */
const val GLASS_ALPHA = 0.70f

/** §4's hairline border opacity. */
const val BORDER_ALPHA = 0.10f

/** The barely-there fill on a secondary control, such as Attach. */
const val SUBTLE_FILL_ALPHA = 0.07f

