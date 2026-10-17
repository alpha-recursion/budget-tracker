package com.example.domain.sms

import com.example.data.model.TransactionType
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class SmsBank { KOTAK, BOI, UNKNOWN }

/**
 * A money movement read from a bank SMS.
 *
 * @property dayMs local midnight of the transaction date from the SMS, or null if the SMS had
 *   no parseable date (the caller should then use the time the SMS arrived).
 * @property upiRef UPI reference for de-duplication; null when absent or masked ("XXXX").
 * @property isConfident true when a bank-specific format matched; false for the generic
 *   fallback, whose results should be reviewed by the user.
 */
data class ParsedBankSms(
    val amount: Double,
    val type: TransactionType,
    val counterparty: String?,
    val dayMs: Long?,
    val upiRef: String?,
    val bank: SmsBank,
    val isConfident: Boolean,
    /** Read with a format the owner taught (see [SmsTemplate]). */
    val fromTemplate: Boolean = false,
    /**
     * A failed payment given back by the bank. [upiRef] is the reference of the original
     * debit, so the planner can cancel that payment instead of counting the money twice.
     */
    val isReversal: Boolean = false,
    /** The source says this was money between the owner's own accounts (statement rows). */
    val isSelfTransfer: Boolean = false,
    /** Where it was read when not from an SMS, e.g. "Google Pay statement". */
    val source: String? = null
)

/**
 * Parses transaction alerts from the owner's banks (Kotak, Bank of India), with a generic
 * fallback for other formats. Pure Kotlin so it can be unit tested without a device.
 *
 * Formats are taken from real messages:
 * - Kotak: "Sent Rs.45.00 from Kotak Bank A/c XXXXX to YOGESH BBAN SUPEKAR on 27-08-26. UPI Ref ..."
 * - BOI:   "Rs.100.00 debited A/cXX5XXX and credited to SWARAJ POLI BHAJI KENDRA via UPI Ref No
 *           659909850362 on 21Aug26. ... -BOI"
 * - Kotak credit: "Received Rs.100.00 in your Kotak Bank AC 4880 from GAYATRI HARISHCHANDR on
 *           24-08-26.UPI Ref:623610319810"
 * - Kotak reversal: "Rs.64.00 is credited to Kotak Bank a/c no. 4880 on 19-07-26 as a reversal
 *           of debit transaction (UPI Ref no 620002690546)."
 * - BOI credit: "BOI - Rs.5000.00 Credited to your Ac XX5781 on 12-10-26 by UPI ref
 *           No.628583071397.Avl Bal 10469.82"
 *
 * Credit card bill payments ("Payment of INR 2375.65 is credited to your Kotak Bank Credit Card
 * x4867 ...") are ignored: the money already left a bank account as its own debit alert.
 */
object BankSmsParser {

    private const val AMOUNT = """([\d,]+(?:\.\d{1,2})?)"""

    private val KOTAK_SENT = Regex(
        """Sent\s+Rs\.?\s*$AMOUNT\s+from\s+Kotak\s+Bank\s+A/c\s*\S+\s+to\s+(.+?)\s+on\s+(\d{1,2}-\d{1,2}-\d{2,4})\.?\s*(?:UPI\s+Ref(?:\s+No)?[:.]?\s*([A-Za-z0-9]+))?""",
        RegexOption.IGNORE_CASE
    )

    // Real messages write "AC 4880"; "A/c" is accepted too.
    private val KOTAK_RECEIVED = Regex(
        """Received\s+Rs\.?\s*$AMOUNT\s+in\s+your\s+Kotak\s+Bank\s+A/?c\s*\S+\s+from\s+(.+?)\s+on\s+(\d{1,2}-\d{1,2}-\d{2,4})\.?\s*(?:UPI\s+Ref(?:\s+No)?[:.]?\s*([A-Za-z0-9]+))?""",
        RegexOption.IGNORE_CASE
    )

