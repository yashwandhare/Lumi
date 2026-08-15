package com.trace.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.EaseInOutSine
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.trace.ui.theme.LocalMotionEnabled
import com.trace.ui.theme.MascotAngry
import com.trace.ui.theme.MascotAngryCore
import com.trace.ui.theme.MascotAngryEdge
import com.trace.ui.theme.MascotCore
import com.trace.ui.theme.MascotEdge
import com.trace.ui.theme.MascotEye
import com.trace.ui.theme.SlimeBlue
import com.trace.ui.theme.TraceMotion
import kotlin.random.Random
import kotlin.random.nextInt

@Composable
fun TraceBlob(
    modifier: Modifier = Modifier,
    isTyping: Boolean = false,
    reactionTrigger: Int = 0,
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
            kotlinx.coroutines.delay(rng.nextInt(TraceMotion.SHAPE_SHIFT_MS).toLong())
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
            animation = tween(TraceMotion.BREATH_MS, easing = EaseInOutSine),
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
        animationSpec = tween(durationMillis = TraceMotion.BREATH_MS, easing = EaseInOutSine),
        label = "BlobScale"
    )

    val offsetY = if (animate) infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(TraceMotion.DRIFT_MS, easing = EaseInOutSine),
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
    val p1 = if (animate) infiniteTransition.animateFloat(targetT1, targetT2, infiniteRepeatable(tween(TraceMotion.DRIFT_MS, easing = EaseInOutSine), RepeatMode.Reverse), label = "p1").value else targetT1
    val p2 = if (animate) infiniteTransition.animateFloat(targetT2, targetT1, infiniteRepeatable(tween(3200, easing = EaseInOutSine), RepeatMode.Reverse), label = "p2").value else targetT2
    val p3 = if (animate) infiniteTransition.animateFloat(targetB1, targetB2, infiniteRepeatable(tween(2800, easing = EaseInOutSine), RepeatMode.Reverse), label = "p3").value else targetB1
    val p4 = if (animate) infiniteTransition.animateFloat(targetB2, targetB1, infiniteRepeatable(tween(3500, easing = EaseInOutSine), RepeatMode.Reverse), label = "p4").value else targetB2


    var clickCount by remember { mutableStateOf(0) }
    val isAngry = clickCount >= TraceMotion.ANGER_TAPS

    androidx.compose.runtime.LaunchedEffect(clickCount) {
        if (clickCount > 0) {
            kotlinx.coroutines.delay(TraceMotion.ANGER_WINDOW_MS)
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
            kotlinx.coroutines.delay(TraceMotion.WINK_MS.toLong())
            isWinking = false
        }
    }

    // When typing, look down towards the input field
    val targetEyeOffsetY = if (isTyping) 4f else eyeOffsetY

    val targetEyeHeight = when {
        isAngry -> 2.dp
        isWinking -> 2.dp
        isRelaxed -> 6.dp // Squint slightly when relaxed
        else -> 8.dp
    }

    val eyeHeight by animateDpAsState(
        targetValue = targetEyeHeight,
        animationSpec = tween(TraceMotion.QUICK_MS, easing = EaseInOutSine),
        label = "EyeSquint"
    )

    val animatedEyeOffsetX by animateFloatAsState(
        targetValue = eyeOffsetX,
        animationSpec = tween(TraceMotion.CALM_MS, easing = EaseInOutSine),
        label = "EyeOffsetX"
    )

    val animatedEyeOffsetY by animateFloatAsState(
        targetValue = targetEyeOffsetY,
        animationSpec = tween(TraceMotion.CALM_MS, easing = EaseInOutSine),
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
            kotlinx.coroutines.delay(TraceMotion.QUICK_MS.toLong())
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

    Box(
        modifier = modifier
            .offset(x = finalOffsetX.dp, y = offsetY.dp)
            .scale(scale)
            .semantics { contentDescription = MASCOT_DESCRIPTION }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClickLabel = "Poke Trace"
            ) {
                clickCount++
                isWinking = true
                scope.launch {
                    kotlinx.coroutines.delay(TraceMotion.WINK_MS.toLong())
                    isWinking = false
                }
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
                    .size(width = 5.dp, height = eyeHeight * 0.75f)
                    .clip(CircleShape)
                    .background(MascotEye)
            )
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = eyeHeight * 0.75f)
                    .clip(CircleShape)
                    .background(MascotEye)
            )
        }
    }
}

@Composable
fun TraceLogoIcon(modifier: Modifier = Modifier) {
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
 * How long the body takes to change mood colour. Longer than `TraceMotion.CALM_MS` on purpose — a
 * mood is meant to bleed in slowly, not switch. This is the mascot's own timing, so it stays here
 * rather than becoming an app-wide token.
 */
private const val MOOD_SHIFT_MS = 500

/**
 * What a screen reader announces. The mascot is Trace's presence on the screen rather than a control,
 * so it names the app and stops — its mood is decoration, and reading it aloud would be noise.
 */
private const val MASCOT_DESCRIPTION = "Trace"
