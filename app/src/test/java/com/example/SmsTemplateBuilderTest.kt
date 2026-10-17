package com.example

import com.example.data.model.TransactionType
import com.example.domain.sms.BankSmsParser
import com.example.domain.sms.RawSms
import com.example.domain.sms.SmsBank
import com.example.domain.sms.SmsDateFormat
import com.example.domain.sms.SmsImportPlanner
import com.example.domain.sms.SmsTemplate
import com.example.domain.sms.SmsTemplateBuilder
import com.example.domain.sms.TemplateBuildResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SmsTemplateBuilderTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")

    private val zepto = "Sent Rs.453.00 from Kotak Bank A/c X6789 to Zepto on 31-08-26. UPI Ref 900000000008. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
    private val panshop = "Sent Rs.30.00 from Kotak Bank A/c X6789 to CORNER PANSHOP on 31-08-26. UPI Ref 900000000009. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
    private val devTea = "Sent Rs.53.00 from Kotak Bank A/c X6789 to MORNING TEA on 29-08-26. UPI Ref 900000000010. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
    private val boi = "Rs.250.00 debited A/cXX2468 and credited to NEW ASHA KHANAWAL via UPI Ref No 900000000004 on 13Aug26. Call 18001031906, if not done by you. -BOI"

    private fun day(y: Int, m: Int, d: Int) = Calendar.getInstance(ist).apply { clear(); set(y, m - 1, d) }.timeInMillis

    /** Teach from [sample] accepting every suggestion, as a user tapping "Save" would. */
    private fun teach(sample: String): SmsTemplate {
        val s = SmsTemplateBuilder.suggest(sample)
        val result = SmsTemplateBuilder.build(
            text = s.text,
            amount = s.amounts.first(),
            payee = s.payee,
            date = s.dates.firstOrNull(),
            ref = s.refs.firstOrNull(),
            type = s.type,
            bank = s.bank,
            timeZone = ist
        )
        assertTrue("build failed: $result", result is TemplateBuildResult.Success)
        return (result as TemplateBuildResult.Success).template
    }

    @Test
    fun suggestionsForKotakSample() {
        val s = SmsTemplateBuilder.suggest(zepto)
        assertEquals("453.00", s.amounts.single().value)
        assertEquals("Zepto", s.payee?.value) // not "Kotak Bank A/c X6789 to Zepto"
        assertEquals("31-08-26", s.dates.single().span.value)
        assertEquals(SmsDateFormat.DAY_MONTH_YEAR_NUMERIC, s.dates.single().format)
        assertEquals("900000000008", s.refs.single().value)
        assertEquals(TransactionType.EXPENSE, s.type)
        assertEquals(SmsBank.KOTAK, s.bank)
    }

    @Test
    fun suggestionsForBoiSample() {
        val s = SmsTemplateBuilder.suggest(boi)
        assertEquals("250.00", s.amounts.single().value)
        assertEquals("NEW ASHA KHANAWAL", s.payee?.value)
        assertEquals("13Aug26", s.dates.single().span.value)
        assertEquals(SmsDateFormat.DAY_MONTH_NAME_YEAR, s.dates.single().format)
        assertEquals("900000000004", s.refs.single().value)
        assertEquals(TransactionType.EXPENSE, s.type) // "debited" comes before "credited to"
        assertEquals(SmsBank.BOI, s.bank)
    }

    @Test
    fun formatTaughtFromOneMessageReadsTheOthers() {
        val template = teach(zepto)

        val p = template.match(BankSmsParser.normalize(panshop), ist)
        assertNotNull(p)
        assertEquals(30.0, p!!.amount, 0.001)
        assertEquals("Corner Panshop", p.counterparty)
        assertEquals(day(2026, 8, 31), p.dayMs)
        assertEquals("900000000009", p.upiRef)

        val d = template.match(BankSmsParser.normalize(devTea), ist)
        assertEquals(53.0, d!!.amount, 0.001)
        assertEquals("Morning Tea", d.counterparty)
        assertEquals(day(2026, 8, 29), d.dayMs)

        // A different bank's format is not matched.
        assertNull(template.match(BankSmsParser.normalize(boi), ist))
    }

    @Test
    fun boiFormatCanBeTaughtToo() {
        val template = teach(boi)
        val other = boi.replace("250.00", "1,200.50").replace("NEW ASHA KHANAWAL", "ANNAPURNA POLI BHAJI KENDRA")
            .replace("900000000004", "900000000001").replace("13Aug26", "21Aug26").replace("XX2468", "XX1234")
        val p = template.match(BankSmsParser.normalize(other), ist)!!
        assertEquals(1200.5, p.amount, 0.001)
        assertEquals("Annapurna Poli Bhaji Kendra", p.counterparty)
        assertEquals(day(2026, 8, 21), p.dayMs)
    }

    @Test
    fun newFormatIsRecognisedByTheScanOnceTaught() {
        val unknown = "Rs.120.00 spent at CAFE COFFEE DAY on 30-08-26 via Kotak. Not you? Call 1800"
        val sms = RawSms(1, "AX-KOTAKB", unknown.replace("120.00", "99.00").replace("CAFE COFFEE DAY", "DMART"), day(2026, 8, 30))
        val before = SmsImportPlanner.plan(listOf(sms), emptySet(), emptyList(), emptyList(), setOf("Other"), ist)
        assertEquals(0, before.candidateCount)

        val template = teach(unknown)
        val after = SmsImportPlanner.plan(listOf(sms), emptySet(), emptyList(), emptyList(), setOf("Other"), ist, listOf(template))
        val item = after.groups.single().items.single()
        assertEquals(99.0, item.parsed.amount, 0.001)
        assertEquals("Dmart", item.parsed.counterparty)
        assertTrue(item.parsed.fromTemplate)
    }

    @Test
    fun refusesUnsafeOrAmbiguousFormats() {
        val otp = SmsTemplateBuilder.suggest("123456 is your OTP for Rs.500.00 payment to AMAZON. Do not share.")
        assertTrue(
            SmsTemplateBuilder.build(otp.text, otp.amounts.first(), null, null, null, TransactionType.EXPENSE, SmsBank.KOTAK, ist)
                is TemplateBuildResult.Failure
        )

        // Payee at the very end has nothing after it to show where it stops.
        val text = "Paid Rs.40.00 to TEA STALL"
        val s = SmsTemplateBuilder.suggest(text)
        val payee = SmsTemplateBuilder.locate(s.text, "TEA STALL", s.amounts)
        assertTrue(
            SmsTemplateBuilder.build(s.text, s.amounts.first(), payee, null, null, TransactionType.EXPENSE, SmsBank.KOTAK, ist)
                is TemplateBuildResult.Failure
        )

        // Almost no fixed words: would match far too much.
        val bare = SmsTemplateBuilder.suggest("Rs.40.00")
        assertTrue(
            SmsTemplateBuilder.build(bare.text, bare.amounts.first(), null, null, null, TransactionType.EXPENSE, SmsBank.KOTAK, ist)
                is TemplateBuildResult.Failure
        )
    }

    @Test
    fun typedPayeeMustAppearInTheSms() {
        val s = SmsTemplateBuilder.suggest(zepto)
        assertNull(SmsTemplateBuilder.locate(s.text, "Swiggy", s.amounts))
        assertEquals("Zepto", SmsTemplateBuilder.locate(s.text, "zepto", s.amounts)?.value)
    }
}
