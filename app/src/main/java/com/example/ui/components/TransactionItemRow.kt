package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.local.TransactionEntity
import com.example.data.model.CategoryInfo
import com.example.data.model.TransactionType
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.PositiveText
import com.example.ui.util.Formatters
import com.example.ui.util.LocalCategories
import com.example.ui.util.getCategoryIcon

/**
 * One transaction as a plain row, meant to sit inside a white card with other rows.
 * Tap to edit; long-press to delete ([onDelete], optional).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItemRow(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    showDate: Boolean = true
) {
    val category = CategoryInfo.resolve(transaction.category, LocalCategories.current)
    val isIncome = transaction.type.equals(TransactionType.INCOME.name, ignoreCase = true)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
            .padding(horizontal = Space.l, vertical = Space.m)
            .testTag("transaction_item_${transaction.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PwIconBadge(icon = getCategoryIcon(category.iconKey), tint = Color(category.colorHex))
        Spacer(modifier = Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (showDate) category.name + " · " + Formatters.formatDate(transaction.date) else category.name,
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(Space.s))
        // The amount is what people scan for: largest weight in the row.
        Text(
            text = Formatters.formatSignedCurrency(transaction.amount, transaction.type),
            style = MaterialTheme.typography.titleSmall,
            color = if (isIncome) PositiveText else Ink,
            maxLines = 1
        )
    }
}
