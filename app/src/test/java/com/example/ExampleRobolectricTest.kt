package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BudgetEntity
import com.example.data.local.PennywiseDatabase
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.model.TransactionType
import com.example.data.repository.PennywiseRepository
import com.example.ui.PennywiseViewModel
import com.example.ui.SortOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: PennywiseDatabase
    private lateinit var repository: PennywiseRepository
    private lateinit var viewModel: PennywiseViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = PennywiseDatabase.createInMemory(context)
        repository = PennywiseRepository(database)
        viewModel = PennywiseViewModel(context, repository)
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
    fun `test navigation tabs switching`() {
        assertEquals(0, viewModel.selectedTab.value)

        // Switch to Transactions (Tab 1)
        viewModel.setTab(1)
        assertEquals(1, viewModel.selectedTab.value)

        // Switch to Analytics (Tab 2)
        viewModel.setTab(2)
        assertEquals(2, viewModel.selectedTab.value)

        // Return to Dashboard (Tab 0)
        viewModel.setTab(0)
        assertEquals(0, viewModel.selectedTab.value)
    }

    @Test
    fun `test filter and search state updates`() {
        // Search query
        viewModel.setSearchQuery("Groceries")
        assertEquals("Groceries", viewModel.searchQuery.value)
        viewModel.setSearchQuery("")
        assertEquals("", viewModel.searchQuery.value)

        // Filter type
        viewModel.setFilterType(TransactionType.EXPENSE.name)
        assertEquals(TransactionType.EXPENSE.name, viewModel.filterType.value)
        viewModel.setFilterType(TransactionType.INCOME.name)
        assertEquals(TransactionType.INCOME.name, viewModel.filterType.value)
        viewModel.setFilterType(null)
        assertNull(viewModel.filterType.value)

        // Category filter
        viewModel.setSelectedCategoryFilter(Category.FOOD_DINING.displayName)
        assertEquals(Category.FOOD_DINING.displayName, viewModel.selectedCategoryFilter.value)
        viewModel.setSelectedCategoryFilter(null)
        assertNull(viewModel.selectedCategoryFilter.value)

        // Sort option
        viewModel.setSortOption(SortOption.HIGHEST_AMOUNT)
        assertEquals(SortOption.HIGHEST_AMOUNT, viewModel.sortOption.value)
        viewModel.setSortOption(SortOption.NEWEST)
        assertEquals(SortOption.NEWEST, viewModel.sortOption.value)
    }

    @Test
    fun `test add transaction dialog flow and validation`() {
        viewModel.openAddDialog()
        assertTrue(viewModel.isAddEditOpen.value)
        assertNull(viewModel.editingTransaction.value)

        // Type toggle
        viewModel.updateFormType(TransactionType.INCOME)
        assertEquals(TransactionType.INCOME, viewModel.formType.value)
        viewModel.updateFormType(TransactionType.EXPENSE)
        assertEquals(TransactionType.EXPENSE, viewModel.formType.value)

        // Invalid amount
        viewModel.updateFormAmount("-10.00")
        viewModel.updateFormTitle("Invalid Test")
        assertFalse(viewModel.saveTransaction())
        assertNotNull(viewModel.amountError.value)
        assertTrue(viewModel.isAddEditOpen.value)

        // Empty title
        viewModel.updateFormAmount("25.00")
        viewModel.updateFormTitle("")
        assertFalse(viewModel.saveTransaction())
        assertNotNull(viewModel.titleError.value)
        assertTrue(viewModel.isAddEditOpen.value)

        // Valid form
        viewModel.updateFormTitle("Valid Grocery Trip")
        viewModel.updateFormCategory(Category.FOOD_DINING.displayName)
        val saveResult = viewModel.saveTransaction()
        assertTrue(saveResult)

        // Dismiss dialog
        viewModel.dismissAddEditDialog()
        assertFalse(viewModel.isAddEditOpen.value)
    }

    @Test
    fun `quick add income opens the dialog as income with an income category`() {
        viewModel.openAddDialog(TransactionType.INCOME)
        assertEquals(TransactionType.INCOME, viewModel.formType.value)
        assertTrue(
            Category.getCategoriesForType(TransactionType.INCOME)
                .any { it.displayName == viewModel.formCategory.value }
        )
    }

    @Test
    fun `custom category validation rejects blanks and duplicates`() {
        assertNotNull(viewModel.addCustomCategory("   ", TransactionType.EXPENSE, "two_wheeler", 0xFF0984E3))
        // Built-in names are taken, case-insensitively.
        assertNotNull(viewModel.addCustomCategory("shopping", TransactionType.EXPENSE, "two_wheeler", 0xFF0984E3))
        assertNull(viewModel.addCustomCategory("Bike & Fuel", TransactionType.EXPENSE, "two_wheeler", 0xFF0984E3))
    }

    @Test
    fun `deleting a custom category moves its transactions to Other`() = runBlocking {
        repository.saveCustomCategory(
            com.example.data.local.CustomCategoryEntity("Bike & Fuel", "two_wheeler", 0xFF0984E3, TransactionType.EXPENSE.name)
        )
        val id = database.transactionDao().insert(
            TransactionEntity(
                title = "Petrol",
                amount = 500.0,
                type = TransactionType.EXPENSE.name,
                category = "Bike & Fuel",
                date = 1000L
            )
        )
        repository.setBudget("Bike & Fuel", 2000.0)

        repository.deleteCustomCategory("Bike & Fuel")

        assertEquals(Category.OTHER.displayName, database.transactionDao().getTransactionById(id)?.category)
        assertTrue(database.budgetDao().getAllBudgetsOnce().none { it.category == "Bike & Fuel" })
        assertTrue(database.categoryDao().getAllCategoriesOnce().isEmpty())
    }

    @Test
    fun `sms import writes transactions, records and rules atomically`() = runBlocking {
        val tx = TransactionEntity(
            title = "Annapurna Poli Bhaji Kendra",
            amount = 100.0,
            type = TransactionType.EXPENSE.name,
            category = Category.FOOD_DINING.displayName,
            date = 1000L
        )
        val created = repository.importFromSms(
            items = listOf(
                com.example.data.repository.SmsImportItem("ref:BOI:EXPENSE:1", 1000L, tx),
                com.example.data.repository.SmsImportItem("ref:KOTAK:EXPENSE:2", 2000L, null)
            ),
            rules = listOf(com.example.data.local.MerchantRuleEntity("annapurna poli bhaji kendra", Category.FOOD_DINING.displayName, false))
        )

        assertEquals(1, created)
        assertEquals(1, database.transactionDao().getAllTransactionsOnce().size)
        assertEquals(setOf("ref:BOI:EXPENSE:1", "ref:KOTAK:EXPENSE:2"), repository.smsImportKeys())
        val records = database.smsImportDao().getAllOnce().associateBy { it.dedupeKey }
        assertNotNull(records.getValue("ref:BOI:EXPENSE:1").transactionId)
        assertEquals("SELF_TRANSFER", records.getValue("ref:KOTAK:EXPENSE:2").status)
        assertNull(records.getValue("ref:KOTAK:EXPENSE:2").transactionId)
        assertEquals(1, repository.merchantRules().size)
    }

    @Test
    fun `deleting a custom category also updates remembered payee rules`() = runBlocking {
        repository.saveCustomCategory(
            com.example.data.local.CustomCategoryEntity("Bike & Fuel", "two_wheeler", 0xFF0984E3, TransactionType.EXPENSE.name)
        )
        repository.importFromSms(
            items = emptyList(),
            rules = listOf(com.example.data.local.MerchantRuleEntity("indian oil", "Bike & Fuel", false))
        )

        repository.deleteCustomCategory("Bike & Fuel")

        assertEquals(Category.OTHER.displayName, repository.merchantRules().single().category)
    }

    @Test
    fun `remembering a vendor category updates the existing rule for that vendor`() = runBlocking {
        repository.importFromSms(
            items = emptyList(),
            rules = listOf(com.example.data.local.MerchantRuleEntity("expense:some other t c", "Other", false))
        )
        repository.rememberCategory(TransactionType.EXPENSE, "Some Other T Center", Category.FOOD_DINING.displayName)

        val rules = repository.merchantRules()
        assertEquals(1, rules.size) // no second spelling added
        assertEquals("expense:some other t c", rules.single().counterpartyKey)
        assertEquals(Category.FOOD_DINING.displayName, rules.single().category)
    }

    @Test
    fun `setCategory updates only the chosen transactions`() = runBlocking {
        val dao = database.transactionDao()
        val a = dao.insert(TransactionEntity(title = "A", amount = 1.0, type = "EXPENSE", category = "Other", date = 1L))
        val b = dao.insert(TransactionEntity(title = "B", amount = 1.0, type = "EXPENSE", category = "Other", date = 2L))
        repository.setCategory(listOf(a), Category.FOOD_DINING.displayName)
        assertEquals(Category.FOOD_DINING.displayName, dao.getTransactionById(a)?.category)
        assertEquals("Other", dao.getTransactionById(b)?.category)
    }

    @Test
    fun `budget limits are validated before saving`() {
        assertNotNull(viewModel.saveBudget(Category.FOOD_DINING.displayName, ""))
        assertNotNull(viewModel.saveBudget(Category.FOOD_DINING.displayName, "-100"))
        assertNotNull(viewModel.saveBudget("", "3000"))
        assertNull(viewModel.saveBudget(Category.FOOD_DINING.displayName, "₹3,000"))
    }

    @Test
    fun `edit dialog amount is locale independent`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            viewModel.openEditDialog(
                TransactionEntity(
                    id = 7L,
                    title = "Lunch",
                    amount = 12.5,
                    type = TransactionType.EXPENSE.name,
                    category = Category.FOOD_DINING.displayName,
                    date = 1000L
                )
            )
            assertEquals("12.50", viewModel.formAmount.value)
            assertEquals(12.5, com.example.domain.FormValidation.parseAmount(viewModel.formAmount.value)!!, 0.001)
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `test edit transaction dialog population`() {
        val testEntity = TransactionEntity(
            id = 42L,
            title = "Existing Coffee",
            amount = 4.75,
            type = TransactionType.EXPENSE.name,
            category = Category.FOOD_DINING.displayName,
            date = 1700000000000L,
            note = "Latte with oat milk"
        )

        viewModel.openEditDialog(testEntity)
        assertTrue(viewModel.isAddEditOpen.value)
        assertEquals(testEntity, viewModel.editingTransaction.value)
        assertEquals("Existing Coffee", viewModel.formTitle.value)
        assertEquals("4.75", viewModel.formAmount.value)
        assertEquals(Category.FOOD_DINING.displayName, viewModel.formCategory.value)
        assertEquals("Latte with oat milk", viewModel.formNote.value)

        viewModel.dismissAddEditDialog()
        assertFalse(viewModel.isAddEditOpen.value)
        assertNull(viewModel.editingTransaction.value)
    }

    @Test
    fun `test delete transaction prompt and confirmation flow`() {
        val testEntity = TransactionEntity(
            id = 99L,
            title = "To be deleted",
            amount = 100.0,
            type = TransactionType.EXPENSE.name,
            category = Category.OTHER.displayName,
            date = 1000L
        )

        viewModel.promptDelete(testEntity)
        assertEquals(testEntity, viewModel.transactionToDelete.value)

        viewModel.dismissDelete()
        assertNull(viewModel.transactionToDelete.value)
    }

    @Test
    fun `test room database transaction CRUD`() = runBlocking {
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
        assertTrue(all.any { it.id == id && it.title == "Test Coffee" })

        // Update
        val updated = loaded!!.copy(title = "Updated Coffee", amount = 5.00)
        dao.update(updated)
        val reloaded = dao.getTransactionById(id)
        assertEquals("Updated Coffee", reloaded?.title)
        assertEquals(5.00, reloaded?.amount ?: 0.0, 0.001)

        // Delete
        dao.deleteById(id)
        assertNull(dao.getTransactionById(id))
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

    @Test
    fun `currency format uses Indian Rupee symbol`() {
        val formatted = com.example.ui.util.Formatters.formatCurrency(1250.50)
        assertTrue(formatted.startsWith("₹"))
        assertTrue(formatted.contains("1,250.50"))

        val signedExpense = com.example.ui.util.Formatters.formatSignedCurrency(100.0, "EXPENSE")
        assertEquals("-₹100.00", signedExpense)

        val signedIncome = com.example.ui.util.Formatters.formatSignedCurrency(500.0, "INCOME")
        assertEquals("+₹500.00", signedIncome)
    }
}
