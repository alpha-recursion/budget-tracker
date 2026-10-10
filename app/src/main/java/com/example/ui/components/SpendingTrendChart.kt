package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Accent
import com.example.ui.theme.Hairline
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.theme.SurfaceWhite
import com.example.ui.util.ChartMath

/**
 * Smooth area chart (reference style): curved line, gradient fill fading to transparent,
 * faint dashed grid, muted labels. Tap a day to select it; [selectedIndex] gets a dot.
 */
@Composable
fun SpendingTrendChart(
    values: List<Double>,
    labels: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = InkTertiary)
    val selectedLabelStyle = MaterialTheme.typography.labelSmall.copy(color = InkSecondary)
    val grid = ChartMath.gridValues(values.maxOrNull() ?: 0.0)
    val top = grid.last()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .testTag("spending_trend_chart")
            .pointerInput(values.size) {
                detectTapGestures { offset ->
                    if (values.isEmpty()) return@detectTapGestures
                    val leftInset = 44.dp.toPx()
                    val slot = (size.width - leftInset) / values.size
                    val index = ((offset.x - leftInset) / slot).toInt().coerceIn(0, values.lastIndex)
                    onSelect(index)
                }
            }
    ) {
        if (values.isEmpty()) return@Canvas
        val leftInset = 44.dp.toPx()
        val bottomInset = 24.dp.toPx()
        val topInset = 8.dp.toPx()
        val plotHeight = size.height - bottomInset - topInset
        val slot = (size.width - leftInset) / values.size
        fun x(i: Int) = leftInset + slot * (i + 0.5f)
        fun y(v: Double) = topInset + plotHeight * (1f - (v / top).toFloat())

        // Grid: dashed lines at friendly amounts with compact labels.
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
        for (value in grid) {
            val gy = y(value)
            drawLine(Hairline, Offset(leftInset, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            val text = measurer.measure(ChartMath.compactRupees(value), labelStyle)
            drawText(text, topLeft = Offset(0f, gy - text.size.height / 2f))
        }

        // Day labels.
        labels.forEachIndexed { i, label ->
            val text = measurer.measure(label, if (i == selectedIndex) selectedLabelStyle else labelStyle)
            drawText(text, topLeft = Offset(x(i) - text.size.width / 2f, size.height - text.size.height))
        }

        // Curve: control points share the y of their end points, so the line never dips
        // below zero or overshoots a day's value.
        val points = values.mapIndexed { i, v -> Offset(x(i), y(v)) }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val cur = points[i]
                val midX = (prev.x + cur.x) / 2f
                cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, y(0.0))
            lineTo(points.first().x, y(0.0))
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(
                colors = listOf(Accent.copy(alpha = 0.28f), Accent.copy(alpha = 0.0f)),
                startY = topInset,
                endY = y(0.0)
            )
        )
        drawPath(line, color = Accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

        selectedIndex?.takeIf { it in points.indices }?.let { i ->
            val p = points[i]
            drawLine(Accent.copy(alpha = 0.25f), Offset(p.x, topInset), Offset(p.x, y(0.0)), strokeWidth = 1.dp.toPx())
            drawCircle(SurfaceWhite, radius = 7.dp.toPx(), center = p)
            drawCircle(Accent, radius = 4.5.dp.toPx(), center = p)
        }
    }
}
