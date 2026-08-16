package com.lumi.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.lumi.ui.theme.LocalMotionEnabled
import com.lumi.ui.theme.MascotAngry
import com.lumi.ui.theme.MascotAngryCore
import com.lumi.ui.theme.MascotAngryEdge
import com.lumi.ui.theme.MascotCore
import com.lumi.ui.theme.MascotEdge
import com.lumi.ui.theme.MascotEye
import com.lumi.ui.theme.SlimeBlue
import com.lumi.ui.theme.LumiMotion
import kotlin.random.Random
import kotlin.random.nextInt

@Composable
fun LumiBlob(
    modifier: Modifier = Modifier,
    isTyping: Boolean = false,
    reactionTrigger: Int = 0,
    /**
     * The open eye, width by height.
     *
     * A parameter rather than a constant because the mascot is drawn at two very different sizes. The
     * home screen's is 69dp and is the thing the user looks *at*, so it gets larger, oval eyes with some
     * character. The docked one in the top bar is 32dp, where that same oval closes to a smudge — so the
     * default reproduces the original near-circular pair and every other call site is unchanged.
     */
    eyeSize: DpSize = DpSize(width = 5.dp, height = 6.dp),
) {
    // Every idle animation below is gated on this. False means reduced motion, battery saver, or the
    // app is off screen — see LocalMotionEnabled. When it is false the mascot holds its resting pose
    // rather than freezing mid-squash, and none of the loops are started at all.
    val animate = LocalMotionEnabled.current

    // One seeded generator instead of `random()` at each call site. todo.md requires the mascot's
    // motion to be deterministic, and a fixed seed means the same sequence every run — reproducible,
    // and assertable in a test.
    val rng = remember { Random(seed = 20260815) }

    var isRelaxed by remember { mutableStateOf(false) }

    // Randomly toggle between blob slime and relaxed slime
    androidx.compose.runtime.LaunchedEffect(animate) {
        if (!animate) {
            isRelaxed = false
            return@LaunchedEffect
        }
        while (true) {
            kotlinx.coroutines.delay(rng.nextInt(LumiMotion.SHAPE_SHIFT_MS).toLong())
            isRelaxed = !isRelaxed
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "blob_breathe")

    // Read through `.value` inside an `if` rather than by delegation, so that when motion is off the
    // animation is never attached to the transition and no frame callback is scheduled. Gating the
    // value alone would leave the loop running invisibly.
    val isBreathing = if (animate) infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(LumiMotion.BREATH_MS, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "is_breathing"
    ).value else 1f

    val scale by animateFloatAsState(
        targetValue = when {
            isTyping -> 0.85f
            !animate -> 1f
            isBreathing > 0.5f -> 1.05f
            else -> 0.98f
        },
        animationSpec = tween(durationMillis = LumiMotion.BREATH_MS, easing = EaseInOutSine),
        label = "BlobScale"
    )

    val offsetY = if (animate) infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(LumiMotion.DRIFT_MS, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob_float"
    ).value else 0f

    // If relaxed, the slime spreads out just slightly, but stays very round
    val targetT1 = if (isRelaxed) 40f else 48f
    val targetT2 = if (isRelaxed) 50f else 50f
    val targetB1 = if (isRelaxed) 35f else 45f
    val targetB2 = if (isRelaxed) 40f else 48f

    // Resting silhouette when motion is off: the mid-point of each pair, so the mascot sits at a
    // plausible shape rather than at one extreme of its morph.
    val p1 = if (animate) infiniteTransition.animateFloat(targetT1, targetT2, infiniteRepeatable(tween(LumiMotion.DRIFT_MS, easing = EaseInOutSine), RepeatMode.Reverse), label = "p1").value else targetT1
    val p2 = if (animate) infiniteTransition.animateFloat(targetT2, targetT1, infiniteRepeatable(tween(3200, easing = EaseInOutSine), RepeatMode.Reverse), label = "p2").value else targetT2
    val p3 = if (animate) infiniteTransition.animateFloat(targetB1, targetB2, infiniteRepeatable(tween(2800, easing = EaseInOutSine), RepeatMode.Reverse), label = "p3").value else targetB1
    val p4 = if (animate) infiniteTransition.animateFloat(targetB2, targetB1, infiniteRepeatable(tween(3500, easing = EaseInOutSine), RepeatMode.Reverse), label = "p4").value else targetB2


    var clickCount by remember { mutableStateOf(0) }
    var doubleTapCount by remember { mutableStateOf(0) }
    val isAngry = clickCount >= LumiMotion.ANGER_TAPS

    androidx.compose.runtime.LaunchedEffect(clickCount) {
        if (clickCount > 0) {
            kotlinx.coroutines.delay(LumiMotion.ANGER_WINDOW_MS)
            clickCount = 0
        }
    }

    var isWinking by remember { mutableStateOf(false) }
    var eyeOffsetX by remember { mutableStateOf(0f) }
    var eyeOffsetY by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    
    // Not gated on `animate`: this is direct feedback for a tap the user just made, and §6 calls it
    // "instantly". Reduced motion means no idle decoration, not no response to input.
    androidx.compose.runtime.LaunchedEffect(reactionTrigger) {
        if (reactionTrigger > 0) {
            isWinking = true
            kotlinx.coroutines.delay(LumiMotion.WINK_MS.toLong())
            isWinking = false
        }
    }

    // When typing, look down towards the input field
    val targetEyeOffsetY = if (isTyping) 4f else eyeOffsetY

    val targetEyeHeight = when {
        // A scowl and a blink both close the eye to a line, and a squint is most of the way open. All
        // three are fractions of the open height rather than fixed dp values: with the eye scaled up on
        // the home screen, a hardcoded 2dp blink would barely move the larger eye and would stop reading
        // as a blink at all.
        isAngry || isWinking -> eyeSize.height * CLOSED_EYE_FRACTION
        isRelaxed -> eyeSize.height * SQUINT_FRACTION
        else -> eyeSize.height
    }

    val eyeHeight by animateDpAsState(
        targetValue = targetEyeHeight,
        animationSpec = tween(LumiMotion.QUICK_MS, easing = EaseInOutSine),
        label = "EyeSquint"
    )

    val animatedEyeOffsetX by animateFloatAsState(
        targetValue = eyeOffsetX,
        animationSpec = tween(LumiMotion.CALM_MS, easing = EaseInOutSine),
        label = "EyeOffsetX"
    )

    val animatedEyeOffsetY by animateFloatAsState(
        targetValue = targetEyeOffsetY,
        animationSpec = tween(LumiMotion.CALM_MS, easing = EaseInOutSine),
        label = "EyeOffsetY"
    )

    val blobColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isAngry) MascotAngry else SlimeBlue,
        animationSpec = tween(MOOD_SHIFT_MS),
        label = "BlobColor"
    )

    androidx.compose.runtime.LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            val delayToBlink = rng.nextInt(1000..5000).toLong()
            kotlinx.coroutines.delay(delayToBlink)
            isWinking = true
            kotlinx.coroutines.delay(LumiMotion.QUICK_MS.toLong())
            isWinking = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(animate) {
        if (!animate) {
            eyeOffsetX = 0f
            eyeOffsetY = 0f
            return@LaunchedEffect
        }
        while (true) {
            val delayToLook = rng.nextInt(2000..6000).toLong()
            kotlinx.coroutines.delay(delayToLook)
            if (rng.nextInt(3) == 0) {
                eyeOffsetX = 0f
                eyeOffsetY = 0f
            } else {
                eyeOffsetX = rng.nextInt(-4..4).toFloat()
                eyeOffsetY = rng.nextInt(-2..2).toFloat()
            }
        }
    }

    // When angry, shake by stepping a fixed pattern from a coroutine. The previous version read
    // `random()` inside `animateFloatAsState`, which only re-rolled when something else happened to
    // recompose — so the shake was mostly still, and it was non-deterministic, which todo.md rules
    // out. Anger is provoked by tapping, so this runs even when idle motion is off.
    var shakeStep by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(isAngry) {
        if (!isAngry) {
            shakeStep = 0
            return@LaunchedEffect
        }
        while (true) {
            kotlinx.coroutines.delay(SHAKE_STEP_MS)
            shakeStep++
        }
    }
    val shakeOffset by animateFloatAsState(
        targetValue = if (isAngry) SHAKE_PATTERN[shakeStep % SHAKE_PATTERN.size] else 0f,
        animationSpec = tween(SHAKE_STEP_MS.toInt(), easing = EaseInOutSine),
        label = "Shake"
    )
    val finalOffsetX = if (isAngry) shakeOffset else 0f

    /**
     * The double-tap bounce: up quickly, then settle back with a little overshoot.
     *
     * A separate multiplier rather than a change to [scale], so it composes with breathing and the
     * typing shrink instead of fighting them — a bounce that replaced the breathing scale would stutter
     * whenever the two disagreed.
     *
     * Not gated on [LocalMotionEnabled]: this is the direct answer to a gesture the user just made, and
     * reduced motion means no idle decoration, not an app that ignores input. Same reasoning as the wink.
     */
    val bounce = remember { Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(doubleTapCount) {
        if (doubleTapCount > 0) {
            bounce.animateTo(BOUNCE_PEAK, tween(BOUNCE_UP_MS, easing = EaseInOutSine))
            bounce.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
        }
    }

    fun poke() {
        clickCount++
        isWinking = true
        scope.launch {
            kotlinx.coroutines.delay(LumiMotion.WINK_MS.toLong())
            isWinking = false
        }
    }

    Box(
        modifier = modifier
            .offset(x = finalOffsetX.dp, y = offsetY.dp)
            .scale(scale * bounce.value)
            .semantics {
                contentDescription = MASCOT_DESCRIPTION
                // The tap gesture below replaces `clickable`, which would swallow the double tap. This
                // keeps the mascot reachable by a screen reader, which `pointerInput` alone would not.
                onClick(label = "Poke Lumi") { poke(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { poke() },
                    onDoubleTap = {
                        doubleTapCount++
                        poke()
                    },
                )
            }
            .size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        val blobShape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStartPercent = p1.toInt(),
            topEndPercent = p2.toInt(),
            bottomEndPercent = p3.toInt(),
            bottomStartPercent = p4.toInt()
        )

        // Soft glowing aura (still perfectly circular/soft behind the morphing body)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(1.2f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            blobColor.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        val darkInnerColor by androidx.compose.animation.animateColorAsState(
            targetValue = if (isAngry) MascotAngryCore else MascotCore,
            animationSpec = tween(MOOD_SHIFT_MS),
            label = "DarkInnerColor"
        )
        val lightOuterColor by androidx.compose.animation.animateColorAsState(
            targetValue = if (isAngry) MascotAngryEdge else MascotEdge,
            animationSpec = tween(MOOD_SHIFT_MS),
            label = "LightOuterColor"
        )

        // Core blob body (morphed as slime)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(0.85f) // Slightly smaller so it fits within the aura nicely
                .clip(blobShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            darkInnerColor,
                            blobColor,
                            lightOuterColor
                        ),
                        radius = 120f // stretch the gradient out to the edges
                    )
                )
        )
        
        // Eyes (Interactive)
        Row(
            modifier = Modifier.offset(x = animatedEyeOffsetX.dp, y = animatedEyeOffsetY.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = eyeSize.width, height = eyeHeight)
                    .clip(CircleShape)
                    .background(MascotEye)
            )
            Box(
                modifier = Modifier
                    .size(width = eyeSize.width, height = eyeHeight)
                    .clip(CircleShape)
                    .background(MascotEye)
            )
        }
    }
}

