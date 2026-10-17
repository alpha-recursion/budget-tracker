package com.example.data.model

enum class Category(
    val displayName: String,
    val iconKey: String,
    val defaultType: TransactionType,
    val colorHex: Long
) {
    FOOD_DINING("Food & Dining", "restaurant", TransactionType.EXPENSE, 0xFFFF7675),
    SHOPPING("Shopping", "shopping_bag", TransactionType.EXPENSE, 0xFF6C5CE7),
    HOUSING("Housing & Rent", "home", TransactionType.EXPENSE, 0xFF4834D4),
    TRANSPORTATION("Transportation", "directions_car", TransactionType.EXPENSE, 0xFF0984E3),
    UTILITIES("Utilities & Bills", "bolt", TransactionType.EXPENSE, 0xFF00B894),
    ENTERTAINMENT("Entertainment", "movie", TransactionType.EXPENSE, 0xFFFD79A8),
    HEALTHCARE("Healthcare", "local_hospital", TransactionType.EXPENSE, 0xFFE84393),
    // Finer everyday categories (Oct 2026), so auto-categorising can be specific.
    TEA_SNACKS("Tea & Snacks", "local_cafe", TransactionType.EXPENSE, 0xFFD97706),
    GROCERIES("Groceries", "local_grocery_store", TransactionType.EXPENSE, 0xFF65A30D),
    FUEL_VEHICLE("Fuel & Vehicle", "local_gas_station", TransactionType.EXPENSE, 0xFFEA580C),
    MOBILE_RECHARGE("Mobile & Recharge", "phone_android", TransactionType.EXPENSE, 0xFF0284C7),
    SALARY("Salary & Wages", "work", TransactionType.INCOME, 0xFF00CEC9),
    FREELANCE("Freelance & Side Gig", "laptop", TransactionType.INCOME, 0xFFA29BFE),
    INVESTMENT("Investments", "trending_up", TransactionType.INCOME, 0xFFFDCB6E),
    OTHER("Other", "more_horiz", TransactionType.EXPENSE, 0xFF636E72);

    companion object {
        fun fromDisplayName(name: String): Category {
            return entries.firstOrNull { 
                it.displayName.equals(name, ignoreCase = true) || it.name.equals(name, ignoreCase = true) 
            } ?: OTHER
        }

        fun getCategoriesForType(type: TransactionType): List<Category> {
            return when (type) {
                TransactionType.EXPENSE -> entries.filter { it.defaultType == TransactionType.EXPENSE || it == OTHER }
                TransactionType.INCOME -> entries.filter { it.defaultType == TransactionType.INCOME || it == OTHER }
            }
        }
    }
}
