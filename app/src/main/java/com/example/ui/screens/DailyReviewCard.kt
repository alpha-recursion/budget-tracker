package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.DailyReviewState
import com.example.ui.components.PillShape
import com.example.ui.components.accentShadow
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentSoft
import com.example.ui.theme.Ink
import com.example.ui.theme.PositiveSoft
import com.example.ui.theme.PositiveText
import com.example.ui.theme.SurfaceWhite

/** Home strip under the hero: confirm the day is fully logged, or see that it is (v4). */
@Composable
fun DailyReviewCard(state: DailyReviewState, onConfirm: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    if (state.reviewedToday) {
        val streak = state.review.current
        val noSpend = if (state.spentToday) "" else " · no-spend day"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(PositiveSoft)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("daily_review_card"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PositiveText, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Checked in" + (if (streak > 1) " · $streak-day streak" else "") + noSpend,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = PositiveText
            )
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AccentSoft)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
            .testTag("daily_review_card"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Nightlight, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Today's check-in", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink)
            Text(
                when {
                    state.spentToday -> "Added everything you spent today?"
                    state.review.current > 0 -> "Nothing spent today yet · ${state.review.current}-day streak"
                    else -> "Nothing spent today yet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.8f)
            )
        }
        Text(
            if (state.spentToday) "Logged all" else "No-spend day",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = SurfaceWhite,
            modifier = Modifier
                .accentShadow(PillShape, 6.dp)
                .clip(PillShape)
                .background(Accent)
                .clickable(onClick = onConfirm)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("daily_review_confirm_button")
        )
    }
}
