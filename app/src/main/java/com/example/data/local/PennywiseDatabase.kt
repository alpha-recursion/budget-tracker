package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TransactionEntity::class, BudgetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PennywiseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao

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
                .fallbackToDestructiveMigration(true)
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
