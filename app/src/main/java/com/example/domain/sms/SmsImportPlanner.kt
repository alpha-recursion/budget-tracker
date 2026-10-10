package com.example.domain.sms

import com.example.data.local.MerchantRuleEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.model.TransactionType
import com.example.domain.VendorMatcher
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** An SMS as read from the phone's inbox. */
data class RawSms(
    val id: Long,
    val sender: String,
    val body: String,
    val receivedAt: Long
)

/** One transaction found in an SMS, ready for the user to review. */
data class ImportCandidate(
    val dedupeKey: String,
    val parsed: ParsedBankSms,
    val receivedAt: Long,
    /** When the money moved: the SMS arrival time, or the date written in the SMS if they differ. */
    val dateMs: Long,
    val title: String,
    /** Credits use a generic format that is not verified yet; the user must confirm them. */
    val needsReview: Boolean,
    /** A transaction with the same type and amount already exists on that day (likely entered by hand). */
    val possibleDuplicate: Boolean,
    /** A debit and a credit with the same UPI reference: money moved between the owner's own accounts. */
    val looksLikeSelfTransfer: Boolean
) {
    val selectedByDefault: Boolean get() = !needsReview && !possibleDuplicate
}

/** Candidates for one payee and direction; the user picks a category (or self transfer) once. */
data class ImportGroup(
    val key: String,
    val title: String,
    val type: TransactionType,
    /** Key used to remember the choice for next time; null when the SMS named no payee. */
    val ruleKey: String?,
    val items: List<ImportCandidate>,
    val suggestedCategory: String,
    val suggestedSelfTransfer: Boolean,
    /** Other spellings of the same vendor merged into this group (e.g. truncated names). */
    val nameVariants: List<String> = emptyList()
)

/** What a scan saw, so the user can tell why messages were or were not found. */
data class ScanStats(
    val messagesRead: Int,
    /** Messages from alphanumeric (bank-style) senders. */
    val fromBankSenders: Int,
    /** Kotak/BOI transaction alerts understood, including already-imported ones. */
    val recognised: Int,
    /** Newest few bank-sender messages that look like transactions but were not understood. */
    val unrecognised: List<RawSms>
)

data class ImportPlan(
    val groups: List<ImportGroup>,
    /** Bank transaction messages skipped because they were imported in an earlier scan. */
    val alreadyImportedCount: Int,
    val stats: ScanStats = ScanStats(0, 0, 0, emptyList())
) {
    val candidateCount: Int get() = groups.sumOf { it.items.size }
}

/**
 * Turns inbox messages into a reviewable import plan. Pure Kotlin so every rule is unit tested.
 */
object SmsImportPlanner {

    /** Banks the owner chose to import from. */
    private val SUPPORTED_BANKS = setOf(SmsBank.KOTAK, SmsBank.BOI)

    /**
     * Indian banks send alerts from alphanumeric headers (e.g. "JD-KOTAKB"). Messages from
     * phone numbers are ignored so a forwarded or spoofed text cannot add transactions.
     */
    fun isBankSender(sender: String): Boolean = sender.any { it.isLetter() }

    /** Max unrecognised examples kept for the scan-details panel. */
    private const val UNRECOGNISED_EXAMPLES = 5

    /** True when [sms] is a Kotak/BOI transaction alert this app can import. */
    fun isSupportedTransaction(
        sms: RawSms,
        timeZone: TimeZone = TimeZone.getDefault(),
        templates: List<SmsTemplate> = emptyList()
    ): Boolean {
        if (!isBankSender(sms.sender)) return false
        val parsed = BankSmsParser.parse(sms.body, timeZone, templates) ?: return false
        return parsed.isSupported()
    }

    // Formats the owner taught are always wanted, whatever bank they belong to.
    private fun ParsedBankSms.isSupported(): Boolean = fromTemplate || bank in SUPPORTED_BANKS

