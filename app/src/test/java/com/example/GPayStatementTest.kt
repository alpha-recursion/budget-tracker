package com.example

import com.example.data.model.TransactionType
import com.example.domain.sms.SmsBank
import com.example.domain.sms.SmsImportPlanner
import com.example.domain.sms.toTransaction
import com.example.domain.statement.GPayStatementParser
import com.example.domain.statement.StatementFormatException
import com.example.domain.statement.StatementRowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Made-up rows in the layout of a real Google Pay statement (no real names or numbers). */
class GPayStatementTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")

    private data class Row(val date: String, val time: String, val what: String, val ref: String, val account: String, val amount: String)

    private val rows = listOf(
        Row("01 Sep, 2026", "10:00 AM", "Paid to TEST CAFE", "100000000001", "Paid by Kotak Mahindra Bank 1111", "₹168"),
        Row("02 Sep, 2026", "07:15 PM", "Paid to Sample Person", "100000000002", "Paid by Kotak Mahindra Bank 1111", "₹1,000"),
        Row("10 Sep, 2026", "11:47 AM", "Self transfer to Bank of India 2222", "100000000003", "Paid by Kotak Mahindra Bank 1111", "₹5,000"),
        Row("16 Sep, 2026", "10:11 PM", "Received from Another Person", "100000000004", "Paid to Kotak Mahindra Bank 3333", "₹490.07"),
        Row("28 Sep, 2026", "12:00 AM", "Paid to PHARMACY 24", "100000000005", "Paid by Bank of India 2222", "₹224.64")
    )

    private val pageHeader = listOf(
        "Transaction statement",
        "9999999999, someone@example.com",
        "Note: This statement reflects payments made by you on the Google Pay app. Self transfer payments are not included in the total money paid and",
        "received. Any payments transactions and activity deleted from your Google Account will not show up in this statement."
    )

    /** One field per line, as the PDF stores them. */
    private fun streamText(rows: List<Row>): String = (
        pageHeader + listOf(
            "Page 1 of 2", "Transaction statement period", "01 September 2026 - 30 September 2026",
            "Sent", "₹1,392.64", "Received", "₹490.07", "Date & time Transaction details Amount"
        ) + rows.take(2).flatMap { listOf(it.date, it.time, it.what, "UPI Transaction ID: ${it.ref}", it.account, it.amount) } +
            pageHeader + listOf("Page 2 of 2", "Date & time Transaction details Amount") +
            rows.drop(2).flatMap { listOf(it.date, it.time, it.what, "UPI Transaction ID: ${it.ref}", it.account, it.amount) }
        ).joinToString("\n")

    /** Sorted by position: date, name and amount share a line. */
    private fun positionText(rows: List<Row>): String = (
        listOf(
            "Transaction statement", "9999999999, someone@example.com",
            "Transaction statement period Sent Received",
            "01 September 2026 - 30 September 2026 ₹1,392.64 ₹490.07",
            "Date & time Transaction details Amount"
        ) + rows.flatMap {
            listOf("${it.date} ${it.what} ${it.amount}", "${it.time} UPI Transaction ID: ${it.ref}", it.account)
        } + pageHeader.drop(2) + "Page 1 of 1"
        ).joinToString("\n")

    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance(ist).apply {
        clear()
        set(2026, Calendar.SEPTEMBER, day, hour, minute, 0)
    }.timeInMillis

    @Test
    fun readsEveryRowInBothTextLayouts() {
        for (text in listOf(streamText(rows), positionText(rows))) {
            val statement = GPayStatementParser.parse(text, ist)
            assertEquals(5, statement.rows.size)
            val (cafe, person, transfer, received, pharmacy) = statement.rows

            assertEquals(StatementRowKind.PAID, cafe.kind)
            assertEquals("TEST CAFE", cafe.counterparty)
            assertEquals("100000000001", cafe.upiRef)
            assertEquals(168.0, cafe.amount, 0.001)
            assertEquals(at(1, 10, 0), cafe.timeMs)
            assertEquals(SmsBank.KOTAK, cafe.bank)
            assertEquals("1111", cafe.accountLast4)

            assertEquals(1000.0, person.amount, 0.001)
            assertEquals(at(2, 19, 15), person.timeMs)

            assertEquals(StatementRowKind.SELF_TRANSFER, transfer.kind)
            assertEquals(SmsBank.KOTAK, transfer.bank)
            assertEquals(SmsBank.BOI, transfer.targetBank)

            assertEquals(StatementRowKind.RECEIVED, received.kind)
            assertEquals("Another Person", received.counterparty)
            assertEquals("3333", received.accountLast4) // "Paid to <bank>" here is the account, not a payee

            // A payee ending in digits is still a payee; 12:00 AM is midnight.
            assertEquals("PHARMACY 24", pharmacy.counterparty)
            assertEquals(SmsBank.BOI, pharmacy.bank)
            assertEquals(at(28, 0, 0), pharmacy.timeMs)
            assertEquals(224.64, pharmacy.amount, 0.001)

            assertEquals("01 September 2026 - 30 September 2026", statement.period)
            assertEquals(1392.64, statement.sent!!, 0.001)
            assertEquals(490.07, statement.received!!, 0.001)
            assertTrue("self transfers are not part of the printed totals", statement.totalsMatch)
        }
    }

    @Test
    fun toleratesSpacesLostOrAddedInExtraction() {
        // Seen in real extraction: "11Sep, 2026", "Bank ofIndia2222", runs of spaces between words.
        val text = positionText(rows)
            .replace(Regex("""(\d{2}) Sep"""), "$1Sep")
            .replace("Bank of India 2222", "Bank ofIndia2222")
            .replace("Paid to", "Paid  to")
            .replace("UPI Transaction ID", "UPI  Transaction ID")
        val statement = GPayStatementParser.parse(text, ist)
        assertEquals(5, statement.rows.size)
        val transfer = statement.rows[2]
        assertEquals(SmsBank.BOI, transfer.targetBank)
        assertEquals(SmsBank.BOI, statement.rows[4].bank)
        assertEquals("2222", statement.rows[4].accountLast4)
        assertEquals("TEST CAFE", statement.rows[0].counterparty)
        assertTrue(statement.totalsMatch)
    }

    @Test
    fun rejectsFilesItCannotReadReliably() {
        // A row lost its amount: refuse rather than shift every amount onto the wrong row.
        val broken = streamText(rows).replaceFirst("\n₹1,000\n", "\n")
        assertThrows(StatementFormatException::class.java) { GPayStatementParser.parse(broken, ist) }
        assertThrows(StatementFormatException::class.java) { GPayStatementParser.parse("Hello, this is not a statement", ist) }
        assertFalse(GPayStatementParser.looksLikeStatement("Your OTP is 123456"))
    }

    @Test
    fun totalsMismatchIsFlagged() {
        val statement = GPayStatementParser.parse(streamText(rows).replace("₹1,392.64", "₹2,000"), ist)
        assertFalse(statement.totalsMatch)
    }

    @Test
    fun planSkipsSmsImportsAndMarksSelfTransfers() {
        val statement = GPayStatementParser.parse(streamText(rows), ist)
        val plan = SmsImportPlanner.planStatement(
            statement = statement,
            alreadyImportedKeys = setOf("ref:KOTAK:EXPENSE:100000000001"), // the cafe came in by SMS
            existingTransactions = emptyList(),
            rules = emptyList(),
            validCategories = setOf("Other"),
            timeZone = ist
        )
        assertEquals(1, plan.alreadyImportedCount)
        assertEquals(4, plan.candidateCount)
        val items = plan.groups.flatMap { it.items }
        assertTrue(items.none { it.parsed.upiRef == "100000000001" })

        val transferGroup = plan.groups.single { g -> g.items.any { it.parsed.upiRef == "100000000003" } }
        assertTrue(transferGroup.suggestedSelfTransfer)
        assertEquals(listOf("ref:BOI:INCOME:100000000003"), transferGroup.items.single().extraKeys)

        val received = items.single { it.parsed.upiRef == "100000000004" }
        assertEquals(TransactionType.INCOME, received.parsed.type)
        assertEquals("ref:KOTAK:INCOME:100000000004", received.dedupeKey)
        assertTrue(received.selectedByDefault)
        assertEquals(at(16, 22, 11), received.dateMs)
        assertTrue(plan.statement!!.totalsMatch)
        assertEquals(1, plan.statement!!.selfTransfers)
        assertEquals("From Google Pay statement (Kotak) · UPI Ref 100000000004", received.toTransaction("Other").note)
    }
}
