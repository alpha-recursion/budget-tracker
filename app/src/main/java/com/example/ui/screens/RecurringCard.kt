package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.local.RecurringEntity
import com.example.domain.Recurring
import com.example.ui.components.PwCard
import com.example.ui.components.PwIconBadge
import com.example.ui.components.Space
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.util.Formatters

/** Home: what subscriptions and bills still take out of this month, and what is next. */
@Composable
fun RecurringCard(items: List<RecurringEntity>, onOpen: () -> Unit) {
    val month = Recurring.month(items, System.currentTimeMillis())
    PwCard(
        onClick = onOpen,
        contentPadding = PaddingValues(Space.l),
        modifier = Modifier.testTag("recurring_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PwIconBadge(icon = Icons.Default.Repeat, tint = Accent)
            Spacer(modifier = Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                if (month.next == null) {
                    Text("Recurring payments", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text("Subscriptions and bills, with reminders", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                } else {
                    Text("Committed this month", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Text(
                        if (month.remainingCount == 0) "Nothing more to pay" else "${Formatters.formatCurrency(month.remaining)} still to pay",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        modifier = Modifier.testTag("recurring_card_total")
                    )
                    Text(
                        "Next: ${month.next.item.name} · ${Formatters.formatShortDate(month.next.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkTertiary)
        }
    }
}
