package com.example.domain.statement

import com.example.domain.sms.SmsBank
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class StatementRowKind { PAID, RECEIVED, SELF_TRANSFER }

/**
 * One row of a Google Pay statement.
 *
 * @property counterparty who was paid / who paid; for a self transfer, the account it went to.
 * @property bank the owner's account the money left (paid, self transfer) or arrived in (received).
 * @property targetBank for a self transfer, the owner's account that received it.
 */
data class StatementRow(
    val kind: StatementRowKind,
    val counterparty: String,
    val upiRef: String,
    val timeMs: Long,
    val amount: Double,
    val bank: SmsBank,
    val accountLast4: String?,
    val targetBank: SmsBank? = null
)

data class GPayStatement(
    val rows: List<StatementRow>,
    /** "01 September 2026 - 30 September 2026" as printed, if found. */
    val period: String?,
    /** The statement's own totals (self transfers excluded), to check what was read. */
    val sent: Double?,
    val received: Double?
) {
    val paidTotal: Double get() = rows.filter { it.kind == StatementRowKind.PAID }.sumOf { it.amount }
    val receivedTotal: Double get() = rows.filter { it.kind == StatementRowKind.RECEIVED }.sumOf { it.amount }

    /** True when the rows add up to the totals printed on the statement (or none were printed). */
    val totalsMatch: Boolean
        get() = (sent == null || kotlin.math.abs(sent - paidTotal) < 0.01) &&
            (received == null || kotlin.math.abs(received - receivedTotal) < 0.01)
}

/** Why a file could not be read; [message] is shown to the owner. */
class StatementFormatException(message: String) : Exception(message)

/**
 * Reads the text of a Google Pay "Transaction statement" PDF. Each row has a date, a time,
 * "Paid to / Received from / Self transfer to" + name, "UPI Transaction ID: …", the account
 * ("Paid by / Paid to <bank> <last 4>") and an amount.
 *
 * PDF text can come out in different orders (column by column, or line by line with the date,
 * name and amount on one line), so fields are not read positionally. Every field of a kind is
 * collected in document order and the n-th date, time, name, reference, account and amount are
 * paired up; if their counts differ the file is rejected rather than guessed at.
 */
