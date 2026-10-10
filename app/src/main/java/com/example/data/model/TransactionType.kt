package com.example.data.model

enum class TransactionType {
    EXPENSE,
    INCOME;

    fun displayName(): String = when (this) {
        EXPENSE -> "Expense"
        INCOME -> "Income"
    }
}
