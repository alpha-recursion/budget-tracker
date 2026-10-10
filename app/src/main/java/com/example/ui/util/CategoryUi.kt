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
val CategoryColorChoices: List<Long> = listOf(
    0xFFFF7675, 0xFFE17055, 0xFFFDCB6E, 0xFF00B894, 0xFF00CEC9,
    0xFF0984E3, 0xFF6C5CE7, 0xFFA29BFE, 0xFFE84393, 0xFF636E72
)