object GPayStatementParser {

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    // Spaces between fixed words are optional: text extraction sometimes drops them ("11Sep, 2026").
    private val DATE = Regex("""\b(\d{1,2}) ?(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec), ?(\d{4})\b""")
    private val TIME = Regex("""\b(\d{1,2}):(\d{2}) ?([AP]M)\b""", RegexOption.IGNORE_CASE)
    private val UPI = Regex("""UPI ?Transaction ?ID:?\s*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("""₹\s?([\d,]+(?:\.\d{1,2})?)""")
    // A name runs to the amount, the reference or the end of the line.
    private val PARTY = Regex("""\b(Paid ?to|Paid ?by|Received ?from|Self ?transfer ?to)\s*(.+?)\s*(?=₹|UPI ?Transaction ?ID|$)""")
    private val ACCOUNT_VALUE = Regex("""^(.*?\D)\s*(\d{4})$""")
    // Includes no-break spaces, which some PDFs use between words.
    private val WHITESPACE = Regex("""[\s ]+""")
    private val PERIOD =Regex("""\b(\d{1,2} [A-Z][a-z]+ \d{4}) - (\d{1,2} [A-Z][a-z]+ \d{4})\b""")

    private enum class Kind { DATE, TIME, UPI, AMOUNT, PARTY }
    private class Token(val kind: Kind, val match: MatchResult)

    /** Cheap check before parsing: does this text look like a Google Pay statement? */
    fun looksLikeStatement(text: String): Boolean {
        // Same space handling as parsing: extraction can pad words ("UPI  Transaction ID").
        val flat = text.replace(WHITESPACE, " ")
        return UPI.containsMatchIn(flat) && flat.contains("Transaction statement", ignoreCase = true)
    }

    fun parse(text: String, timeZone: TimeZone = TimeZone.getDefault()): GPayStatement {
        if (!looksLikeStatement(text)) {
            throw StatementFormatException("This doesn't look like a Google Pay statement.")
        }
        // Position-based extraction pads words with runs of spaces ("Paid  to"); collapse them.
        val tokens = text.lines().flatMap { line -> tokensOf(line.replace(WHITESPACE, " ").trim()) }

        val firstDate = tokens.indexOfFirst { it.kind == Kind.DATE }
        if (firstDate < 0) throw StatementFormatException("No transactions were found in this statement.")
        // Amounts before the first row are the summary: Sent, then Received.
        val summary = tokens.take(firstDate).filter { it.kind == Kind.AMOUNT }.map { amountOf(it.match) }
        val body = tokens.drop(firstDate)

        val dates = body.filter { it.kind == Kind.DATE }.map { it.match }
        val times = body.filter { it.kind == Kind.TIME }.map { it.match }
        val refs = body.filter { it.kind == Kind.UPI }.map { it.match.groupValues[1] }
        val amounts = body.filter { it.kind == Kind.AMOUNT }.map { amountOf(it.match) }
        val parties = pairParties(body.filter { it.kind == Kind.PARTY }.map { it.match })

        val n = dates.size
        if (listOf(times.size, refs.size, amounts.size, parties.size).any { it != n }) {
            throw StatementFormatException(
                "Couldn't read this statement reliably ($n dates, ${refs.size} references, ${amounts.size} amounts). Nothing was imported."
            )
        }

        val rows = (0 until n).map { i ->
            val (kindWord, name, account) = parties[i]
            val kind = when (kindWord) {
                "Received from" -> StatementRowKind.RECEIVED
                "Self transfer to" -> StatementRowKind.SELF_TRANSFER
                else -> StatementRowKind.PAID
            }
            val accountMatch = account?.let { ACCOUNT_VALUE.find(it) }
            StatementRow(
                kind = kind,
                counterparty = name,
                upiRef = refs[i],
                timeMs = timeOf(dates[i], times[i], timeZone),
                amount = amounts[i],
                bank = bankOf(accountMatch?.groupValues?.get(1) ?: account.orEmpty()),
                accountLast4 = accountMatch?.groupValues?.get(2),
                targetBank = if (kind == StatementRowKind.SELF_TRANSFER) {
                    bankOf(ACCOUNT_VALUE.find(name)?.groupValues?.get(1) ?: name)
                } else {
                    null
                }
            )
        }
        return GPayStatement(
            rows = rows,
            period = PERIOD.find(text)?.value,
            sent = summary.getOrNull(0).takeIf { summary.size == 2 },
            received = summary.getOrNull(1).takeIf { summary.size == 2 }
        )
    }

    /** Fields on one line, left to right. */
    private fun tokensOf(line: String): List<Token> {
        if (line.isEmpty()) return emptyList()
        val found = ArrayList<Token>()
        DATE.findAll(line).forEach { found += Token(Kind.DATE, it) }
        TIME.findAll(line).forEach { found += Token(Kind.TIME, it) }
        UPI.findAll(line).forEach { found += Token(Kind.UPI, it) }
        AMOUNT.findAll(line).forEach { found += Token(Kind.AMOUNT, it) }
        PARTY.findAll(line).forEach { found += Token(Kind.PARTY, it) }
        return found.sortedBy { it.match.range.first }
    }

    /**
     * Each row has a "what" line (Paid to / Received from / Self transfer to + name) followed by
     * an account line (Paid by / Paid to + bank + last 4). "Paid to" is used for both, so the two
     * are told apart by order. Returns (kind word, name, account or null) per row.
     */
    private fun pairParties(matches: List<MatchResult>): List<Triple<String, String, String?>> {
        val rows = ArrayList<Triple<String, String, String?>>()
        var pending: Pair<String, String>? = null
        for (m in matches) {
            // "Paidto" (space lost in extraction) -> "Paid to".
            val word = when (m.groupValues[1].replace(" ", "").lowercase(Locale.US)) {
                "paidby" -> "Paid by"
                "receivedfrom" -> "Received from"
                "selftransferto" -> "Self transfer to"
                else -> "Paid to"
            }
            val value = m.groupValues[2].trim()
            val current = pending
            if (current == null) {
                // Expecting a "what" line; a stray account line cannot start a row.
                if (word != "Paid by") pending = word to value
            } else {
                val isAccount = word == "Paid by" || (word == "Paid to" && ACCOUNT_VALUE.matches(value))
                if (isAccount) {
                    rows += Triple(current.first, current.second, value)
                    pending = null
                } else {
                    // The previous row had no account line.
                    rows += Triple(current.first, current.second, null)
                    pending = word to value
                }
            }
        }
        pending?.let { rows += Triple(it.first, it.second, null) }
        return rows
    }

    private fun amountOf(m: MatchResult): Double = m.groupValues[1].replace(",", "").toDouble()

    private fun timeOf(date: MatchResult, time: MatchResult, timeZone: TimeZone): Long {
        val day = date.groupValues[1].toInt()
        val month = MONTHS.indexOf(date.groupValues[2])
        val year = date.groupValues[3].toInt()
        val hour12 = time.groupValues[1].toInt() % 12
        val pm = time.groupValues[3].equals("PM", ignoreCase = true)
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month, day, hour12 + if (pm) 12 else 0, time.groupValues[2].toInt(), 0)
        }.timeInMillis
    }

    /** "Kotak Mahindra Bank" -> KOTAK, "Bank of India" -> BOI (not State / Central Bank of India). */
    internal fun bankOf(name: String): SmsBank {
        // Spaces ignored: extraction can turn "Bank of India" into "Bank ofIndia".
        val n = name.replace(WHITESPACE, "").lowercase(Locale.US)
        return when {
            "kotak" in n -> SmsBank.KOTAK
            n == "bankofindia" || n == "boi" -> SmsBank.BOI
            else -> SmsBank.UNKNOWN
        }
    }
}
