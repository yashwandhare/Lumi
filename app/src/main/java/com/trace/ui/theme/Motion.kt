package com.trace.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * One place that answers "how long, and on what curve".
 *
 * Read these instead of writing a literal into a `tween`. A screen that invents its own duration
 * is the first step towards a screen that feels like it came from a different app.
 *
 * The three mascot values are fixed by `DESIGN_LANGUAGE.md` §6 and are not free to tune:
 * breathing is a 2s loop, the vertical float is 2.5s, and the shape shift lands somewhere in
 * 5-12s. Changing them changes the mascot's character, which is a design decision, not a
 * performance one.
 */
object TraceMotion {

    /** State flips the user already knows about: a press, an eye squint, a toggle. */
    const val QUICK_MS = 150

    /** The default. Enter, exit, expansion, colour changes. */
    const val STANDARD_MS = 250

    /** Deliberate, larger moves that should not feel rushed. */
    const val CALM_MS = 400

    /** §6 breathing. Slow enough to read as alive rather than as a loading spinner. */
    const val BREATH_MS = 2000

    /**
     * §6 vertical float. Deliberately not a multiple of [BREATH_MS] — two loops on coprime
     * periods drift in and out of phase, which is what stops the mascot looking mechanical.
     * Keep them from lining up.
     */
    const val DRIFT_MS = 2500

    /** §6 wink. The eyes squint for exactly this long on a button tap. */
    const val WINK_MS = 300

    /** §6 shape shift. A new resting silhouette is chosen at a random point in this range. */
    val SHAPE_SHIFT_MS = 5_000..12_000

    /** §6 anger. Five taps inside this window turn the mascot red. */
    const val ANGER_WINDOW_MS = 2000L

    /** Taps required inside [ANGER_WINDOW_MS] to anger the mascot. */
    const val ANGER_TAPS = 5

    /** Enter and exit. Decelerates into place; nothing overshoots. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Continuous loops. Sine has no hard stop at either end, so a reversing loop never ticks. */
    val Breathing: Easing = EaseInOutSine
}

/**
 * False when Trace must not animate.
 *
 * Three separate conditions collapse into this one flag, because a caller that has to check
 * three things will eventually check two:
 *
 * 1. **Reduced motion.** The user turned animations off system-wide.
 * 2. **Battery saver.** An infinite loop is exactly the sort of work a user in power-save mode is
 *    trying to avoid.
 * 3. **Lifecycle.** Below `STARTED` the app is not on screen, so an infinite animation is burning
 *    frames nobody sees.
 *
 * Provided at theme level rather than checked per screen so no animation can forget it.
 *
 * **When this is false, render the animation's resting state — never a frozen mid-frame.** A
 * mascot stopped mid-squash looks broken; a mascot at rest looks deliberate.
 *
 * This is not a replacement for [LocalReducedMotion]. That one answers "the user asked for
 * stillness", which also governs non-animation choices. This one answers "run this loop now".
 */
val LocalMotionEnabled = staticCompositionLocalOf { true }
