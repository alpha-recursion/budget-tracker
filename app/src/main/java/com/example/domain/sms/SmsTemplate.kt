package com.example.domain.sms

import com.example.data.model.TransactionType
import java.util.TimeZone

/** Date layouts found in Indian bank SMS. */
enum class SmsDateFormat(val regex: String) {
    /** 27-08-26, 27/08/2026 */
    DAY_MONTH_YEAR_NUMERIC("""\d{1,2}[-/]\d{1,2}[-/]\d{2,4}"""),

    /** 21Aug26, 21-Aug-26, 21 Aug 2026 */
    DAY_MONTH_NAME_YEAR("""\d{1,2}[- ]?[A-Za-z]{3}[- ,]*\d{2,4}""");

    fun parse(raw: String, timeZone: TimeZone): Long? = when (this) {
        DAY_MONTH_YEAR_NUMERIC -> BankSmsParser.parseNumericDate(raw, timeZone)
        DAY_MONTH_NAME_YEAR -> BankSmsParser.parseMonthNameDate(raw, timeZone)
    }
}

/**
 * A message format taught by the owner from one example SMS. [pattern] is a regular expression
 * whose numbered groups hold the fields (numbered, not named: named groups need Android 8+).
 */
data class SmsTemplate(
    val id: Long = 0,
    val bank: SmsBank,
    val type: TransactionType,
    val pattern: String,
    val amountGroup: Int,
    val payeeGroup: Int?,
    val dateGroup: Int?,
    val dateFormat: SmsDateFormat?,
    val refGroup: Int?,
    val sample: String
) {
    private val regex: Regex by lazy { Regex(pattern, RegexOption.IGNORE_CASE) }

    /** Reads [normalizedText] (whitespace already collapsed); null if it is not this format. */
    fun match(normalizedText: String, timeZone: TimeZone): ParsedBankSms? {
        val m = regex.find(normalizedText) ?: return null
        fun group(index: Int?): String? = index?.let { m.groups[it]?.value }
        val amount = group(amountGroup)?.replace(",", "")?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        return ParsedBankSms(
            amount = amount,
            type = type,
            counterparty = group(payeeGroup)?.let { BankSmsParser.cleanCounterparty(it) },
            dayMs = group(dateGroup)?.let { raw -> dateFormat?.parse(raw, timeZone) },
            upiRef = group(refGroup)?.takeUnless { ref -> ref.all { it == 'X' || it == 'x' } },
            bank = bank,
            isConfident = true,
            fromTemplate = true
        )
    }
}
