package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupData
import com.example.data.backup.BackupSerializer
import com.example.data.backup.InvalidBackupException
import com.example.data.local.BudgetEntity
import com.example.data.local.CustomCategoryEntity
import com.example.data.local.PennywiseDatabase
import com.example.data.local.TransactionEntity
import com.example.data.repository.PennywiseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupTest {

    private val sample = BackupData(
        transactions = listOf(
            TransactionEntity(
                id = 5L,
                title = "Swaraj Poli Bhaji Kendra",
                amount = 100.0,
                type = "EXPENSE",
                category = "Food & Dining",
                date = 1_787_000_000_000L,
                note = "UPI \"lunch\" — ₹100"
            )
        ),
        budgets = listOf(BudgetEntity(category = "Bike & Fuel", monthlyLimit = 2500.0)),
        categories = listOf(CustomCategoryEntity("Bike & Fuel", "two_wheeler", 0xFF0984E3, "EXPENSE")),
        exportedAt = 42L
    )

    @Test
    fun `backup json round trips every field`() {
        assertEquals(sample, BackupSerializer.fromJson(BackupSerializer.toJson(sample)))
    }

    @Test
    fun `non pennywise or damaged files are rejected`() {
        assertThrows(InvalidBackupException::class.java) { BackupSerializer.fromJson("{\"hello\": 1}") }
        assertThrows(InvalidBackupException::class.java) { BackupSerializer.fromJson("not json") }
        assertThrows(InvalidBackupException::class.java) {
            BackupSerializer.fromJson("{\"app\":\"pennywise\",\"formatVersion\":99,\"transactions\":[]}")
        }
    }

    @Test
    fun `restore replaces all existing data`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = PennywiseDatabase.createInMemory(context)
        try {
            val repository = PennywiseRepository(database)
            repository.insertTransaction(
                TransactionEntity(title = "Old", amount = 1.0, type = "EXPENSE", category = "Other", date = 1L)
            )

            repository.restoreBackup(sample)

            assertEquals(sample.transactions, repository.allTransactions.first())
            assertEquals(sample.budgets, repository.allBudgets.first())
            assertEquals(sample.categories, repository.customCategories.first())

            repository.eraseAllData()
            assertEquals(emptyList<TransactionEntity>(), repository.allTransactions.first())
        } finally {
            database.close()
        }
    }
}
