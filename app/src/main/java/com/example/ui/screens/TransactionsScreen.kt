package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TransactionEntity
import com.example.data.model.TransactionType
import com.example.ui.SortOption
import com.example.ui.components.TransactionItemRow
import com.example.ui.theme.Hairline
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentSoft
import com.example.ui.util.LocalCategories

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
    val tabTitles = listOf("All", "Expenses", "Income")
    val focusManager = LocalFocusManager.current
    val selectedTabIndex = when (selectedFilterType) {
        TransactionType.EXPENSE.name -> 1
        TransactionType.INCOME.name -> 2
        else -> 0
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("transactions_screen")
    ) {
        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("search_transactions_input"),
            placeholder = { Text("Search transactions...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            // Let the keyboard's search key dismiss the keyboard so results are visible.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Hairline.copy(alpha = 0.6f)
            )
        )

        // Type Filter Tabs
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = Accent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = Accent
                )
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        val newType = when (index) {
                            1 -> TransactionType.EXPENSE.name
                            2 -> TransactionType.INCOME.name
                            else -> null
                        }
                        onFilterTypeChange(newType)
                    },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_filter_$title")
                )
            }
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategoryChange(null) },
                label = { Text("All Categories") },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentSoft,
                    selectedLabelColor = Accent
                ),
                modifier = Modifier.testTag("category_chip_all")
            )

            LocalCategories.current.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat.name,
                    onClick = {
                        if (selectedCategory == cat.name) onCategoryChange(null)
                        else onCategoryChange(cat.name)
                    },
                    label = { Text(cat.name) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(cat.colorHex).copy(alpha = 0.15f),
                        selectedLabelColor = Color(cat.colorHex)
                    ),
                    modifier = Modifier.testTag("category_chip_${cat.name}")
                )
            }
        }

        // Sort Options Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )

            SortOption.entries.forEach { option ->
                val isSelected = currentSortOption == option
                FilterChip(
                    selected = isSelected,
                    onClick = { onSortOptionChange(option) },
                    label = { Text(option.label, fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentSoft,
                        selectedLabelColor = Accent
                    ),
                    modifier = Modifier.testTag("sort_chip_${option.name}")
                )
            }
        }

        // Count Indicator & Clear Filters
        // Fixed min height: the TextButton is taller than the label, so the list used to jump
        // down/up whenever "Clear filters" appeared or disappeared.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (transactions.size == 1) "1 transaction" else "${transactions.size} transactions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (searchQuery.isNotEmpty() || selectedFilterType != null || selectedCategory != null) {
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        onSearchChange("")
                        onFilterTypeChange(null)
                        onCategoryChange(null)
                    },
                    modifier = Modifier.testTag("clear_filters_button")
                ) {
                    Text("Clear filters", style = MaterialTheme.typography.labelSmall, color = Accent)
                }
            }
        }

        // Transactions List
        if (transactions.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No transactions found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Try adjusting your search or filters",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("transactions_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = transactions,
                    key = { it.id }
                ) { transaction ->
                    TransactionItemRow(
                        transaction = transaction,
                        onClick = { onTransactionClick(transaction) },
                        onDelete = { onTransactionDelete(transaction) },
                        // Animate inserts, removals and re-ordering instead of snapping rows around.
                        modifier = Modifier.animateItem()
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}