@Composable
fun LumiLogoIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        val blobShape = androidx.compose.foundation.shape.RoundedCornerShape(45)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(blobShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MascotCore, // Dark Inner
                            SlimeBlue, // Middle
                            MascotEdge  // Light Outer
                        ),
                        radius = 100f
                    )
                )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(MascotEye))
            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(MascotEye))
        }
    }
}

/**
 * Lumi asleep. The loading screen's easter egg.
 *
 * A separate composable rather than a flag on [LumiBlob], because almost nothing carries over: the body
 * is monochrome instead of cyan, the eyes are closed dashes instead of open circles, there is no
 * interaction, no anger, no glance, and it gains drifting Z's that nothing else has. Threading six
 * mutually-exclusive branches through the interactive mascot would have made both harder to read.
 *
 * **Monochrome on purpose.** The accent means "you can act on this", and there is nothing to act on
 * while the model loads. Draining the colour also makes the sleep read as sleep rather than as a
 * differently-coloured awake mascot.
 *
 * Breathing here is slower than [LumiMotion.BREATH_MS] — sleeping breath, not waiting breath. Gated on
 * [LocalMotionEnabled] like every other loop, and with motion off it holds a still sleeping pose, Z's
 * included, because the pose is the joke and the movement is only garnish.
 */
