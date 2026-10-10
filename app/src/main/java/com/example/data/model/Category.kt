package com.example.data.model

enum class Category(
    val displayName: String,
    val iconKey: String,
    val defaultType: TransactionType,
    val colorHex: Long
) {
    FOOD_DINING("Food & Dining", "restaurant", TransactionType.EXPENSE, 0xFFEA580C),
    SHOPPING("Shopping", "shopping_bag", TransactionType.EXPENSE, 0xFF8B5CF6),
    HOUSING("Housing & Rent", "home", TransactionType.EXPENSE, 0xFF2563EB),
    TRANSPORTATION("Transportation", "directions_car", TransactionType.EXPENSE, 0xFF0284C7),
    UTILITIES("Utilities & Bills", "bolt", TransactionType.EXPENSE, 0xFF65A30D),
    ENTERTAINMENT("Entertainment", "movie", TransactionType.EXPENSE, 0xFFEC4899),
    HEALTHCARE("Healthcare", "local_hospital", TransactionType.EXPENSE, 0xFFE11D48),
    SALARY("Salary & Wages", "work", TransactionType.INCOME, 0xFF059669),
    FREELANCE("Freelance & Side Gig", "laptop", TransactionType.INCOME, 0xFF10B981),
    INVESTMENT("Investments", "trending_up", TransactionType.INCOME, 0xFFD97706),
    OTHER("Other", "more_horiz", TransactionType.EXPENSE, 0xFF64748B);

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
