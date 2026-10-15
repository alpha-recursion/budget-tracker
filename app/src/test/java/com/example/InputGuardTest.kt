package com.example

import com.example.domain.FormValidation
import com.example.domain.InputGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputGuardTest {

    @Test
    fun amountKeepsOnlyDigitsAndOneDecimalPoint() {
        assertEquals("1250", InputGuard.amount("1,250"))
        assertEquals("1250", InputGuard.amount("₹1,250"))
        assertEquals("99", InputGuard.amount("Rs. 99"))
        assertEquals("40", InputGuard.amount("INR 40"))
        assertEquals("15", InputGuard.amount("1e5"))
        assertEquals("12.5", InputGuard.amount("12.5"))
        assertEquals("12.5", InputGuard.amount("1a2.b5"))
        assertEquals("", InputGuard.amount("abc-"))
        assertEquals("5", InputGuard.amount("-5"))
    }

    @Test
    fun amountLimitsDecimalsAndLength() {
        assertEquals("1.23", InputGuard.amount("1.2345"))
        assertEquals("1.23", InputGuard.amount("1.2.3"))
        assertEquals("12345678", InputGuard.amount("1234567890"))
    }

    @Test
    fun amountHasNoLeadingZeros() {
        assertEquals("7", InputGuard.amount("007"))
        assertEquals("0", InputGuard.amount("000"))
        assertEquals("0.5", InputGuard.amount(".5"))
        assertEquals("0.05", InputGuard.amount("0.05"))
        assertEquals("", InputGuard.amount(""))
    }

    @Test
    fun amountFilterOutputPassesAmountValidation() {
        assertTrue(FormValidation.validateAmount(InputGuard.amount("₹ 1,250.50")).isValid)
    }

    @Test
    fun personNameAcceptsLettersOnly() {
        assertEquals("Rahul Kumar", InputGuard.personName("Rahul Kumar"))
        assertEquals("Rahul Kumar", InputGuard.personName("  Rahul2  Kumar9"))
        assertEquals("O'Brien-Smith Jr.", InputGuard.personName("O'Brien-Smith Jr."))
        assertEquals("Ravi", InputGuard.personName("Ravi😀@#"))
        assertEquals("राहुल शर्मा", InputGuard.personName("राहुल शर्मा"))
    }

    @Test
    fun personNameKeepsOneTrailingSpaceWhileTypingButCleanRemovesIt() {
        assertEquals("Rahul ", InputGuard.personName("Rahul "))
        assertEquals("Rahul Kumar", InputGuard.clean("  Rahul   Kumar  "))
    }

    @Test
    fun noLeadingSpacesOrDoubleSpaces() {
        assertEquals("Zepto Now", InputGuard.payee("   Zepto     Now"))
        assertEquals("a b", InputGuard.label("a \t\n b", 40))
    }

    @Test
    fun payeeAllowsDigitsAndPunctuationButNotEmojiOrSymbols() {
        assertEquals("7-Eleven & Co.", InputGuard.payee("7-Eleven & Co."))
        assertEquals("Disney+ Hotstar", InputGuard.payee("Disney+ Hotstar"))
        assertEquals("Chai", InputGuard.payee("Chai☕<>|^~"))
    }

    @Test
    fun lengthIsCappedAndEmojiAreNeverSplit() {
        assertEquals(60, InputGuard.payee("x".repeat(100)).length)
        assertEquals(30, InputGuard.label("y".repeat(100), InputGuard.MAX_CATEGORY).length)
        val capped = InputGuard.note("a".repeat(199) + "😀😀")
        assertEquals("a".repeat(199), capped)
    }

    @Test
    fun notesKeepEmojiButDropControlCharacters() {
        assertEquals("lunch 😀 with team", InputGuard.note("lunch 😀 with team"))
        assertEquals("ab", InputGuard.note("a\u0000b\u0007"))
        assertEquals("a\nb", InputGuard.note("a\nb", multiline = true))
        assertEquals("ab", InputGuard.note("a\nb"))
        assertEquals("hi", InputGuard.note("   hi"))
    }

    @Test
    fun smsKeepsLineBreaks() {
        assertEquals("Rs.40 paid\nRef 123", InputGuard.sms("Rs.40 paid\r\nRef 123"))
    }

    @Test
    fun invisibleCharactersAreRemoved() {
        assertEquals("Rahul", InputGuard.personName("Ra​hul"))
        assertEquals("abc", InputGuard.search("a‮b\u0000c"))
    }

    @Test
    fun namesNeedARealCharacter() {
        assertFalse(FormValidation.validateName("---", 40).isValid)
        assertFalse(FormValidation.validateName("   ", 40).isValid)
        assertFalse(FormValidation.validateName("x".repeat(41), 40).isValid)
        assertTrue(FormValidation.validateName("New phone", 40).isValid)
        assertFalse(FormValidation.validateTitle("...").isValid)
        assertTrue(FormValidation.validateTitle("7-Eleven").isValid)
        assertFalse(FormValidation.validatePerson("1234").isValid)
        assertTrue(FormValidation.validatePerson("Asha").isValid)
    }
}
