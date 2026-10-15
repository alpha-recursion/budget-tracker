package com.example.ui.screens

import com.example.ui.theme.PositiveText
import com.example.ui.theme.AccentSoft
import com.example.ui.components.softShadow
import com.example.ui.components.PwSecondaryButton
import com.example.ui.components.PwSegmented
import com.example.ui.components.PillShape
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.SearchOff
import com.example.domain.InputGuard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.local.TransactionEntity
import com.example.data.model.TransactionType
import com.example.ui.SortOption
import com.example.ui.components.PwCard
import com.example.ui.components.PwChip
import com.example.ui.components.PwDivider
import com.example.ui.components.PwTextField
import com.example.ui.components.Space
import com.example.ui.components.TransactionItemRow
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.theme.SurfaceWhite
import com.example.ui.util.Formatters
import com.example.ui.util.LocalCategories
import java.util.Calendar

/** Rows of one day (or one block when sorted by amount). */
private data class TransactionSection(val key: String, val title: String, val spent: Double?, val items: List<TransactionEntity>)

private fun dayKey(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
fun TransactionsScreen(
    transactions: List<TransactionEntity>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilterType: String?, // null = All, "EXPENSE", "INCOME"
    onFilterTypeChange: (String?) -> Unit,
    selectedCategory: String?,
    onCategoryChange: (String?) -> Unit,
    currentSortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    onTransactionDelete: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val byDate = currentSortOption == SortOption.NEWEST || currentSortOption == SortOption.OLDEST
    val sections = remember(transactions, currentSortOption) {
        if (!byDate) {
            listOf(TransactionSection("sorted", "Sorted by " + currentSortOption.label.lowercase(), null, transactions))
        } else {
            // Input is already sorted by date; keep that order while grouping by day.
            transactions.groupBy { dayKey(it.date) }.map { (day, items) ->
                TransactionSection(
                    key = "day_$day",
                    title = Formatters.formatDate(day),
                    spent = items.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amount },
                    items = items
                )
            }
        }
    }
    val hasFilters = searchQuery.isNotEmpty() || selectedFilterType != null || selectedCategory != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("transactions_screen")
    ) {
        // Search pill + sort (v4).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.screen, end = Space.screen, top = Space.xs, bottom = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            PwTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = "Search payee, category or note",
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = InkSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = InkSecondary)
                        }
                    }
                },
                // Let the keyboard's search key dismiss the keyboard so results are visible.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                filter = InputGuard::search,
                containerColor = SurfaceWhite,
                modifier = Modifier
                    .weight(1f)
                    .softShadow(PillShape, 4.dp)
                    .clip(PillShape)
                    .testTag("search_transactions_input")
            )
            SortMenu(current = currentSortOption, onSelect = onSortOptionChange)
        }

        // All / Expenses / Income with a sliding thumb.
        val types = listOf(null, TransactionType.EXPENSE.name, TransactionType.INCOME.name)
        PwSegmented(
            options = listOf("All", "Expenses", "Income"),
            selectedIndex = types.indexOf(selectedFilterType).coerceAtLeast(0),
            onSelect = { onFilterTypeChange(types[it]) },
            height = 30.dp,
            modifier = Modifier
                .padding(horizontal = Space.screen, vertical = Space.xs)
                .testTag("type_filter")
        )

        // Category filter: only categories of the chosen type (plus Other).
        val categoryPool = LocalCategories.current.filter {
            selectedFilterType == null || it.type.name == selectedFilterType || it.name == "Other"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.s),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PwChip(
                text = "All categories",
                selected = selectedCategory == null,
                onClick = { onCategoryChange(null) },
                modifier = Modifier.testTag("category_chip_all")
            )
            categoryPool.forEach { cat ->
                val selected = selectedCategory == cat.name
                PwChip(
                    text = cat.name,
                    selected = selected,
                    onClick = { onCategoryChange(if (selected) null else cat.name) },
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (selected) SurfaceWhite else Color(cat.colorHex))
                        )
                    },
                    modifier = Modifier.testTag("category_chip_${cat.name}")
                )
            }
        }

        // Count, totals of what is shown, and clear. Fixed min height so the list doesn't jump.
        val shownSpent = transactions.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amount }
        val shownIn = transactions.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amount }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .padding(horizontal = Space.screen + Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (transactions.size == 1) "1 transaction" else "${transactions.size} transactions",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
            if (hasFilters) {
                Text(
                    "Clear",
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                    modifier = Modifier
                        .padding(start = Space.s)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            focusManager.clearFocus()
                            onSearchChange("")
                            onFilterTypeChange(null)
                            onCategoryChange(null)
                        }
                        .padding(horizontal = Space.xs, vertical = Space.xs)
                        .testTag("clear_filters_button")
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("−" + Formatters.formatCurrency(shownSpent), style = MaterialTheme.typography.labelMedium, color = Ink)
            Text(" · ", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            Text("+" + Formatters.formatCurrency(shownIn), style = MaterialTheme.typography.labelMedium, color = PositiveText)
        }

        if (transactions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (hasFilters) SurfaceWhite else AccentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (hasFilters) Icons.Default.SearchOff else Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = if (hasFilters) InkSecondary else Accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(Space.m))
                Text(if (hasFilters) "No matches" else "No transactions yet", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = Ink)
                Text(
                    if (hasFilters) "Nothing fits this search and these filters."
                    else "Add your first one with +, or import past payments from Kotak & BOI SMS.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = InkSecondary,
                    textAlign = TextAlign.Center
                )
                if (hasFilters) {
                    Spacer(modifier = Modifier.height(Space.m))
                    PwSecondaryButton(
                        text = "Clear filters",
                        onClick = {
                            focusManager.clearFocus()
                            onSearchChange("")
                            onFilterTypeChange(null)
                            onCategoryChange(null)
                        },
                        modifier = Modifier.height(40.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("transactions_list"),
                contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = Space.xxl)
            ) {
                sections.forEach { section ->
                    item(key = "header_${section.key}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .padding(start = Space.xs, end = Space.xs, top = Space.l, bottom = Space.s),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(section.title, style = MaterialTheme.typography.labelMedium, color = Ink, modifier = Modifier.weight(1f))
                            val received = section.items.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amount }
                            when {
                                (section.spent ?: 0.0) > 0 -> "−" + Formatters.formatCurrency(section.spent ?: 0.0)
                                section.spent != null && received > 0 -> "+" + Formatters.formatCurrency(received)
                                else -> null
                            }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = InkSecondary) }
                        }
                    }
                    itemsIndexed(section.items, key = { _, tx -> tx.id }) { index, transaction ->
                        val last = section.items.lastIndex
                        // One rounded white block per section, built from per-row shapes.
                        val shape = when {
                            last == 0 -> RoundedCornerShape(20.dp)
                            index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                            index == last -> RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .clip(shape)
                                .background(SurfaceWhite)
                        ) {
                            if (index > 0) PwDivider()
                            TransactionItemRow(
                                transaction = transaction,
                                onClick = { onTransactionClick(transaction) },
                                onDelete = { onTransactionDelete(transaction) },
                                showDate = !byDate
                            )
                        }
                    }
                }
                item(key = "hint") {
                    Text(
                        "Tap to edit · long-press to delete",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.l)
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(current: SortOption, onSelect: (SortOption) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PwChip(
            text = current.label.replace(" amount", ""),
            selected = false,
            onClick = { open = true },
            unselectedColor = SurfaceWhite,
            leading = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = Ink, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.testTag("sort_menu")
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (option == current) Accent else Ink
                        )
                    },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                    modifier = Modifier.testTag("sort_chip_${option.name}")
                )
            }
        }
    }
}