@Composable
fun LumiSleepingBlob(modifier: Modifier = Modifier) {
    val animate = LocalMotionEnabled.current
    val transition = rememberInfiniteTransition(label = "sleep")

    val breath = if (animate) transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(SLEEP_BREATH_MS, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sleep_breath"
    ).value else 1f

    // The awake mascot's *relaxed* silhouette, which is what asleep should look like: still round on
    // top, settled and slightly spread at the base. An earlier version used 50/50/42/42, which is a
    // wide rounded rectangle rather than a slime — the odd boxy outline came from those square-ish
    // bottom corners, not from the gradient.
    val settle = if (animate) transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SLEEP_BREATH_MS, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sleep_settle"
    ).value else 0.5f

    val bodyGrey = MaterialTheme.colorScheme.onSurface
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(breath)
                // The apex softens and the feet spread a little as it breathes, so the settle reads as
                // weight shifting rather than as uniform scaling.
                .clip(
                    // The awake mascot's own relaxed silhouette — same RoundedCornerShape, same
                    // percentages — with the bottom pair pulled in a little so the base sits wider.
                    // Four earlier attempts drew a custom dome path for this; it was never needed.
                    androidx.compose.foundation.shape.RoundedCornerShape(
                        topStartPercent = RELAXED_TOP_START - (settle * 2).toInt(),
                        topEndPercent = RELAXED_TOP_END,
                        bottomEndPercent = SLEEPING_BOTTOM_END + (settle * 2).toInt(),
                        bottomStartPercent = SLEEPING_BOTTOM_START + (settle * 2).toInt(),
                    )
                )
                .background(
                    // Bright centre to mid edge. An earlier version ran 0.34 down to 0.10 alpha, which
                    // renders as #5F5E5B fading into #2B2B2A against the #151515 background —
                    // technically a mascot, visually almost nothing. Monochrome means drained of hue,
                    // not drained of contrast.
                    Brush.radialGradient(
                        colors = listOf(
                            bodyGrey.copy(alpha = 0.60f),
                            bodyGrey.copy(alpha = 0.46f),
                            bodyGrey.copy(alpha = 0.32f),
                        ),
                    )
                )
        )

        // Closed eyes: two short bars, dark against the pale body — the inverse of the awake mascot's
        // light-on-cyan eyes. This is what reads as asleep rather than as blinking. Sat low, because on
        // a dome the widest part of the face is below centre.
        Row(
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.offset(y = 6.dp),
        ) {
            ClosedEye()
            ClosedEye()
        }

        // Z's drift up and to the right, each offset in time so they trail rather than pulse together.
        SleepingZ(animate = animate, delayMs = 0, sizeSp = 15, startX = 20.dp, startY = (-10).dp)
        SleepingZ(animate = animate, delayMs = 800, sizeSp = 12, startX = 30.dp, startY = (-20).dp)
        SleepingZ(animate = animate, delayMs = 1600, sizeSp = 9, startX = 38.dp, startY = (-27).dp)
    }
}

