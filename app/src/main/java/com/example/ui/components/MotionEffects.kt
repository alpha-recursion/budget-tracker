package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.PwMotion

/**
 * 0 → 1 over [durationMs] whenever [keys] change (bars growing, rings and donuts sweeping,
 * lines drawing themselves). Always 1 when the phone's "Remove animations" is on.
 */
@Composable
fun rememberGrowth(vararg keys: Any?, durationMs: Int = PwMotion.LONG, delayMs: Int = 0): Float {
    if (LocalReduceMotion.current) return 1f
    val progress = remember { Animatable(0f) }
    LaunchedEffect(*keys) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, delayMs, FastOutSlowInEasing))
    }
    return progress.value
}

/** A number that counts from its old value to the new one (KPI amounts). */
@Composable
fun animatedAmount(target: Double): Double {
    val value by animateFloatAsState(target.toFloat(), PwMotion.grow(550), label = "count_up")
    return if (LocalReduceMotion.current) target else value.toDouble()
}

/** 0 → 1 looping, for the pulsing "now" dot; stays 0 when animations are off. */
@Composable
fun rememberPulse(periodMs: Int = 1800): Float {
    if (LocalReduceMotion.current) return 0f
    val transition = rememberInfiniteTransition(label = "pulse")
    val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "pulse_value")
    return value
}

/** A soft light band sweeping across progress bars (the goal bars' shimmer). */
@Composable
fun Modifier.shimmer(): Modifier {
    if (LocalReduceMotion.current) return this
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing)), label = "shimmer_x")
    return drawWithContent {
        drawContent()
        val band = size.width * 0.4f
        val start = size.width * x - band / 2
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0f)),
                startX = start,
                endX = start + band
            ),
            topLeft = Offset(start, 0f),
            size = androidx.compose.ui.geometry.Size(band, size.height)
        )
    }
}
