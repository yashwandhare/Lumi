package com.trace.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One spacing scale for the whole app, per `DESIGN_LANGUAGE.md` §5.
 *
 * Material 3 has no slot for spacing, so it is provided separately and read as
 * `MaterialTheme.spacing`. Use these rather than literal dp: a screen that invents its own
 * padding is the first step to a screen that looks like it came from a different application.
 *
 * The six named steps are exactly §5's table. [none] and [touchTarget] are utilities rather than
 * steps on the scale — [touchTarget] is the accessibility floor for anything tappable, which is
 * why it lives here where a layout author will see it.
 */
@Suppress("unused")
data class TraceSpacing(
    val none: Dp = 0.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    /** Minimum interactive size. Never ship a tap target smaller than this. */
    val touchTarget: Dp = 48.dp,
)

val LocalTraceSpacing = staticCompositionLocalOf { TraceSpacing() }

/**
 * Shapes by *context*, per `DESIGN_LANGUAGE.md` §4.
 *
 * Material's five-slot `Shapes` cannot express this: §4 assigns radii to roles — input, tile,
 * panel — not to a small/medium/large ramp. The Material slots are all 16dp (see
 * `CohesiveShapes`) so a stock component is already correct; these are for the cases §4 calls out
 * separately.
 */
object TraceShape {

    /** §4 default. Matches every Material shape token, so it is rarely needed explicitly. */
    val default: RoundedCornerShape = RoundedCornerShape(16.dp)

    /** §4 input field. Deliberately the most rounded thing in the app — the primary control. */
    val input: RoundedCornerShape = RoundedCornerShape(28.dp)

    /** §4 attachment tiles. */
    val tile: RoundedCornerShape = RoundedCornerShape(24.dp)

    /**
     * §4 sidebars and modals: square, edge to edge.
     *
     * A rounded panel that reaches the screen edge shows background through its corners, which
     * reads as a mistake rather than as a radius. Full-bleed panels get no radius at all.
     */
    val panel: Shape = RectangleShape

    /** §4 icon buttons. */
    val control: Shape = CircleShape
}

/**
 * Fixed component dimensions, per `DESIGN_LANGUAGE.md` §4 and §7.
 *
 * These are sizes, not spacing, and they are kept apart deliberately: `spacing.md` between two
 * elements and a 16dp-tall element are unrelated facts that happen to share a number today.
 * Collapsing them means a spacing change silently resizes a control.
 */
object TraceSize {

    /**
     * §7 icon button diameter — the *visual* circle.
     *
     * Smaller than [TraceSpacing.touchTarget] on purpose. The drawn circle is 42dp; the tappable
     * area must still be at least 48dp, which Material's `minimumInteractiveComponentSize`
     * enforces for its own components and which a hand-rolled control must add itself.
     */
    val iconButton: Dp = 42.dp

    /** Glyph inside an [iconButton]. */
    val icon: Dp = 20.dp

    /** Circular avatar in the drawer header. */
    val avatar: Dp = 40.dp

    /** §8 sidebar width, both sides. */
    val drawer: Dp = 280.dp

    /** §6 mascot on the home screen. */
    val mascot: Dp = 72.dp

    /** §4 attachment tile height. */
    val tile: Dp = 96.dp

    /**
     * §4 glass border. Sub-pixel on purpose: it should read as an edge, not as a stroke.
     *
     * Not 1dp. At 1dp on a 2.6x-density screen this becomes a 3px line, which stops looking like
     * glass and starts looking like a table cell.
     */
    val hairline: Dp = 0.5.dp
}