@Composable
private fun ClosedEye() {
    Box(
        modifier = Modifier
            .size(width = 8.dp, height = 2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.62f))
    )
}

/**
 * One Z, rising and fading.
 *
 * [delayMs] staggers the three so they form a trail. With motion off each simply sits at its start
 * position at partial opacity, which still reads as sleep.
 */
@Composable
private fun SleepingZ(
    animate: Boolean,
    delayMs: Int,
    sizeSp: Int,
    startX: androidx.compose.ui.unit.Dp,
    startY: androidx.compose.ui.unit.Dp,
) {
    val transition = rememberInfiniteTransition(label = "z$delayMs")
    val progress = if (animate) transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Z_CYCLE_MS, delayMillis = delayMs, easing = EaseInOutSine),
            repeatMode = RepeatMode.Restart,
        ),
        label = "z_rise",
    ).value else 0.35f

    Text(
        text = "z",
        style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = sizeSp.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface.copy(
            // Fade in over the first third, out over the last third, so a Z never pops or vanishes.
            alpha = 0.85f * when {
                progress < 0.3f -> progress / 0.3f
                progress > 0.7f -> (1f - progress) / 0.3f
                else -> 1f
            }
        ),
        modifier = Modifier.offset(x = startX, y = startY - (progress * Z_RISE_DP).dp),
    )
}

