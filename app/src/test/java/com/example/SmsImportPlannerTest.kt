package com.example

import com.example.data.local.MerchantRuleEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.TransactionType
import com.example.domain.sms.RawSms
import com.example.domain.sms.SmsImportPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SmsImportPlannerTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val categories = setOf("Food & Dining", "Other", "Bike & Fuel")

    private fun at(day: Int, hour: Int): Long = Calendar.getInstance(ist).apply {
        clear()
        set(2026, Calendar.AUGUST, day, hour, 0, 0)
    }.timeInMillis

    private val boiFood = RawSms(
        id = 1,
        sender = "VM-BOIIND",
        body = "Rs.100.00 debited A/cXX5XXX and credited to SWARAJ POLI BHAJI KENDRA via UPI Ref No " +
            "659909850362 on 21Aug26. Call 18001031906, if not done by you. -BOI",
        receivedAt = at(21, 13)
    )
    private val kotakPerson = RawSms(
        id = 2,
        sender = "JD-KOTAKB-S",
        body = "Sent Rs.45.00 from Kotak Bank A/c XXXXX to YOGESH BBAN SUPEKAR on 27-08-26. " +
            "UPI Ref 512345678901. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud",
        receivedAt = at(27, 9)
    )

    private fun plan(
        messages: List<RawSms>,
        imported: Set<String> = emptySet(),
        existing: List<TransactionEntity> = emptyList(),
        rules: List<MerchantRuleEntity> = emptyList()
    ) = SmsImportPlanner.plan(messages, imported, existing, rules, categories, ist)

    @Test
    fun findsRealKotakAndBoiPayments() {
        val result = plan(listOf(boiFood, kotakPerson))
        assertEquals(2, result.candidateCount)
        // Newest first.
        assertEquals(listOf("Yogesh Bban Supekar", "Swaraj Poli Bhaji Kendra"), result.groups.map { it.title })
        val food = result.groups.last().items.single()
        assertEquals(100.0, food.parsed.amount, 0.001)
        assertEquals(boiFood.receivedAt, food.dateMs) // same day as the SMS states -> exact arrival time
        assertTrue(food.selectedByDefault)
    }

    @Test
    fun ignoresPhoneNumberSendersAndOtherBanks() {
        val forwarded = boiFood.copy(id = 3, sender = "+919876543210")
        val otherBank = RawSms(4, "AD-HDFCBK", "Rs.500.00 debited from A/c XX12 via UPI Ref No 999. -HDFC", at(22, 10))
        assertEquals(0, plan(listOf(forwarded, otherBank)).candidateCount)
    }

    @Test
    fun skipsAlreadyImportedAndRepeatedMessages() {
        val key = SmsImportPlanner.dedupeKey(boiFood, com.example.domain.sms.BankSmsParser.parse(boiFood.body, ist)!!)
        val result = plan(listOf(boiFood, boiFood.copy(id = 9, receivedAt = boiFood.receivedAt + 1000), kotakPerson), imported = setOf(key))
        assertEquals(1, result.alreadyImportedCount) // the same alert received twice counts once
        assertEquals(1, result.candidateCount)
    }

    @Test
    fun repeatedAlertInOneScanIsShownOnce() {
        val result = plan(listOf(kotakPerson, kotakPerson.copy(id = 10, receivedAt = kotakPerson.receivedAt + 5000)))
        assertEquals(1, result.candidateCount)
    }

    @Test
    fun messagesWithoutReferenceStillDedupeByFingerprint() {
        val masked = kotakPerson.copy(body = kotakPerson.body.replace("512345678901", "XXXXXXXXXXXX"))
        val parsed = com.example.domain.sms.BankSmsParser.parse(masked.body, ist)!!
        val key = SmsImportPlanner.dedupeKey(masked, parsed)
        assertTrue(key.startsWith("sms:"))
        assertEquals(key, SmsImportPlanner.dedupeKey(masked.copy(id = 99), parsed)) // provider id does not matter
        assertEquals(0, plan(listOf(masked), imported = setOf(key)).candidateCount)
    }

    @Test
    fun flagsLikelyManualDuplicates() {
        val manual = TransactionEntity(
            title = "Lunch", amount = 100.0, type = "EXPENSE", category = "Food & Dining", date = at(21, 20)
        )
        val item = plan(listOf(boiFood), existing = listOf(manual)).groups.single().items.single()
        assertTrue(item.possibleDuplicate)
        assertFalse(item.selectedByDefault)
    }

    @Test
    fun creditsNeedReviewAndAreNotPreselected() {
        val credit = RawSms(5, "VM-BOIIND", "Your A/c XX5XXX is credited with Rs.2,000.00 by UPI Ref No 700000000001. -BOI", at(25, 18))
        val item = plan(listOf(credit)).groups.single().items.single()
        assertEquals(TransactionType.INCOME, item.parsed.type)
        assertTrue(item.needsReview)
        assertFalse(item.selectedByDefault)
        assertEquals("Money received (BOI)", item.title)
    }

    @Test
    fun matchingDebitAndCreditAreSuggestedAsSelfTransfer() {
        val debit = kotakPerson.copy(body = kotakPerson.body.replace("YOGESH BBAN SUPEKAR", "OM PATIL"))
        val credit = RawSms(6, "VM-BOIIND", "Your A/c XX5XXX is credited with Rs.45.00 by UPI Ref No 512345678901. -BOI", at(27, 9))
        val groups = plan(listOf(debit, credit)).groups
        assertEquals(2, groups.size) // both sides are kept (different bank + direction)
        assertTrue(groups.all { it.suggestedSelfTransfer })
    }

    @Test
    fun rememberedChoicesArePrefilledPerDirection() {
        val rules = listOf(
            MerchantRuleEntity("expense:swaraj poli bhaji kendra", "Food & Dining", false),
            MerchantRuleEntity("expense:yogesh bban supekar", "Deleted Category", false)
        )
        val groups = plan(listOf(boiFood, kotakPerson), rules = rules).groups.associateBy { it.title }
        assertEquals("Food & Dining", groups.getValue("Swaraj Poli Bhaji Kendra").suggestedCategory)
        // A rule pointing at a category that no longer exists falls back to Other.
        assertEquals("Other", groups.getValue("Yogesh Bban Supekar").suggestedCategory)
        assertEquals("expense:swaraj poli bhaji kendra", groups.getValue("Swaraj Poli Bhaji Kendra").ruleKey)
    }

    @Test
    fun paymentsToSamePayeeAreGrouped() {
        val again = boiFood.copy(
            id = 7,
            body = boiFood.body.replace("659909850362", "659909850999").replace("21Aug26", "22Aug26"),
            receivedAt = at(22, 13)
        )
        val group = plan(listOf(boiFood, again)).groups.single()
        assertEquals(2, group.items.size)
    }
}
