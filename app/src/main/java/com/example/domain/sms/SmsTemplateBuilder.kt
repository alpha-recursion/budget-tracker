package com.example.domain.sms

import com.example.data.model.TransactionType
import java.util.TimeZone
import kotlin.math.abs

/** A piece of the example SMS: [start] inclusive, [end] exclusive, in the normalised text. */
data class FieldSpan(val start: Int, val end: Int, val value: String)

data class DetectedDate(val span: FieldSpan, val format: SmsDateFormat)

/** Pre-filled guesses for the "Teach this format" screen. */
data class TemplateSuggestions(
    val text: String,
    val amounts: List<FieldSpan>,
    val dates: List<DetectedDate>,
    val refs: List<FieldSpan>,
    val payee: FieldSpan?,
    val type: TransactionType,
    val bank: SmsBank
)

sealed class TemplateBuildResult {
    data class Success(val template: SmsTemplate, val preview: ParsedBankSms) : TemplateBuildResult()
    data class Failure(val message: String) : TemplateBuildResult()
}

/**
 * Builds an [SmsTemplate] from one example SMS and the fields the owner confirmed.
 *
 * The example is turned into a pattern where the chosen fields become capture groups, fixed
 * words stay as they are, words containing digits (account numbers, phone numbers) match any
 * word, and only two words after the last field are kept so differing footers still match.
 */
object SmsTemplateBuilder {

    private const val AMOUNT_GROUP = """([\d,]+(?:\.\d{1,2})?)"""
    private const val PAYEE_GROUP = """(.+?)"""
    private const val REF_GROUP = """([A-Za-z0-9]+)"""
    private const val TRAILING_WORDS_KEPT = 2
    private const val MIN_FIXED_WORDS = 2

    private val AMOUNT = Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val REF = Regex("""Ref(?:erence)?(?:\s*No)?[:.]?\s*([A-Za-z0-9]{6,})""", RegexOption.IGNORE_CASE)
    // Tried in order: "to X" names the payee of a payment; "from/by X" the sender of a credit.
    // Order matters: "Sent Rs.5 from Kotak Bank A/c X1 to Zepto" must give "Zepto".
    private const val PAYEE_END = """(?=\s+(?:on|via|using|UPI|Ref)\b|[.,]|$)"""
    private val PAYEE_PATTERNS = listOf(
        Regex("""\b(?:credited to|paid to|sent to|to)\s+(.+?)$PAYEE_END""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:from|by)\s+(.+?)$PAYEE_END""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+(.+?)$PAYEE_END""", RegexOption.IGNORE_CASE)
    )
    private val DEBIT_WORDS = Regex("""\b(debited|sent|paid|spent|withdrawn|purchase)\b""", RegexOption.IGNORE_CASE)
    private val CREDIT_WORDS = Regex("""\b(credited|received|deposited|refund(?:ed)?)\b""", RegexOption.IGNORE_CASE)

