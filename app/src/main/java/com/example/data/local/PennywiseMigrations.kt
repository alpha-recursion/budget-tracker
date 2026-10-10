package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PennywiseMigrations {

    /**
     * v1 -> v2:
     * - adds the `categories` table for user-created categories;
     * - removes the US-style demo transactions/budgets that v1 seeded on first launch. Rows are
     *   matched on every seeded field, so the user's own entries are never touched.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (" +
                    "`name` TEXT NOT NULL, `iconKey` TEXT NOT NULL, " +
                    "`colorHex` INTEGER NOT NULL, `type` TEXT NOT NULL, PRIMARY KEY(`name`))"
            )

            val demoTransactions = listOf(
                Triple("Monthly Salary", 4500.0, "Direct deposit from employer"),
                Triple("Apartment Rent", 1200.0, "October rent payment"),
                Triple("Whole Foods Groceries", 135.5, "Weekly grocery run"),
                Triple("Electric & Water Bill", 94.2, "City utility payment"),
                Triple("Freelance UI Design", 650.0, "Landing page design client"),
                Triple("Metro Transit Pass", 75.0, "Monthly subway card"),
                Triple("New Headphones", 129.99, "Noise-cancelling earbuds"),
                Triple("Movie Night & Snacks", 42.0, "Weekend cinema"),
                Triple("Coffee & Bagel", 8.75, "Morning espresso")
            )
            for ((title, amount, note) in demoTransactions) {
                db.execSQL(
                    "DELETE FROM transactions WHERE title = ? AND amount = ? AND note = ?",
                    arrayOf<Any>(title, amount, note)
                )
            }

            val demoBudgets = listOf(
                "Food & Dining" to 500.0,
                "Housing & Rent" to 1300.0,
                "Shopping" to 300.0,
                "Transportation" to 150.0,
                "Utilities & Bills" to 200.0,
                "Entertainment" to 150.0
            )
            for ((category, limit) in demoBudgets) {
                db.execSQL(
                    "DELETE FROM budgets WHERE category = ? AND monthlyLimit = ?",
                    arrayOf<Any>(category, limit)
                )
            }
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
