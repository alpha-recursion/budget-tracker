package com.example

import com.example.data.local.MerchantRuleEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.TransactionType
import com.example.domain.sms.AutoCapture
import com.example.domain.sms.CaptureState
import com.example.domain.sms.RawSms
import com.example.domain.sms.SmsImportPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AutoCaptureTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val categories = setOf("Food & Dining", "Other")

    private fun at(day: Int, hour: Int): Long = Calendar.getInstance(ist).apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, 0, 0)
    }.timeInMillis

    private val zepto = RawSms(
        id = 0,
        sender = "JD-KOTAKB-S",
        body = "Sent Rs.453.00 from Kotak Bank A/c X0271 to Zepto on 10-10-26. UPI Ref 528311470110. " +
            "Not you, https://kotak.com/KBANKT/Fraud",
        receivedAt = at(10, 11)
    )
    private val zeptoRule = MerchantRuleEntity("expense:zepto", "Food & Dining", isSelfTransfer = false)

    private fun decide(
        sms: RawSms,
        rules: List<MerchantRuleEntity> = listOf(zeptoRule),
        existing: List<TransactionEntity> = emptyList(),
        valid: Set<String> = categories
    ) = AutoCapture.decide(SmsImportPlanner.plan(listOf(sms), emptySet(), existing, rules, valid, ist))

    @Test
    fun knownPayeeIsAddedAutomatically() {
        val decision = decide(zepto)
        val added = decision.autoAdd.single()
        assertEquals("Food & Dining", added.category)
        assertFalse(added.isSelfTransfer)
        assertTrue(decision.toReview.isEmpty())
    }

    @Test
    fun newPayeeWaitsForReview() {
        val decision = decide(zepto, rules = emptyList())
        assertTrue(decision.autoAdd.isEmpty())
        assertEquals(1, decision.toReview.size)
    }

    @Test
    fun deletedCategoryIsNotARememberedChoice() {
        val decision = decide(zepto, valid = setOf("Other"))
        assertTrue(decision.autoAdd.isEmpty())
    }

    @Test
    fun possibleDuplicateOfHandEntryWaitsForReview() {
        val manual = TransactionEntity(title = "Zepto", amount = 453.0, type = "EXPENSE", category = "Food & Dining", date = at(10, 9))
        assertTrue(decide(zepto, existing = listOf(manual)).autoAdd.isEmpty())
    }

    @Test
    fun withoutUpiReferenceWaitsForReview() {
        // The fallback dedupe key includes the arrival time, which the inbox may record
        // differently from the broadcast: auto-adding could lead to a double import later.
        val noRef = zepto.copy(body = zepto.body.replace(" UPI Ref 528311470110.", ""))
        val decision = decide(noRef)
        assertTrue(decision.autoAdd.isEmpty())
        assertEquals(1, decision.toReview.size)
    }

    @Test
    fun unverifiedCreditsWaitForReview() {
        val credit = RawSms(0, "VM-BOIIND", "Your A/c XX5XXX is credited with Rs.2,000.00 by UPI Ref No 700000000001. -BOI", at(10, 12))
        val rule = MerchantRuleEntity("income:money received (boi)", "Other", false)
        assertTrue(decide(credit, rules = listOf(rule)).autoAdd.isEmpty())
    }

    @Test
    fun verifiedCreditFromRememberedPayerIsAdded() {
        val credit = RawSms(0, "JD-KOTAKB-S", "Received Rs.57000.00 in your Kotak Bank AC 0271 from OM MADHUKAR PATIL on 10-10-26.UPI Ref:623396914412", at(10, 12))
        val rule = MerchantRuleEntity("income:om madhukar patil", "Other", isSelfTransfer = true)
        val decision = decide(credit, rules = listOf(rule))
        assertTrue(decision.autoAdd.single().isSelfTransfer)
        assertTrue(decide(credit, rules = emptyList()).autoAdd.isEmpty())
    }

    @Test
    fun reversalOfRecordedPaymentCancelsItSilently() {
        val reversal = RawSms(0, "JD-KOTAKB-S", "Rs.453.00 is credited to Kotak Bank a/c no. 0271 on 10-10-26 as a reversal of debit transaction (UPI Ref no 528311470110).", at(10, 12))
        val decision = AutoCapture.decide(
            SmsImportPlanner.plan(listOf(reversal), setOf("ref:KOTAK:EXPENSE:528311470110"), emptyList(), emptyList(), categories, ist)
        )
        assertEquals("ref:KOTAK:EXPENSE:528311470110", decision.autoAdd.single().candidate.cancelsKey)
        val state = CaptureState().record(decision, reversal.receivedAt, reversal.receivedAt - AutoCapture.SCAN_MARGIN_MS)
        assertEquals(0, state.addedCount)
    }

    @Test
    fun rememberedSelfTransferIsRecordedSilently() {
        val rule = MerchantRuleEntity("expense:zepto", "Other", isSelfTransfer = true)
        val decision = decide(zepto, rules = listOf(rule))
        assertTrue(decision.autoAdd.single().isSelfTransfer)
        val state = CaptureState().record(decision, zepto.receivedAt, zepto.receivedAt - AutoCapture.SCAN_MARGIN_MS)
        assertFalse(state.hasNews)
    }

    @Test
    fun stateCountsAddedAndPendingAndRepeatedSmsOnce() {
        val added = decide(zepto)
        val pending = decide(zepto, rules = emptyList())
        val from = zepto.receivedAt - AutoCapture.SCAN_MARGIN_MS
        val state = CaptureState()
            .record(added, zepto.receivedAt, from)
            .record(pending, zepto.receivedAt, from)
            .record(pending, zepto.receivedAt + 1000, from + 1000) // same alert delivered twice
        assertEquals(1, state.addedCount)
        assertEquals(453.0, state.addedTotal, 0.001)
        assertEquals(1, state.pendingCount)
        assertEquals(from, state.pendingSinceMs)
        assertEquals(zepto.receivedAt, state.lastPendingAt)
        assertNull(state.latest!!.addedAs)
    }

    @Test
    fun reviewClearsPendingOnlyWhenItCoveredThem() {
        val from = zepto.receivedAt - AutoCapture.SCAN_MARGIN_MS
        val state = CaptureState().record(decide(zepto, rules = emptyList()), zepto.receivedAt, from)
        // A scan that started after the waiting SMS does not show it.
        assertEquals(1, state.afterReview(scanFromMs = from + 1, scannedAtMs = zepto.receivedAt + 1).pendingCount)
        // A scan that ran before the SMS arrived cannot have shown it either.
        assertEquals(1, state.afterReview(scanFromMs = from, scannedAtMs = zepto.receivedAt - 1).pendingCount)
        val cleared = state.afterReview(scanFromMs = from - 1, scannedAtMs = zepto.receivedAt + 1)
        assertEquals(0, cleared.pendingCount)
        assertNull(cleared.pendingSinceMs)
        assertFalse(cleared.hasNews)
    }

    @Test
    fun openingTheAppResetsAddedButKeepsPending() {
        val from = zepto.receivedAt - AutoCapture.SCAN_MARGIN_MS
        val state = CaptureState()
            .record(decide(zepto), zepto.receivedAt, from)
            .record(decide(zepto, rules = emptyList()), zepto.receivedAt, from)
            .seen()
        assertEquals(0, state.addedCount)
        assertEquals(1, state.pendingCount)
        assertTrue(state.hasNews)
        assertFalse(CaptureState().record(decide(zepto), zepto.receivedAt, from).seen().hasNews)
    }
}