    /**
     * Stable identity of an SMS transaction. The UPI reference is shared by both sides of a
     * transfer (Kotak debit + BOI credit), so the bank and direction are part of the key.
     * Without a usable reference, a fingerprint of the message itself is used.
     */
    fun dedupeKey(sms: RawSms, parsed: ParsedBankSms): String {
        val ref = parsed.upiRef
        if (ref != null) return "ref:${parsed.bank.name}:${parsed.type.name}:$ref"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${sms.sender}|${sms.receivedAt}|${sms.body}".toByteArray(Charsets.UTF_8))
        return "sms:" + digest.take(16).joinToString("") { "%02x".format(it) }
    }

    /** Remembered-choice key: direction + normalised payee, e.g. "expense:swaraj poli bhaji kendra". */
    fun ruleKey(type: TransactionType, counterparty: String): String? {
        val normalised = counterparty.lowercase(Locale.US).replace(Regex("""\s+"""), " ").trim()
        if (normalised.isEmpty()) return null
        return "${type.name.lowercase(Locale.US)}:$normalised"
    }

    fun plan(
        messages: List<RawSms>,
        alreadyImportedKeys: Set<String>,
        existingTransactions: List<TransactionEntity>,
        rules: List<MerchantRuleEntity>,
        validCategories: Set<String>,
        timeZone: TimeZone = TimeZone.getDefault(),
        templates: List<SmsTemplate> = emptyList()
    ): ImportPlan {
        val rulesByKey = rules.associateBy { it.counterpartyKey }
        val seenKeys = HashSet<String>()
        // Distinct keys, so an alert the bank sent twice is not counted twice.
        val alreadyImported = HashSet<String>()

        data class Found(val sms: RawSms, val parsed: ParsedBankSms, val key: String)

        var fromBankSenders = 0
        var recognised = 0
        val unrecognised = ArrayList<RawSms>()

        val found = messages
            .sortedByDescending { it.receivedAt }
            .mapNotNull { sms ->
                if (!isBankSender(sms.sender)) return@mapNotNull null
                fromBankSenders++
                val parsed = BankSmsParser.parse(sms.body, timeZone, templates)?.takeIf { it.isSupported() }
                if (parsed == null) {
                    if (unrecognised.size < UNRECOGNISED_EXAMPLES &&
                        BankSmsParser.looksLikeTransaction(sms.body) &&
                        unrecognised.none { it.body == sms.body }
                    ) {
                        unrecognised += sms
                    }
                    return@mapNotNull null
                }
                recognised++
                val key = dedupeKey(sms, parsed)
                when {
                    key in alreadyImportedKeys -> {
                        alreadyImported += key
                        null
                    }
                    !seenKeys.add(key) -> null // same alert delivered twice in this scan
                    else -> Found(sms, parsed, key)
                }
            }

        // UPI refs that appear as both a debit and a credit of the same amount in this scan.
        val selfTransferRefs = found
            .filter { it.parsed.upiRef != null }
            .groupBy { it.parsed.upiRef }
            .filterValues { sides ->
                sides.any { it.parsed.type == TransactionType.EXPENSE } &&
                    sides.any { it.parsed.type == TransactionType.INCOME } &&
                    sides.map { it.parsed.amount }.distinct().size == 1
            }
            .keys

        val candidates = found.map { f ->
            val dateMs = transactionTime(f.parsed.dayMs, f.sms.receivedAt, timeZone)
            ImportCandidate(
                dedupeKey = f.key,
                parsed = f.parsed,
                receivedAt = f.sms.receivedAt,
                dateMs = dateMs,
                title = f.parsed.counterparty ?: fallbackTitle(f.parsed),
                needsReview = !f.parsed.isConfident,
                possibleDuplicate = existingTransactions.any { tx ->
                    tx.type == f.parsed.type.name &&
                        abs(tx.amount - f.parsed.amount) < 0.005 &&
                        sameLocalDay(tx.date, dateMs, timeZone)
                },
                looksLikeSelfTransfer = f.parsed.upiRef != null && f.parsed.upiRef in selfTransferRefs
            )
        }

        // Same vendor spelled differently (banks truncate names) -> one group per direction.
        val canonicalByType = TransactionType.entries.associateWith { type ->
            VendorMatcher.canonicalNames(
                candidates.filter { it.parsed.type == type }.mapNotNull { it.parsed.counterparty }
            )
        }

        val groups = candidates
            .groupBy { c ->
                val payee = c.parsed.counterparty
                val type = c.parsed.type
                if (payee != null && VendorMatcher.compact(payee).isNotEmpty()) {
                    "${type.name.lowercase(Locale.US)}:" + VendorMatcher.groupKey(payee, canonicalByType.getValue(type))
                } else {
                    // Messages without a payee are reviewed one by one, so e.g. salary and a
                    // friend's repayment are not forced into the same category.
                    "single:${c.dedupeKey}"
                }
            }
            .map { (key, items) ->
                val first = items.first()
                val type = first.parsed.type
                val canonical = first.parsed.counterparty?.let { canonicalByType.getValue(type)[it] ?: it }
                val canonicalKey = canonical?.let { ruleKey(type, it) }
                val rule = canonical?.let { findRule(type, it, canonicalKey, rulesByKey) }
                // Update the existing rule for this vendor rather than adding a second spelling:
                // two rules for one vendor would make the next spelling ambiguous.
                val ruleKey = rule?.counterpartyKey ?: canonicalKey
                val category = rule?.category?.takeIf { it in validCategories } ?: Category.OTHER.displayName
                ImportGroup(
                    key = key,
                    title = canonical ?: first.title,
                    type = type,
                    ruleKey = ruleKey,
                    items = items,
                    suggestedCategory = category,
                    suggestedSelfTransfer = rule?.isSelfTransfer == true || items.all { it.looksLikeSelfTransfer },
                    nameVariants = items.map { it.title }.distinct().filter { it != canonical }
                )
            }
            .sortedByDescending { group -> group.items.maxOf { it.dateMs } }

        return ImportPlan(
            groups = groups,
            alreadyImportedCount = alreadyImported.size,
            stats = ScanStats(
                messagesRead = messages.size,
                fromBankSenders = fromBankSenders,
                recognised = recognised,
                unrecognised = unrecognised
            )
        )
    }

