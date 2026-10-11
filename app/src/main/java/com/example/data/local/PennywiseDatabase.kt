package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TransactionEntity::class,
        BudgetEntity::class,
        CustomCategoryEntity::class,
        SmsImportEntity::class,
        MerchantRuleEntity::class,
        SmsTemplateEntity::class,
        DebtEntryEntity::class,
        GoalEntity::class,
        GoalEntryEntity::class,
        GoalMilestoneEntity::class,
        ChallengeEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class PennywiseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun smsImportDao(): SmsImportDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun smsTemplateDao(): SmsTemplateDao
    abstract fun debtDao(): DebtDao
    abstract fun goalDao(): GoalDao
    abstract fun challengeDao(): ChallengeDao

    companion object {
        @Volatile
        private var INSTANCE: PennywiseDatabase? = null

        fun getDatabase(context: Context): PennywiseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PennywiseDatabase::class.java,
                    "pennywise_database"
                )
                // Never wipe the user's data on a schema change: every version bump must ship
                // an explicit Migration (see PennywiseMigrations). Only a downgrade, which
                // cannot be migrated, falls back to recreating the database.
                .addMigrations(*PennywiseMigrations.ALL)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemory(context: Context): PennywiseDatabase {
            return Room.inMemoryDatabaseBuilder(
                context,
                PennywiseDatabase::class.java
            )
            .allowMainThreadQueries()
            .build()
        }
    }
}
