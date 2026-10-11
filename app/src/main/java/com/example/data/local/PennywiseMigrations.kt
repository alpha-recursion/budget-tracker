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

    /** v2 -> v3: SMS import bookkeeping and remembered payee choices. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sms_imports` (" +
                    "`dedupeKey` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                    "`transactionId` INTEGER, `receivedAt` INTEGER NOT NULL, PRIMARY KEY(`dedupeKey`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `merchant_rules` (" +
                    "`counterpartyKey` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                    "`isSelfTransfer` INTEGER NOT NULL, PRIMARY KEY(`counterpartyKey`))"
            )
        }
    }

    /** v3 -> v4: SMS formats taught by the owner. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sms_templates` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bank` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, `pattern` TEXT NOT NULL, `amountGroup` INTEGER NOT NULL, " +
                    "`payeeGroup` INTEGER, `dateGroup` INTEGER, `dateFormat` TEXT, `refGroup` INTEGER, " +
                    "`sample` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
        }
    }

    /** v4 -> v5: money lent and borrowed (DebtEntryEntity). */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `debts` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `person` TEXT NOT NULL, " +
                    "`personKey` TEXT NOT NULL, `kind` TEXT NOT NULL, `amount` REAL NOT NULL, " +
                    "`date` INTEGER NOT NULL, `dueDate` INTEGER, `note` TEXT NOT NULL, `smsKey` TEXT)"
            )
        }
    }

    /** v5 -> v6: savings goals and the money set aside for them. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `goals` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`target` REAL NOT NULL, `iconKey` TEXT NOT NULL, `colorHex` INTEGER NOT NULL, " +
                    "`targetDate` INTEGER, `createdAt` INTEGER NOT NULL, " +
                    "`celebratedMilestone` INTEGER NOT NULL, `completedAt` INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `goal_entries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, " +
                    "`amount` REAL NOT NULL, `date` INTEGER NOT NULL, `note` TEXT NOT NULL)"
            )
        }
    }

    /** v6 -> v7: "I bought it" on goals and named goal checkpoints. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `goals` ADD COLUMN `purchasedAt` INTEGER")
            db.execSQL("ALTER TABLE `goals` ADD COLUMN `purchasedAmount` REAL")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `goal_milestones` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, `amount` REAL NOT NULL, `celebrated` INTEGER NOT NULL)"
            )
        }
    }

    /** v7 -> v8: monthly challenges. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `challenges` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `month` INTEGER NOT NULL, " +
                    "`type` TEXT NOT NULL, `category` TEXT, `target` REAL NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
    )
}
