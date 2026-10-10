package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.CategoryBreakdown
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.util.Formatters

/**
 * Spending by category as a ring of rounded segments with small gaps (reference style),
 * with the total in the middle and the top categories listed beside it.
 */
@Composable
fun SpendingDonutChart(
    breakdowns: List<CategoryBreakdown>,
    totalExpense: Double,
    modifier: Modifier = Modifier
) {
    PwCard(modifier = modifier.testTag("spending_donut_chart")) {
        PwSectionTitle(title = "Spending by category", subtitle = "All time")
        Spacer(modifier = Modifier.height(Space.l))

        if (breakdowns.isEmpty() || totalExpense <= 0.0) {
            Text(
                "No spending recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary,
                modifier = Modifier.padding(vertical = Space.xl)
            )
            return@PwCard
        }

        // Grow the ring in once when it first appears.
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { started = true }
        val progress by animateFloatAsState(if (started) 1f else 0f, tween(800), label = "donut")

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(132.dp)) {
                    val stroke = 18.dp.toPx()
                    val diameter = size.minDimension - stroke
                    val radius = diameter / 2f
                    val topLeft = Offset(stroke / 2f, stroke / 2f)
                    val arcSize = Size(diameter, diameter)

                    if (breakdowns.size == 1) {
                        drawArc(Color(breakdowns.first().category.colorHex), -90f, 360f * progress, false, topLeft, arcSize,
                            style = Stroke(stroke))
                        return@Canvas
                    }
                    // A round cap extends half the stroke past each end of an arc; shorten
                    // each arc by that much (in degrees) plus a gap so segments never overlap.
                    val capDegrees = Math.toDegrees((stroke / 2f / radius).toDouble()).toFloat()
                    val gapDegrees = 4f
                    var start = -90f
                    for (item in breakdowns) {
                        val sweep = (item.percentage.toFloat() / 100f) * 360f * progress
                        val visible = sweep - 2 * capDegrees - gapDegrees
                        val color = Color(item.category.colorHex)
                        if (visible > 0f) {
                            drawArc(color, start + capDegrees + gapDegrees / 2f, visible, false, topLeft, arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Round))
                        } else if (sweep > 0.5f) {
                            // Too small for an arc: a dot keeps the category visible.
                            drawArc(color, start + sweep / 2f, 0.01f, false, topLeft, arcSize,
                                style = Stroke(stroke * 0.6f, cap = StrokeCap.Round))
                        }
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
                    val total = Formatters.formatCurrency(totalExpense)
                    Text(
                        total,
                        style = if (total.length > 9) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("spent", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                }
            }

            Spacer(modifier = Modifier.width(Space.xl))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                breakdowns.take(4).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(item.category.colorHex))
                        )
                        Spacer(modifier = Modifier.width(Space.s))
                        Text(
                            item.categoryName,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            Formatters.formatPercentage(item.percentage),
                            style = MaterialTheme.typography.labelLarge,
                            color = Ink
                        )
                    }
                }
            }
        }
    }
}