    /**
     * The remembered choice for a payee: exact key first, otherwise a rule for the same vendor
     * spelled differently (e.g. saved as "some other t c", now seen as "Some Other T Center").
     */
    fun findRule(
        type: TransactionType,
        payee: String,
        exactKey: String?,
        rulesByKey: Map<String, MerchantRuleEntity>
    ): MerchantRuleEntity? {
        exactKey?.let { rulesByKey[it] }?.let { return it }
        val prefix = "${type.name.lowercase(Locale.US)}:"
        val sameDirection = rulesByKey.values
            .filter { it.counterpartyKey.startsWith(prefix) }
            .associateBy { VendorMatcher.compact(it.counterpartyKey.removePrefix(prefix)) }
        val match = VendorMatcher.bestMatch(payee, sameDirection.keys) ?: return null
        return sameDirection[match]
    }

    private fun fallbackTitle(parsed: ParsedBankSms): String {
        val bank = when (parsed.bank) {
            SmsBank.KOTAK -> "Kotak"
            SmsBank.BOI -> "BOI"
            SmsBank.UNKNOWN -> "bank"
        }
        return if (parsed.type == TransactionType.INCOME) "Money received ($bank)" else "Payment ($bank)"
    }

    /** Use the SMS arrival time when it is on the date the SMS states; otherwise trust the stated date. */
    private fun transactionTime(dayMs: Long?, receivedAt: Long, timeZone: TimeZone): Long = when {
        dayMs == null -> receivedAt
        sameLocalDay(dayMs, receivedAt, timeZone) -> receivedAt
        else -> dayMs
    }

    private fun sameLocalDay(a: Long, b: Long, timeZone: TimeZone): Boolean {
        val ca = Calendar.getInstance(timeZone).apply { timeInMillis = a }
        val cb = Calendar.getInstance(timeZone).apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}
