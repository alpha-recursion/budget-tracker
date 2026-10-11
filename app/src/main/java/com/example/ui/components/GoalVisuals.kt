package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Accent
import com.example.ui.theme.Hairline
import com.example.ui.theme.Positive
import com.example.ui.theme.Warning
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Circular progress ring with rounded ends; [content] sits in the middle. */
@Composable
fun GoalRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 7.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "goal_ring"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2
            val arcSize = Size(this.size.width - width, this.size.height - width)
            drawArc(Hairline, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(width))
            if (animated > 0f) {
                drawArc(
                    color, -90f, 360f * animated, false, Offset(inset, inset), arcSize,
                    style = Stroke(width, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

private data class Particle(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val sway: Float,
    val phase: Float,
    val spin: Float,
    val color: Color,
    val w: Float,
    val h: Float
)

/** A short burst of falling confetti over the whole screen; plays once. */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier, durationMs: Int = 2600) {
    val particles = remember {
        val colors = listOf(Accent, Positive, Warning, Color(0xFFE84393), Color(0xFF6C5CE7), Color(0xFF00CEC9))
        val random = Random(42)
        List(90) {
            Particle(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.35f,
                speed = 0.8f + random.nextFloat() * 0.7f,
                sway = 10f + random.nextFloat() * 30f,
                phase = random.nextFloat() * 2f * PI.toFloat(),
                spin = 180f + random.nextFloat() * 540f,
                color = colors[it % colors.size],
                w = 6f + random.nextFloat() * 6f,
                h = 10f + random.nextFloat() * 8f
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        particles.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val y = -40f + (size.height + 80f) * local * p.speed
            val x = p.x * size.width + sin(p.phase + local * 8f) * p.sway
            val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
            rotate(degrees = p.spin * local, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.w / 2, y - p.h / 2),
                    size = Size(p.w, p.h)
                )
            }
        }
    }
}
