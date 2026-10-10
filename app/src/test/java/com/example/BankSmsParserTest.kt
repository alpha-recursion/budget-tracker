package com.example

import com.example.data.model.TransactionType
import com.example.domain.sms.BankSmsParser
import com.example.domain.sms.SmsBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BankSmsParserTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")

    private fun day(year: Int, month: Int, dayOfMonth: Int): Long = Calendar.getInstance(ist).apply {
        clear()
        set(year, month - 1, dayOfMonth)
    }.timeInMillis

    // Real messages from the owner's phone (account numbers / refs masked by the owner).
    private val kotakSent = "Sent Rs.45.00 from Kotak Bank A/c XXXXX to YOGESH BBAN SUPEKAR on 27-08-26. " +
        "UPI Ref XXXXXXXXXXXXXXXXXXX . Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
    private val boiDebit = "Rs.100.00 debited A/cXX5XXX and credited to SWARAJ POLI BHAJI KENDRA via UPI Ref No " +
        "659909850362 on 21Aug26. Call 18001031906, if not done by you. -BOI"

    @Test
    fun parsesKotakUpiPayment() {
        val parsed = BankSmsParser.parse(kotakSent, ist)
        assertNotNull(parsed)
        parsed!!
        assertEquals(45.0, parsed.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("Yogesh Bban Supekar", parsed.counterparty)
        assertEquals(day(2026, 8, 27), parsed.dayMs)
        assertNull("masked ref must not be used for de-duplication", parsed.upiRef)
        assertEquals(SmsBank.KOTAK, parsed.bank)
        assertTrue(parsed.isConfident)
    }

    @Test
    fun parsesBoiUpiPayment() {
        val parsed = BankSmsParser.parse(boiDebit, ist)
        assertNotNull(parsed)
        parsed!!
        assertEquals(100.0, parsed.amount, 0.001)
        // Contains both "debited" and "credited to": it is money leaving the user's account.
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("Swaraj Poli Bhaji Kendra", parsed.counterparty)
        assertEquals(day(2026, 8, 21), parsed.dayMs)
        assertEquals("659909850362", parsed.upiRef)
        assertEquals(SmsBank.BOI, parsed.bank)
        assertTrue(parsed.isConfident)
    }

    @Test
    fun parsesLargeAmountsWithCommas() {
        val parsed = BankSmsParser.parse(boiDebit.replace("Rs.100.00", "Rs.1,25,000.50"), ist)
        assertEquals(125000.5, parsed!!.amount, 0.001)
    }

    @Test
    fun genericFallbackHandlesCreditsButIsMarkedForReview() {
        val parsed = BankSmsParser.parse(
            "Your A/c XX1234 is credited with INR 2,000.00 by UPI Ref No 512345678901. -Some Bank",
            ist
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals(2000.0, parsed.amount, 0.001)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("512345678901", parsed.upiRef)
        assertFalse(parsed.isConfident)
    }

    @Test
    fun otherBanksNamedBankOfIndiaAreNotBoi() {
        val sbi = BankSmsParser.parse("Rs.250.00 debited from your A/c XX12 by UPI. -State Bank of India", ist)
        assertEquals(SmsBank.UNKNOWN, sbi!!.bank)
        val central = BankSmsParser.parse("Rs.250.00 debited from your A/c XX12 by UPI. Central Bank of India", ist)
        assertEquals(SmsBank.UNKNOWN, central!!.bank)
        val boi = BankSmsParser.parse("Rs.250.00 debited from your A/c XX12 by UPI. Bank of India", ist)
        assertEquals(SmsBank.BOI, boi!!.bank)
    }

    @Test
    fun ignoresMessagesThatDidNotMoveMoney() {
        assertNull(BankSmsParser.parse("123456 is your OTP for UPI transaction of Rs.500.00. Do not share.", ist))
        assertNull(BankSmsParser.parse("Rahul has requested Rs.200.00 from you on UPI. Pay or decline.", ist))
        assertNull(BankSmsParser.parse("UPI txn of Rs.300.00 from A/c XX12 failed. Amount will be refunded.", ist))
        assertNull(BankSmsParser.parse("Get a personal loan up to Rs.5,00,000 instantly!", ist))
    }
}
