package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.SurfaceMuted

/** Semicircle progress gauge (reference style) with a value and caption in the middle. */
@Composable
fun BudgetGauge(
    fraction: Float,
    color: Color,
    value: String,
    caption: String,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "gauge")
    Box(modifier = modifier.size(width = 150.dp, height = 96.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(modifier = Modifier.size(width = 150.dp, height = 96.dp)) {
            val stroke = 16.dp.toPx()
            val diameter = size.width - stroke
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(SurfaceMuted, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(color, 180f, 180f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = Ink, maxLines = 1)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = InkSecondary)
        }
    }
}
