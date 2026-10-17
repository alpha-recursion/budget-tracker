package com.example

import com.example.data.local.TransactionEntity
import com.example.data.model.CategoryInfo
import com.example.data.model.TransactionType
import com.example.domain.AutoCategorizer
import com.example.domain.AutoSuggestion
import com.example.domain.CategoryModel
import com.example.domain.GuessSource
import com.example.domain.sms.RawSms
import com.example.domain.sms.SmsImportPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCategorizerTest {

    private val all = CategoryInfo.builtIns.map { it.name }.toSet()
    private val expense = TransactionType.EXPENSE

    private fun suggest(payee: String, valid: Set<String> = all, model: CategoryModel = CategoryModel.EMPTY) =
        AutoCategorizer.suggest(payee, expense, valid, model)

    private fun category(payee: String, valid: Set<String> = all, model: CategoryModel = CategoryModel.EMPTY) =
        (suggest(payee, valid, model) as? AutoSuggestion.Category)?.name

    private fun tx(title: String, category: String, id: Long = 0, type: String = "EXPENSE") =
        TransactionEntity(id = id, title = title, amount = 50.0, type = type, category = category, date = id)

    @Test
    fun keywordsPlaceCommonIndianPayees() {
        assertEquals("Healthcare", category("Wellcare Pharmacy"))
        assertEquals("Healthcare", category("City Multispeciality Hospital"))
        assertEquals("Tea & Snacks", category("Ak pan shop"))
        assertEquals("Tea & Snacks", category("CORNER PANSHOP"))
        assertEquals("Tea & Snacks", category("Corner Tea Stall"))
        assertEquals("Food & Dining", category("Hotel Sunrise"))
        assertEquals("Food & Dining", category("Zomato"))
        assertEquals("Groceries", category("Sunrise Super Market"))
        assertEquals("Groceries", category("ZEPTO MARKETPLACE PRIVATE LIMITED"))
        assertEquals("Fuel & Vehicle", category("RIVERSIDE PETROLCIRCLE"))
        assertEquals("Mobile & Recharge", category("Jio Prepaid"))
        assertEquals("Transportation", category("ONE CITY NCMC CARD"))
        // A medical store is healthcare, not groceries.
        assertEquals("Healthcare", category("Sample Medical and General Stores"))
    }

    @Test
    fun matchedWordIsShownWhole() {
        val s = suggest("Green Cross PharmacyIpd") as AutoSuggestion.Category
        assertEquals(GuessSource.KEYWORD, s.source)
        assertEquals("pharmacyipd", s.matched)
    }

    @Test
    fun fallsBackToABroaderCategoryWhenTheFinerOneIsGone() {
        val withoutNew = all - setOf("Tea & Snacks", "Groceries", "Fuel & Vehicle", "Mobile & Recharge")
        assertEquals("Utilities & Bills", category("Airtel Prepaid", withoutNew))
        assertEquals("Food & Dining", category("Shree Krishna Tea Stall", withoutNew))
        assertEquals("Shopping", category("Daily Mart", withoutNew))
    }

    @Test
    fun peopleAreMarkedNotGuessed() {
        assertEquals(AutoSuggestion.Person, suggest("Mr Rohan Mehta"))
        assertEquals(AutoSuggestion.Person, suggest("Priya Nair"))
        assertFalse(AutoCategorizer.looksLikePerson("Mohan Enterprises"))
        assertFalse(AutoCategorizer.looksLikePerson("Lucky Star Wines"))
        assertFalse(AutoCategorizer.looksLikePerson("ZENTRO")) // one word: could be a brand
        assertFalse(AutoCategorizer.looksLikePerson("Sample MH 18"))
    }

    @Test
    fun learnsFromTheOwnersOwnChoices() {
        val model = CategoryModel.learn(
            listOf(
                tx("Shri Laxmi Narayan T Stola", "Tea & Snacks", 1),
                tx("Ganesh Enterprises", "Groceries", 2),
                tx("Ganesh Tailoring Works", "Shopping", 3),
                tx("Priya Nair", "Shopping", 4), // a person: not learned from
                tx("Unknown Payee", "Other", 5) // Other teaches nothing
            )
        )
        val s = suggest("Narayan Stola Corner", model = model) as AutoSuggestion.Category
        assertEquals("Tea & Snacks", s.name)
        assertEquals(GuessSource.LEARNED, s.source)
        assertEquals("narayan stola", s.matched)
        // "ganesh" points two ways: no guess.
        assertNull(suggest("Ganesh Store Point", model = model))
        // Learning never overrides a person.
        assertEquals(AutoSuggestion.Person, suggest("Priya Menon", model = model))
    }

    @Test
    fun sortOutOtherSuggestsPerPayee() {
        val txs = listOf(
            tx("Wellcare Pharmacy", "Other", 1),
            tx("WELLCARE PHARMACY", "Other", 2),
            tx("Priya Nair", "Other", 3),
            tx("Hotel Sunrise", "Food & Dining", 4)
        )
        val fixes = AutoCategorizer.suggestForOther(txs, all, CategoryModel.learn(txs))
        val fix = fixes.single()
        assertEquals("Healthcare", fix.suggestion.name)
        assertEquals(setOf(1L, 2L), fix.transactionIds.toSet())
    }

    @Test
    fun importReviewIsPrefilledAndUndecidedPayeesComeFirst() {
        fun kotak(id: Long, payee: String, ref: String) = RawSms(
            id, "JD-KOTAKB-S",
            "Sent Rs.40.00 from Kotak Bank A/c X1111 to $payee on 30-08-26. UPI Ref $ref.",
            1_000_000L * id
        )
        val plan = SmsImportPlanner.plan(
            messages = listOf(kotak(1, "WELLCARE PHARMACY", "100000000101"), kotak(2, "ZENTRO", "100000000102"), kotak(3, "PRIYA NAIR", "100000000103")),
            alreadyImportedKeys = emptySet(),
            existingTransactions = emptyList(),
            rules = emptyList(),
            validCategories = all
        )
        assertEquals(listOf("Zentro", "Priya Nair", "Wellcare Pharmacy"), plan.groups.map { it.title })
        val pharmacy = plan.groups.last()
        assertEquals("Healthcare", pharmacy.suggestedCategory)
        assertEquals("pharmacy", pharmacy.autoCategory?.matched)
        assertFalse(pharmacy.hasRememberedChoice) // a guess never triggers automatic live capture
        assertTrue(plan.groups.first().needsCategory)
        assertTrue(plan.groups[1].isPerson)
    }
}