    private val KOTAK_REVERSAL = Regex(
        """Rs\.?\s*$AMOUNT\s+is\s+credited\s+to\s+Kotak\s+Bank\s+A/?c(?:\s+no\.?)?\s*\S+\s+on\s+(\d{1,2}-\d{1,2}-\d{2,4})\s+as\s+a\s+reversal\s+of\s+debit\s+transaction\s*\(?\s*UPI\s+Ref(?:\s+No)?\.?\s*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val BOI_CREDIT = Regex(
        """\bBOI\s*-\s*Rs\.?\s*$AMOUNT\s+Credited\s+to\s+your\s+A/?c\s*\S+\s+on\s+(\d{1,2}-\d{1,2}-\d{2,4})\s+by\s+UPI\s+Ref\s+No\.?\s*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    // Paying a credit card bill: not income, and the bank debit that paid it is already an alert.
    private val CARD_BILL_PAYMENT = Regex("""\bcredited\s+to\s+your\s+.{0,40}?\bcredit\s+card\b""", RegexOption.IGNORE_CASE)

    private val BOI_DEBIT = Regex(
        """Rs\.?\s*$AMOUNT\s+debited\s+A/c\s*\S+\s+and\s+credited\s+to\s+(.+?)\s+via\s+UPI\s+Ref\s+No\.?\s*([A-Za-z0-9]+)\s+on\s+(\d{1,2}[A-Za-z]{3}\d{2,4})""",
        RegexOption.IGNORE_CASE
    )

    private val GENERIC_AMOUNT = Regex("""(?:Rs\.?|INR|₹)\s*$AMOUNT""", RegexOption.IGNORE_CASE)
    private val GENERIC_REF = Regex("""(?:UPI\s+Ref(?:erence)?(?:\s+No)?|Ref\s+No)[:.]?\s*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val DEBIT_WORDS = Regex("""\b(debited|sent|paid|spent|withdrawn|purchase)\b""", RegexOption.IGNORE_CASE)
    private val CREDIT_WORDS = Regex("""\b(credited|received|deposited|refund(?:ed)?|reversed)\b""", RegexOption.IGNORE_CASE)
    // "-BOI" signature, "BOI -" prefix or "Bank of India", but not "State Bank of India" / "Central Bank of India".
    private val BOI_MARKER = Regex("""-\s*BOI\b|^BOI\s*-|(?<!State |Central )\bBank of India\b""", RegexOption.IGNORE_CASE)
    private val ACCOUNT_HINT = Regex("""A/c|\bUPI\b|\bcard\b|\baccount\b""", RegexOption.IGNORE_CASE)

    // Messages that mention money but did not move it.
    private val NOT_A_TRANSACTION = Regex(
        """\bOTP\b|one[- ]time password|\brequest(?:ed|s)?\b|\bwill be debited\b|\bis due\b|\bfailed\b|\bdeclined\b|\bunsuccessful\b""",
        RegexOption.IGNORE_CASE
    )

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private val TRANSACTION_WORDS = Regex(
        """\b(debited|credited|sent|received|paid|spent|withdrawn|deposited)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Rough check used for scan diagnostics: does this text look like a money movement alert
     * (amount + transaction word, and not an OTP/request/failure)? It does not extract anything.
     */
    fun looksLikeTransaction(body: String): Boolean {
        val text = body.replace(Regex("""\s+"""), " ")
        return GENERIC_AMOUNT.containsMatchIn(text) &&
            TRANSACTION_WORDS.containsMatchIn(text) &&
            !isIgnored(text)
    }

    /** Collapses whitespace so formats match however the SMS was wrapped. */
    fun normalize(body: String): String = body.replace(Regex("""\s+"""), " ").trim()

    /**
     * True for OTPs, payment requests, failures and other messages that never move money, and
     * for credit card bill payments, which are counted by the bank debit that paid them.
     */
    fun isNonTransaction(body: String): Boolean = isIgnored(normalize(body))

    private fun isIgnored(text: String): Boolean =
        NOT_A_TRANSACTION.containsMatchIn(text) || CARD_BILL_PAYMENT.containsMatchIn(text)

    /**
     * @param templates formats taught by the owner; tried first because the owner knows their
     *   own messages best.
     */
    fun parse(
        body: String,
        timeZone: TimeZone = TimeZone.getDefault(),
        templates: List<SmsTemplate> = emptyList()
    ): ParsedBankSms? {
        val text = normalize(body)
        if (isIgnored(text)) return null

        for (template in templates) {
            template.match(text, timeZone)?.let { return it }
        }

        KOTAK_SENT.find(text)?.let { m ->
            return build(m.groupValues[1], TransactionType.EXPENSE, m.groupValues[2],
                parseNumericDate(m.groupValues[3], timeZone), m.groupValues[4], SmsBank.KOTAK)
        }
        KOTAK_RECEIVED.find(text)?.let { m ->
            return build(m.groupValues[1], TransactionType.INCOME, m.groupValues[2],
                parseNumericDate(m.groupValues[3], timeZone), m.groupValues[4], SmsBank.KOTAK)
        }
        KOTAK_REVERSAL.find(text)?.let { m ->
            return build(m.groupValues[1], TransactionType.INCOME, "",
                parseNumericDate(m.groupValues[2], timeZone), m.groupValues[3], SmsBank.KOTAK)
                ?.copy(isReversal = true)
        }
        BOI_DEBIT.find(text)?.let { m ->
            return build(m.groupValues[1], TransactionType.EXPENSE, m.groupValues[2],
                parseMonthNameDate(m.groupValues[4], timeZone), m.groupValues[3], SmsBank.BOI)
        }
        BOI_CREDIT.find(text)?.let { m ->
            return build(m.groupValues[1], TransactionType.INCOME, "",
                parseNumericDate(m.groupValues[2], timeZone), m.groupValues[3], SmsBank.BOI)
        }
        return parseGeneric(text)
    }

    private fun parseGeneric(text: String): ParsedBankSms? {
        if (!ACCOUNT_HINT.containsMatchIn(text)) return null
        val amount = GENERIC_AMOUNT.find(text)?.groupValues?.get(1)?.toAmount() ?: return null
        if (amount <= 0.0) return null

        // "Rs.100 debited A/c... and credited to X" is a debit: the earliest keyword wins.
        val debitAt = DEBIT_WORDS.find(text)?.range?.first
        val creditAt = CREDIT_WORDS.find(text)?.range?.first
        val type = when {
            debitAt == null && creditAt == null -> return null
            creditAt == null -> TransactionType.EXPENSE
            debitAt == null -> TransactionType.INCOME
            debitAt < creditAt -> TransactionType.EXPENSE
            else -> TransactionType.INCOME
        }
        val bank = when {
            text.contains("Kotak", ignoreCase = true) -> SmsBank.KOTAK
            BOI_MARKER.containsMatchIn(text) -> SmsBank.BOI
            else -> SmsBank.UNKNOWN
        }
        return ParsedBankSms(
            amount = amount,
            type = type,
            counterparty = null,
            dayMs = null,
            upiRef = cleanRef(GENERIC_REF.find(text)?.groupValues?.get(1)),
            bank = bank,
            isConfident = false
        )
    }

    private fun build(
        rawAmount: String,
        type: TransactionType,
        rawCounterparty: String,
        dayMs: Long?,
        rawRef: String?,
        bank: SmsBank
    ): ParsedBankSms? {
        val amount = rawAmount.toAmount() ?: return null
        return ParsedBankSms(
            amount = amount,
            type = type,
            counterparty = cleanCounterparty(rawCounterparty),
            dayMs = dayMs,
            upiRef = cleanRef(rawRef),
            bank = bank,
            isConfident = true
        )
    }

    private fun String.toAmount(): Double? = replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 }

    /** "YOGESH  BBAN SUPEKAR." -> "Yogesh Bban Supekar"; VPAs like "swiggy@icici" are kept as-is. */
    internal fun cleanCounterparty(raw: String): String? {
        val trimmed = raw.trim().trimEnd('.', ',').replace(Regex("""\s+"""), " ")
        if (trimmed.isEmpty()) return null
        if (trimmed.contains('@')) return trimmed.lowercase(Locale.US)
        return trimmed.lowercase(Locale.US).split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase(Locale.US) }
        }
    }

    private fun cleanRef(raw: String?): String? {
        val ref = raw?.trim().orEmpty()
        // Banks or users may mask the reference as "XXXX..."; that cannot be used for de-duplication.
        if (ref.isEmpty() || ref.all { it == 'X' || it == 'x' }) return null
        return ref
    }

    /** "27-08-26", "27-08-2026" or "27/08/26" (day-month-year). */
    internal fun parseNumericDate(raw: String, timeZone: TimeZone): Long? {
        val parts = raw.split('-', '/').mapNotNull { it.trim().toIntOrNull() }
        if (parts.size != 3) return null
        return localMidnight(parts[2], parts[1], parts[0], timeZone)
    }

    /** "21Aug26", "21Aug2026", "21-Aug-26" or "21 Aug, 2026". */
    internal fun parseMonthNameDate(raw: String, timeZone: TimeZone): Long? {
        val m = Regex("""(\d{1,2})[- ]?([A-Za-z]{3})[- ,]*(\d{2,4})""").matchEntire(raw.trim()) ?: return null
        val month = MONTHS.indexOf(m.groupValues[2].lowercase(Locale.US)) + 1
        if (month == 0) return null
        return localMidnight(m.groupValues[3].toInt(), month, m.groupValues[1].toInt(), timeZone)
    }

    private fun localMidnight(year: Int, month: Int, day: Int, timeZone: TimeZone): Long? {
        val fullYear = if (year < 100) 2000 + year else year
        if (month !in 1..12 || day !in 1..31) return null
        return Calendar.getInstance(timeZone).apply {
            isLenient = false
            clear()
            set(fullYear, month - 1, day)
        }.runCatching { timeInMillis }.getOrNull()
    }
}
