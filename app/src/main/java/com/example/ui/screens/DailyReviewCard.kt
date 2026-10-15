package com.example.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.domain.DailyReviewState
import com.example.ui.components.PwCard
import com.example.ui.components.PwPrimaryButton
import com.example.ui.components.Space
import com.example.ui.theme.AccentSoft
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.SurfaceWhite

/** Home: confirm the day is fully logged, and see the check-in and no-spend streaks. */
@Composable
fun DailyReviewCard(state: DailyReviewState, onConfirm: () -> Unit) {
    val streakLines = buildList {
        if (state.review.current > 0) {
            val freeze = if (state.review.freezes > 0) " · ❄️ freeze ready" else ""
            add("🔥 ${state.review.current}-day check-in streak$freeze")
        }
        if (state.noSpend.current > 0) {
            add("🌙 ${state.noSpend.current} no-spend day${if (state.noSpend.current == 1) "" else "s"} in a row")
        }
    }
    PwCard(
        color = if (state.reviewedToday) SurfaceWhite else AccentSoft,
        modifier = Modifier.fillMaxWidth().testTag("daily_review_card")
    ) {
        if (state.reviewedToday) {
            Text(
                if (state.spentToday) "✅ Checked in today" else "🌙 No-spend day · checked in",
                style = MaterialTheme.typography.titleSmall,
                color = Ink
            )
        } else {
            Text("Today's check-in", style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(
                if (state.spentToday) "Added everything you spent today?" else "Nothing spent today. A no-spend day?",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
        }
        streakLines.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
        }
        if (!state.reviewedToday) {
            Spacer(modifier = Modifier.height(Space.m))
            PwPrimaryButton(
                text = if (state.spentToday) "I've logged everything" else "Yes, no spending today",
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().testTag("daily_review_confirm_button")
            )
        }
    }
}
