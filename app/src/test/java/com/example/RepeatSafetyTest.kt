package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DebtEntryEntity
import com.example.data.local.GoalEntity
import com.example.data.local.PennywiseDatabase
import com.example.data.local.TransactionEntity
import com.example.data.repository.PennywiseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Doing the same thing twice (a double tap) must not record it twice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepeatSafetyTest {

    private fun withRepository(block: suspend (PennywiseRepository) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = PennywiseDatabase.createInMemory(context)
        try {
            block(PennywiseRepository(database))
        } finally {
            database.close()
        }
    }

    @Test
    fun buyingAGoalTwiceRecordsOneExpense() = withRepository { repository ->
        repository.saveGoal(
            GoalEntity(name = "Helmet", target = 3000.0, iconKey = "two_wheeler", colorHex = 0xFF00B894, createdAt = 1L),
            initialAmount = 3000.0
        )
        val goalId = repository.goals.first().single().id
        val purchase = TransactionEntity(
            title = "Helmet", amount = 2800.0, type = "EXPENSE", category = "Shopping", date = 5L
        )

        repository.buyGoal(goalId, purchase)
        repository.buyGoal(goalId, purchase)

        assertEquals(1, repository.allTransactions.first().size)
    }

    @Test
    fun movingATransactionToLendingTwiceCreatesOneLoan() = withRepository { repository ->
        val id = repository.insertTransaction(
            TransactionEntity(title = "Rahul", amount = 500.0, type = "EXPENSE", category = "Other", date = 5L)
        )
        val entry = DebtEntryEntity(
            person = "Rahul", personKey = "rahul", kind = "LENT", amount = 500.0, date = 5L, note = ""
        )

        repository.moveTransactionToDebt(id, entry)
        repository.moveTransactionToDebt(id, entry)

        assertEquals(1, repository.debts.first().size)
        assertEquals(0, repository.allTransactions.first().size)
    }
}
