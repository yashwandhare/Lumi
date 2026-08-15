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

@Composable
fun TraceBlob(
    modifier: Modifier = Modifier,
    isTyping: Boolean = false,
    reactionTrigger: Int = 0,
) {
    var isRelaxed by remember { mutableStateOf(false) }

    // Randomly toggle between blob slime and relaxed slime
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay((5000..12000).random().toLong())
            isRelaxed = !isRelaxed
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "blob_breathe")
    
    val isBreathing by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "is_breathing"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isTyping) 0.85f else if (isBreathing > 0.5f) 1.05f else 0.98f,
        animationSpec = tween(durationMillis = 2000, easing = EaseInOutSine),
        label = "BlobScale"
    )
    
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob_float"
    )

    // If relaxed, the slime spreads out just slightly, but stays very round
    val targetT1 = if (isRelaxed) 40f else 48f
    val targetT2 = if (isRelaxed) 50f else 50f
    val targetB1 = if (isRelaxed) 35f else 45f
    val targetB2 = if (isRelaxed) 40f else 48f

    val p1 by infiniteTransition.animateFloat(targetT1, targetT2, infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse), label = "p1")
    val p2 by infiniteTransition.animateFloat(targetT2, targetT1, infiniteRepeatable(tween(3200, easing = EaseInOutSine), RepeatMode.Reverse), label = "p2")
    val p3 by infiniteTransition.animateFloat(targetB1, targetB2, infiniteRepeatable(tween(2800, easing = EaseInOutSine), RepeatMode.Reverse), label = "p3")
    val p4 by infiniteTransition.animateFloat(targetB2, targetB1, infiniteRepeatable(tween(3500, easing = EaseInOutSine), RepeatMode.Reverse), label = "p4")


    var clickCount by remember { mutableStateOf(0) }
    val isAngry = clickCount >= 5

    androidx.compose.runtime.LaunchedEffect(clickCount) {
        if (clickCount > 0) {
            kotlinx.coroutines.delay(2000)
            clickCount = 0
        }
    }

    var isWinking by remember { mutableStateOf(false) }
    var eyeOffsetX by remember { mutableStateOf(0f) }
    var eyeOffsetY by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    
    androidx.compose.runtime.LaunchedEffect(reactionTrigger) {
        if (reactionTrigger > 0) {
            isWinking = true
            kotlinx.coroutines.delay(300)
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
        animationSpec = tween(150, easing = EaseInOutSine),
        label = "EyeSquint"
    )

    val animatedEyeOffsetX by animateFloatAsState(
        targetValue = eyeOffsetX,
        animationSpec = tween(400, easing = EaseInOutSine),
        label = "EyeOffsetX"
    )
    
    val animatedEyeOffsetY by animateFloatAsState(
        targetValue = targetEyeOffsetY,
        animationSpec = tween(400, easing = EaseInOutSine),
        label = "EyeOffsetY"
    )

    val blobColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isAngry) Color(0xFFCF6679) else com.trace.ui.theme.SlimeBlue,
        animationSpec = tween(500),
        label = "BlobColor"
    )

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            val delayToBlink = (1000..5000).random().toLong()
            kotlinx.coroutines.delay(delayToBlink)
            isWinking = true
            kotlinx.coroutines.delay(150)
            isWinking = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            val delayToLook = (2000..6000).random().toLong()
            kotlinx.coroutines.delay(delayToLook)
            if ((0..2).random() == 0) {
                eyeOffsetX = 0f
                eyeOffsetY = 0f
            } else {
                eyeOffsetX = (-4..4).random().toFloat()
                eyeOffsetY = (-2..2).random().toFloat()
            }
        }
    }

    // When angry, shake effect using the offset
    val shakeOffset by animateFloatAsState(
        targetValue = if (isAngry) (-2..2).random().toFloat() else 0f,
        animationSpec = tween(50, easing = EaseInOutSine),
        label = "Shake"
    )
    val finalOffsetX = if (isAngry) shakeOffset else 0f

    Box(
        modifier = modifier
            .offset(x = finalOffsetX.dp, y = offsetY.dp)
            .scale(scale)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                clickCount++
                isWinking = true
                scope.launch {
                    kotlinx.coroutines.delay(300)
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
            targetValue = if (isAngry) Color(0xFF7A1C1C) else Color(0xFF00838F), // Darker cyan/teal core
            animationSpec = tween(500),
            label = "DarkInnerColor"
        )
        val lightOuterColor by androidx.compose.animation.animateColorAsState(
            targetValue = if (isAngry) Color(0xFFE57373) else Color(0xFFE0F7FA), // Light mint/cyan edge
            animationSpec = tween(500),
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
                    .background(Color.White)
            )
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = eyeHeight * 0.75f)
                    .clip(CircleShape)
                    .background(Color.White)
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
                            Color(0xFF00838F), // Dark Inner
                            com.trace.ui.theme.SlimeBlue, // Middle
                            Color(0xFFE0F7FA)  // Light Outer
                        ),
                        radius = 100f
                    )
                )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White))
            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White))
        }
    }
}