/** The double-tap bounce: how big, and how fast the growth is. The settle is a spring. */
private const val BOUNCE_PEAK = 1.18f
private const val BOUNCE_UP_MS = 110

/**
 * How far a blink and a squint close the eye, as a share of its open height.
 *
 * Fractions rather than dp so the poses scale with the eye — see the note at their use site.
 */
private const val CLOSED_EYE_FRACTION = 0.25f
private const val SQUINT_FRACTION = 0.75f

/** Slower than a waking breath. Sleep should look unhurried. */
private const val SLEEP_BREATH_MS = 3400

/** One Z's full rise-and-fade. Long enough that three of them overlap into a trail. */
private const val Z_CYCLE_MS = 2400
private const val Z_RISE_DP = 18f

/**
 * The awake mascot's relaxed corners, reused verbatim for the top of the sleeping pose.
 *
 * The bottom pair is rounded less than the awake mascot's 35/40, which is what widens the base — a
 * smaller corner radius leaves more straight edge, so the slime sits flatter and spreads.
 */
private const val RELAXED_TOP_START = 44
private const val RELAXED_TOP_END = 50
private const val SLEEPING_BOTTOM_END = 22
private const val SLEEPING_BOTTOM_START = 26

/**
 * §6 shake. A fixed pattern, stepped at [SHAKE_STEP_MS] — see the note at its use site for why this
 * is not a random value read during composition.
 *
 * Deliberately small. An earlier version ran ±2dp at 50ms per step, which read as violent rather than
 * annoyed — the mascot is the only playful thing in the app, and a hard jitter makes it look broken
 * instead of irritated. The amplitude tapers across the pattern so the shake settles rather than
 * stopping dead on whichever step anger happens to end on.
 */
private val SHAKE_PATTERN = listOf(-1f, 1f, -0.75f, 0.75f, -0.5f, 0.5f, -0.25f, 0f)
private const val SHAKE_STEP_MS = 70L

/**
 * How long the body takes to change mood colour. Longer than `LumiMotion.CALM_MS` on purpose — a
 * mood is meant to bleed in slowly, not switch. This is the mascot's own timing, so it stays here
 * rather than becoming an app-wide token.
 */
private const val MOOD_SHIFT_MS = 500

/**
 * What a screen reader announces. The mascot is Lumi's presence on the screen rather than a control,
 * so it names the app and stops — its mood is decoration, and reading it aloud would be noise.
 */
private const val MASCOT_DESCRIPTION = "Lumi"
