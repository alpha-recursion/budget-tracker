package com.example.ui.screens

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
        PwTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = "Search transactions",
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
                .padding(horizontal = Space.screen, vertical = Space.s)
                .testTag("search_transactions_input")
        )

        // Type filter + sort.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            listOf(null to "All", TransactionType.EXPENSE.name to "Expenses", TransactionType.INCOME.name to "Income")
                .forEach { (type, label) ->
                    PwChip(
                        text = label,
                        selected = selectedFilterType == type,
                        onClick = { onFilterTypeChange(type) },
                        unselectedColor = SurfaceWhite,
                        modifier = Modifier.testTag("tab_filter_$label")
                    )
                }
            Spacer(modifier = Modifier.weight(1f))
            SortMenu(current = currentSortOption, onSelect = onSortOptionChange)
        }

        // Category filter.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            PwChip(
                text = "All categories",
                selected = selectedCategory == null,
                onClick = { onCategoryChange(null) },
                unselectedColor = SurfaceWhite,
                modifier = Modifier.testTag("category_chip_all")
            )
            LocalCategories.current.forEach { cat ->
                PwChip(
                    text = cat.name,
                    selected = selectedCategory == cat.name,
                    onClick = { onCategoryChange(if (selectedCategory == cat.name) null else cat.name) },
                    unselectedColor = SurfaceWhite,
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(cat.colorHex))
                        )
                    },
                    modifier = Modifier.testTag("category_chip_${cat.name}")
                )
            }
        }

        // Count + clear. Fixed min height so the list doesn't jump when "Clear" appears.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(horizontal = Space.screen),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (transactions.size == 1) "1 transaction" else "${transactions.size} transactions",
                style = MaterialTheme.typography.labelMedium,
                color = InkSecondary,
                modifier = Modifier.weight(1f)
            )
            if (hasFilters) {
                Text(
                    "Clear filters",
                    style = MaterialTheme.typography.labelLarge,
                    color = Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            focusManager.clearFocus()
                            onSearchChange("")
                            onFilterTypeChange(null)
                            onCategoryChange(null)
                        }
                        .padding(horizontal = Space.s, vertical = Space.xs)
                        .testTag("clear_filters_button")
                )
            }
        }

        if (transactions.isEmpty()) {
            PwCard(modifier = Modifier.padding(Space.screen)) {
                Text("No transactions found", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    if (hasFilters) "Try a different search or filter." else "Tap + to add your first one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
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
                            Text(section.title, style = MaterialTheme.typography.labelLarge, color = Ink, modifier = Modifier.weight(1f))
                            section.spent?.takeIf { it > 0 }?.let {
                                Text("−" + Formatters.formatCurrency(it), style = MaterialTheme.typography.labelMedium, color = InkTertiary)
                            }
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
            }
        }
    }
}

@Composable
private fun SortMenu(current: SortOption, onSelect: (SortOption) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PwChip(
            text = current.label,
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
