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
        assertEquals("Healthcare", category("Lifeline Pharmacy"))
        assertEquals("Healthcare", category("City Multispeciality Hospital"))
        assertEquals("Tea & Snacks", category("Rk pan shop"))
        assertEquals("Tea & Snacks", category("PAILWAN PANSHOP"))
        assertEquals("Tea & Snacks", category("Corner Tea Stall"))
        assertEquals("Food & Dining", category("Hotel Sagar"))
        assertEquals("Food & Dining", category("Zomato"))
        assertEquals("Groceries", category("Balaji Super Market"))
        assertEquals("Groceries", category("ZEPTO MARKETPLACE PRIVATE LIMITED"))
        assertEquals("Fuel & Vehicle", category("KOTHRUD PETROLCIRCLE"))
        assertEquals("Mobile & Recharge", category("Jio Prepaid"))
        assertEquals("Transportation", category("ONE PUNE NCMC CARD"))
        // A medical store is healthcare, not groceries.
        assertEquals("Healthcare", category("Sai Medical and General Stores"))
    }

    @Test
    fun matchedWordIsShownWhole() {
        val s = suggest("Mai Mangeshkar PharmacyIpd") as AutoSuggestion.Category
        assertEquals(GuessSource.KEYWORD, s.source)
        assertEquals("pharmacyipd", s.matched)
    }

    @Test
    fun fallsBackToABroaderCategoryWhenTheFinerOneIsGone() {
        val withoutNew = all - setOf("Tea & Snacks", "Groceries", "Fuel & Vehicle", "Mobile & Recharge")
        assertEquals("Utilities & Bills", category("Airtel Prepaid", withoutNew))
        assertEquals("Food & Dining", category("Shree Ram Tea Stall", withoutNew))
        assertEquals("Shopping", category("Daily Mart", withoutNew))
    }

    @Test
    fun peopleAreMarkedNotGuessed() {
        assertEquals(AutoSuggestion.Person, suggest("Mr Satish Gawade"))
        assertEquals(AutoSuggestion.Person, suggest("Sanika Patil"))
        assertFalse(AutoCategorizer.looksLikePerson("Ravi Enterprises"))
        assertFalse(AutoCategorizer.looksLikePerson("Lucky One Wines"))
        assertFalse(AutoCategorizer.looksLikePerson("AWFIS")) // one word: could be a brand
        assertFalse(AutoCategorizer.looksLikePerson("Khandeshi MH 18"))
    }

    @Test
    fun learnsFromTheOwnersOwnChoices() {
        val model = CategoryModel.learn(
            listOf(
                tx("Shri Swami Samarth T Stola", "Tea & Snacks", 1),
                tx("Ganesh Enterprises", "Groceries", 2),
                tx("Ganesh Tailoring Works", "Shopping", 3),
                tx("Sanika Patil", "Shopping", 4), // a person: not learned from
                tx("Unknown Payee", "Other", 5) // Other teaches nothing
            )
        )
        val s = suggest("Samarth Stola Corner", model = model) as AutoSuggestion.Category
        assertEquals("Tea & Snacks", s.name)
        assertEquals(GuessSource.LEARNED, s.source)
        assertEquals("samarth stola", s.matched)
        // "ganesh" points two ways: no guess.
        assertNull(suggest("Ganesh Store Point", model = model))
        // Learning never overrides a person.
        assertEquals(AutoSuggestion.Person, suggest("Sanika Kulkarni", model = model))
    }

    @Test
    fun sortOutOtherSuggestsPerPayee() {
        val txs = listOf(
            tx("Lifeline Pharmacy", "Other", 1),
            tx("LIFELINE PHARMACY", "Other", 2),
            tx("Sanika Patil", "Other", 3),
            tx("Hotel Sagar", "Food & Dining", 4)
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
            messages = listOf(kotak(1, "LIFELINE PHARMACY", "100000000101"), kotak(2, "AWFIS", "100000000102"), kotak(3, "SANIKA PATIL", "100000000103")),
            alreadyImportedKeys = emptySet(),
            existingTransactions = emptyList(),
            rules = emptyList(),
            validCategories = all
        )
        assertEquals(listOf("Awfis", "Sanika Patil", "Lifeline Pharmacy"), plan.groups.map { it.title })
        val pharmacy = plan.groups.last()
        assertEquals("Healthcare", pharmacy.suggestedCategory)
        assertEquals("pharmacy", pharmacy.autoCategory?.matched)
        assertFalse(pharmacy.hasRememberedChoice) // a guess never triggers automatic live capture
        assertTrue(plan.groups.first().needsCategory)
        assertTrue(plan.groups[1].isPerson)
    }
}
