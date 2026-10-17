package com.example.ui.util

import androidx.compose.runtime.compositionLocalOf
import com.example.data.model.CategoryInfo

/** Built-in + user-created categories, provided once at the app root. */
val LocalCategories = compositionLocalOf { CategoryInfo.builtIns }

/** Icons offered when creating a category (keys understood by [getCategoryIcon]). */
val CategoryIconChoices: List<String> = listOf(
    "two_wheeler", "local_gas_station", "local_grocery_store", "local_cafe", "restaurant",
    "shopping_bag", "checkroom", "home", "bolt", "phone_android",
    "school", "local_hospital", "fitness_center", "spa", "movie",
    "sports_esports", "flight", "pets", "card_giftcard", "people",
    "savings", "work", "laptop", "trending_up", "more_horiz"
)

/** Colours offered when creating a category. */
/**
 * v4 category palette: 12 hues deep enough to read as icon tints on white and as 12% chip
 * backgrounds, in rows of 6 around the colour wheel, ending with the v4 purple, pink and slate.
 */
val CategoryColorChoices: List<Long> = listOf(
    0xFFDC2626, 0xFFEA580C, 0xFFD97706, 0xFF65A30D, 0xFF1F7A3D, 0xFF0F766E,
    0xFF0284C7, 0xFF2563EB, 0xFF4F46E5, 0xFF6D28D9, 0xFFDB2777, 0xFF475569
)

/** Default for a new savings goal: the palette's blue. */
val DefaultGoalColor: Long = 0xFF2563EB

/** How many transactions use each category; pickers show the most-used first. */
val LocalCategoryUsage = compositionLocalOf { emptyMap<String, Int>() }

/** Most-used first (ties keep the original order); "Other" always last. */
fun orderByUsage(categories: List<CategoryInfo>, usage: Map<String, Int>): List<CategoryInfo> {
    val other = com.example.data.model.Category.OTHER.displayName
    return categories
        .withIndex()
        .sortedWith(
            compareBy<IndexedValue<CategoryInfo>> { it.value.name == other }
                .thenByDescending { usage[it.value.name] ?: 0 }
                .thenBy { it.index }
        )
        .map { it.value }
}
