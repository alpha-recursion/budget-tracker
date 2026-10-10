package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BudgetEntity
import com.example.data.local.PennywiseDatabase
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: PennywiseDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = PennywiseDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context verifies app_name is Pennywise`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Pennywise", appName)
    }

    @Test
    fun `launch MainActivity directly`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }

    @Test
    fun `test room database transaction insert and query`() = runBlocking {
        val dao = database.transactionDao()
        val tx = TransactionEntity(
            title = "Test Coffee",
            amount = 4.50,
            type = TransactionType.EXPENSE.name,
            category = Category.FOOD_DINING.displayName,
            date = System.currentTimeMillis()
        )

        val id = dao.insert(tx)
        val loaded = dao.getTransactionById(id)

        assertNotNull(loaded)
        assertEquals("Test Coffee", loaded?.title)
        assertEquals(4.50, loaded?.amount ?: 0.0, 0.001)

        val all = dao.getAllTransactions().first()
        assertEquals(1, all.size)

        // Test update
        val updated = loaded!!.copy(title = "Updated Coffee", amount = 5.00)
        dao.update(updated)
        val reloaded = dao.getTransactionById(id)
        assertEquals("Updated Coffee", reloaded?.title)
        assertEquals(5.00, reloaded?.amount ?: 0.0, 0.001)

        // Test delete
        dao.deleteById(id)
        val afterDelete = dao.getAllTransactions().first()
        assertEquals(0, afterDelete.size)
    }

    @Test
    fun `test room database budget insert and query`() = runBlocking {
        val dao = database.budgetDao()
        dao.insertOrUpdate(BudgetEntity(category = "Food & Dining", monthlyLimit = 400.0))

        val budgets = dao.getAllBudgets().first()
        assertEquals(1, budgets.size)
        assertEquals("Food & Dining", budgets[0].category)
        assertEquals(400.0, budgets[0].monthlyLimit, 0.001)
    }
}