    /** Detects likely fields in [rawSms] to pre-fill the teach screen. */
    fun suggest(rawSms: String): TemplateSuggestions {
        val text = BankSmsParser.normalize(rawSms)
        val amounts = AMOUNT.findAll(text).map { it.groups[1]!!.toSpan() }.toList()
        val dates = SmsDateFormat.entries.flatMap { format ->
            Regex(format.regex).findAll(text).map { DetectedDate(it.toSpan(), format) }
        }.filter { d -> amounts.none { it.overlaps(d.span) } }.sortedBy { it.span.start }
        val refs = REF.findAll(text).map { it.groups[1]!!.toSpan() }.toList()

        val taken = amounts + refs + dates.map { it.span }
        val payee = PAYEE_PATTERNS.asSequence()
            .flatMap { pattern -> pattern.findAll(text).map { it.groups[1]!!.toSpan() } }
            .firstOrNull { span -> taken.none { it.overlaps(span) } && span.value.any { it.isLetter() } }

        val debitAt = DEBIT_WORDS.find(text)?.range?.first
        val creditAt = CREDIT_WORDS.find(text)?.range?.first
        val type = if (creditAt != null && (debitAt == null || creditAt < debitAt)) TransactionType.INCOME else TransactionType.EXPENSE
        val bank = when {
            text.contains("Kotak", ignoreCase = true) -> SmsBank.KOTAK
            Regex("""-\s*BOI\b|(?<!State |Central )\bBank of India\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> SmsBank.BOI
            else -> SmsBank.UNKNOWN
        }
        return TemplateSuggestions(text, amounts, dates, refs, payee, type, bank)
    }

    /**
     * Finds where [value] appears in [text] as the payee, avoiding the other chosen fields.
     * Returns null if the typed payee does not appear in the SMS.
     */
    fun locate(text: String, value: String, avoid: List<FieldSpan>): FieldSpan? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        var from = 0
        while (true) {
            val index = text.indexOf(trimmed, from, ignoreCase = true)
            if (index < 0) return null
            val span = FieldSpan(index, index + trimmed.length, text.substring(index, index + trimmed.length))
            if (avoid.none { it.overlaps(span) }) return span
            from = index + 1
        }
    }

    fun build(
        text: String,
        amount: FieldSpan,
        payee: FieldSpan?,
        date: DetectedDate?,
        ref: FieldSpan?,
        type: TransactionType,
        bank: SmsBank,
        timeZone: TimeZone = TimeZone.getDefault()
    ): TemplateBuildResult {
        if (BankSmsParser.isNonTransaction(text)) {
            return TemplateBuildResult.Failure("This looks like an OTP, request or failed payment, which is never imported.")
        }

        data class Field(val span: FieldSpan, val regex: String, val kind: String)
        val fields = listOfNotNull(
            Field(amount, AMOUNT_GROUP, "amount"),
            payee?.let { Field(it, PAYEE_GROUP, "payee") },
            date?.let { Field(it.span, "(${it.format.regex})", "date") },
            ref?.let { Field(it, REF_GROUP, "ref") }
        ).sortedBy { it.span.start }

        if (fields.zipWithNext().any { (a, b) -> a.span.end > b.span.start }) {
            return TemplateBuildResult.Failure("Two of the chosen parts overlap. Pick different text for each.")
        }

        val pattern = StringBuilder()
        var fixedWords = 0
        var cursor = 0
        val groupOf = HashMap<String, Int>()
        fields.forEachIndexed { index, field ->
            val literal = text.substring(cursor, field.span.start)
            if (field.kind == "payee" || (index > 0 && fields[index - 1].kind == "payee")) {
                // A lazy payee group needs fixed text around it to know where it ends.
                if (field.kind != "payee" && literal.isBlank()) {
                    return TemplateBuildResult.Failure("The payee must be followed by some fixed words in the SMS.")
                }
            }
            val (regex, words) = literalToRegex(literal)
            pattern.append(regex)
            fixedWords += words
            pattern.append(field.regex)
            groupOf[field.kind] = index + 1
            cursor = field.span.end
        }
        val trailing = keepLeadingWords(text.substring(cursor), TRAILING_WORDS_KEPT)
        if (fields.last().kind == "payee" && trailing.isBlank()) {
            return TemplateBuildResult.Failure("The payee must be followed by some fixed words in the SMS.")
        }
        val (trailingRegex, trailingWords) = literalToRegex(trailing)
        pattern.append(trailingRegex)
        fixedWords += trailingWords

        if (fixedWords < MIN_FIXED_WORDS) {
            return TemplateBuildResult.Failure("This SMS has too little fixed text to recognise it reliably.")
        }

        val template = SmsTemplate(
            bank = bank,
            type = type,
            pattern = pattern.toString(),
            amountGroup = groupOf.getValue("amount"),
            payeeGroup = groupOf["payee"],
            dateGroup = groupOf["date"],
            dateFormat = date?.format,
            refGroup = groupOf["ref"],
            sample = text
        )

        // The pattern must read the example back exactly as the owner confirmed it.
        val check = runCatching { template.match(text, timeZone) }.getOrNull()
            ?: return TemplateBuildResult.Failure("Could not build a pattern from this SMS.")
        val expectedAmount = amount.value.replace(",", "").toDoubleOrNull()
        val ok = expectedAmount != null && abs(check.amount - expectedAmount) < 0.005 &&
            (payee == null || check.counterparty == BankSmsParser.cleanCounterparty(payee.value)) &&
            (date == null || (check.dayMs != null && check.dayMs == date.format.parse(date.span.value, timeZone))) &&
            (ref == null || check.upiRef == ref.value)
        if (!ok) return TemplateBuildResult.Failure("The pattern did not read this SMS back correctly. Try choosing the parts again.")

        return TemplateBuildResult.Success(template, check)
    }

    /** Fixed text -> regex. Words with digits match any word; whitespace matches any spacing. */
    private fun literalToRegex(literal: String): Pair<String, Int> {
        if (literal.isEmpty()) return "" to 0
        val sb = StringBuilder()
        var fixedWords = 0
        if (literal.first().isWhitespace()) sb.append("""\s+""")
        val words = literal.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        words.forEachIndexed { i, word ->
            if (i > 0) sb.append("""\s+""")
            if (word.any { it.isDigit() }) {
                sb.append("""\S+""")
            } else {
                sb.append(escape(word))
                if (word.any { it.isLetter() }) fixedWords++
            }
        }
        if (words.isNotEmpty() && literal.last().isWhitespace()) sb.append("""\s+""")
        return sb.toString() to fixedWords
    }

    private fun keepLeadingWords(text: String, count: Int): String {
        val m = Regex("""^\s*(?:\S+\s*){0,$count}""").find(text) ?: return ""
        return m.value.trimEnd()
    }

    private fun escape(word: String): String = buildString {
        word.forEach { c ->
            if (c in """\^$.|?*+()[]{}""") append('\\')
            append(c)
        }
    }

    private fun MatchResult.toSpan() = FieldSpan(range.first, range.last + 1, value)
    private fun MatchGroup.toSpan() = FieldSpan(range.first, range.last + 1, value)
    private fun FieldSpan.overlaps(other: FieldSpan) = start < other.end && other.start < end
}
