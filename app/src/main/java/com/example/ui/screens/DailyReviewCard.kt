package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.DailyReviewState
import com.example.domain.SafeToSpend
import com.example.ui.components.PillShape
import com.example.ui.components.accentShadow
import com.example.ui.theme.Accent
import com.example.ui.theme.Hairline
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.PositiveSoft
import com.example.ui.theme.PositiveText
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.WarningText
import com.example.ui.util.Formatters
import kotlin.math.abs

/**
 * Home strip under the hero: what is safe to spend today and the daily check-in, in one row.
 * Once checked in, the check-in shrinks to a small streak chip so the strip stays compact.
 */
@Composable
fun TodayCard(
    review: DailyReviewState,
    safe: SafeToSpend?,
    onConfirm: () -> Unit,
    onSetIncome: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceWhite)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
            .testTag("daily_review_card"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (safe != null) {
                val over = safe.today < 0
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        Formatters.formatWholeRupees(abs(safe.today)),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (over) WarningText else Ink,
                        modifier = Modifier.testTag("safe_to_spend_amount")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (over) "over today's allowance" else "safe to spend today",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                val days = "${safe.daysLeft} day${if (safe.daysLeft == 1) "" else "s"}"
                Text(
                    "${Formatters.formatWholeRupees(safe.spentToday)} spent today · " +
                        if (safe.fromBudgets) "$days left this month" else "$days to payday",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary
                )
            } else {
                Text("Today", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                Text(
                    "Set income & payday to see what's safe to spend",
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent,
                    modifier = Modifier
                        .clickable(onClick = onSetIncome)
                        .testTag("set_income_link")
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (review.reviewedToday) {
            CheckedInChip(streak = review.review.current, noSpend = !review.spentToday)
        } else {
            Text(
                if (review.spentToday) "Logged all" else "No-spend day",
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
}

/** "✓ 5-day": today is checked in; replaces the full-width green bar. */
@Composable
private fun CheckedInChip(streak: Int, noSpend: Boolean) {
    val label = if (streak > 1) "$streak-day" else "Done"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(PillShape)
            .background(PositiveSoft)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics {
                contentDescription = "Checked in" + (if (streak > 1) ", $streak-day streak" else "") +
                    if (noSpend) ", no-spend day" else ""
            }
            .testTag("checked_in_chip")
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = PositiveText, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = PositiveText)
    }
}
