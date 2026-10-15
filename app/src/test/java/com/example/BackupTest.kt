package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupData
import com.example.data.backup.BackupSerializer
import com.example.data.backup.InvalidBackupException
import com.example.data.local.BudgetEntity
import com.example.data.local.ChallengeEntity
import com.example.data.local.DebtEntryEntity
import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.data.local.GoalMilestoneEntity
import com.example.data.local.CustomCategoryEntity
import com.example.data.local.MerchantRuleEntity
import com.example.data.local.RecurringEntity
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
        debts = listOf(
            DebtEntryEntity(1L, "Rahul", "rahul", "LENT", 2000.0, 1_787_000_000_000L, 1_788_000_000_000L, "trip", "ref:KOTAK:EXPENSE:9"),
            DebtEntryEntity(2L, "Rahul", "rahul", "GOT_BACK", 500.0, 1_787_100_000_000L, null, "", null)
        ),
        goals = listOf(
            GoalEntity(7L, "New phone", 20000.0, "phone_android", 0xFF0984E3, 1_790_000_000_000L, 1_787_000_000_000L, 25, null),
            GoalEntity(8L, "Helmet", 3000.0, "two_wheeler", 0xFF00B894, null, 1_786_000_000_000L, 100, 1_786_500_000_000L, 1_786_600_000_000L, 2800.0)
        ),
        goalMilestones = listOf(GoalMilestoneEntity(1L, 7L, "First ₹1,000", 1000.0, true)),
        challenges = listOf(
            ChallengeEntity(1L, 202610, "CATEGORY_LIMIT", "Food & Dining", 2700.0, 1_790_000_000_000L),
            ChallengeEntity(2L, 202610, "NO_SPEND_DAYS", null, 8.0, 1_790_000_000_000L)
        ),
        recurring = listOf(
            RecurringEntity(1L, "Netflix", 649.0, "Entertainment", "MONTHLY", 1_788_000_000_000L, true, 1_787_000_000_000L),
            RecurringEntity(2L, "Insurance", 12000.0, "Utilities & Bills", "YEARLY", 1_790_000_000_000L, false, 1_787_000_000_000L)
        ),
        goalEntries = listOf(
            GoalEntryEntity(1L, 7L, 6000.0, 1_787_000_000_000L, "Already saved"),
            GoalEntryEntity(2L, 7L, -500.0, 1_787_200_000_000L, "")
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
            assertEquals(sample.debts.toSet(), repository.debts.first().toSet())
            assertEquals(sample.goals.toSet(), repository.goals.first().toSet())
            assertEquals(sample.goalEntries.toSet(), repository.goalEntries.first().toSet())

            repository.eraseAllData()
            assertEquals(emptyList<TransactionEntity>(), repository.allTransactions.first())
            assertEquals(emptySet<String>(), repository.smsImportKeys())
            assertEquals(emptyList<MerchantRuleEntity>(), repository.merchantRules())
            assertEquals(0, repository.smsTemplatesOnce().size)
            assertEquals(emptyList<DebtEntryEntity>(), repository.debts.first())
            assertEquals(emptyList<GoalEntity>(), repository.goals.first())
        } finally {
            database.close()
        }
    }
}
