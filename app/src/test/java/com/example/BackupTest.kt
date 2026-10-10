package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupData
import com.example.data.backup.BackupSerializer
import com.example.data.backup.InvalidBackupException
import com.example.data.local.BudgetEntity
import com.example.data.local.CustomCategoryEntity
import com.example.data.local.MerchantRuleEntity
import com.example.data.local.SmsImportEntity
import com.example.data.local.SmsTemplateEntity
import com.example.data.local.PennywiseDatabase
import com.example.data.local.TransactionEntity
import com.example.data.repository.PennywiseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        smsImports = listOf(
            SmsImportEntity("ref:BOI:EXPENSE:659909850362", "IMPORTED", 5L, 1_787_000_000_000L),
            SmsImportEntity("ref:KOTAK:EXPENSE:123", "SELF_TRANSFER", null, 1_787_000_100_000L)
        ),
        merchantRules = listOf(
            MerchantRuleEntity("swaraj poli bhaji kendra", "Food & Dining", false),
            MerchantRuleEntity("om patil", "Other", true)
        ),
        smsTemplates = listOf(
            SmsTemplateEntity(
                id = 3L,
                bank = "KOTAK",
                type = "EXPENSE",
                pattern = """Sent\s+Rs\.([\d,]+(?:\.\d{1,2})?)\s+to\s+(.+?)\s+on""",
                amountGroup = 1,
                payeeGroup = 2,
                dateGroup = null,
                dateFormat = null,
                refGroup = null,
                sample = "Sent Rs.10.00 to A on",
                createdAt = 7L
            )
        ),
        exportedAt = 42L
    )

    @Test
    fun `backup json round trips every field`() {
        assertEquals(sample, BackupSerializer.fromJson(BackupSerializer.toJson(sample)))
    }

    @Test
    fun `backups without sms data still load`() {
        val oldFormat = BackupSerializer.toJson(sample.copy(smsImports = emptyList(), merchantRules = emptyList()))
            .replace(Regex(""",\s*"smsImports": \[\s*\],\s*"merchantRules": \[\s*\]"""), "")
        assertFalse("test setup must really drop the new fields", oldFormat.contains("smsImports"))
        val restored = BackupSerializer.fromJson(oldFormat)
        assertEquals(emptyList<SmsImportEntity>(), restored.smsImports)
        assertEquals(sample.transactions, restored.transactions)
    }

    @Test
    fun `unusable taught formats are dropped instead of failing the restore`() {
        val broken = sample.copy(smsTemplates = sample.smsTemplates + sample.smsTemplates.first().copy(id = 4L, pattern = "(unclosed"))
        val restored = BackupSerializer.fromJson(BackupSerializer.toJson(broken))
        assertEquals(sample.smsTemplates, restored.smsTemplates)
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
            assertEquals(sample.smsImports.map { it.dedupeKey }.toSet(), repository.smsImportKeys())
            assertEquals(sample.merchantRules.toSet(), repository.merchantRules().toSet())
            assertEquals(listOf(3L), repository.smsTemplatesOnce().map { it.id })

            repository.eraseAllData()
            assertEquals(emptyList<TransactionEntity>(), repository.allTransactions.first())
            assertEquals(emptySet<String>(), repository.smsImportKeys())
            assertEquals(emptyList<MerchantRuleEntity>(), repository.merchantRules())
            assertEquals(0, repository.smsTemplatesOnce().size)
        } finally {
            database.close()
        }
    }
}
