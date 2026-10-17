package com.example.domain.sms

import com.example.data.local.MerchantRuleEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.model.TransactionType
import com.example.domain.AutoCategorizer
import com.example.domain.AutoSuggestion
import com.example.domain.CategoryModel
import com.example.domain.VendorMatcher
import com.example.domain.statement.GPayStatement
import com.example.domain.statement.StatementRowKind
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
    val looksLikeSelfTransfer: Boolean,
    /**
     * A reversal whose original payment was already recorded: importing it removes that payment
     * (this is its dedupe key) instead of adding income.
     */
    val cancelsKey: String? = null,
    /**
     * Keys of the other side of a self transfer (the credit into the owner's other account),
     * recorded too when it is imported as a self transfer, so that alert is not counted later.
     */
    val extraKeys: List<String> = emptyList()
) {
    val selectedByDefault: Boolean get() = !needsReview && !possibleDuplicate

    /** A reversal whose original payment was not found; the owner decides whether it is income. */
    val isUnmatchedReversal: Boolean get() = parsed.isReversal && cancelsKey == null
}

/** The transaction an imported SMS becomes; shared by the review screen and live capture. */
fun ImportCandidate.toTransaction(category: String): TransactionEntity {
    val bank = when (parsed.bank) {
        SmsBank.KOTAK -> "Kotak"
        SmsBank.BOI -> "BOI"
        SmsBank.UNKNOWN -> "bank"
    }
    return TransactionEntity(
        title = title.take(60), // same limit as the manual form
        amount = parsed.amount,
        type = parsed.type.name,
        category = category,
        date = dateMs,
        note = (parsed.source?.let { "From $it ($bank)" } ?: "From $bank SMS") +
            (parsed.upiRef?.let { " · UPI Ref $it" } ?: "")
    )
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
    val nameVariants: List<String> = emptyList(),
    /** The owner already chose a (still existing) category or "self transfer" for this payee. */
    val hasRememberedChoice: Boolean = false,
    /** Category suggested automatically (keywords or learned); null when remembered or none. */
    val autoCategory: AutoSuggestion.Category? = null,
    /** The payee looks like a person: no category guessed, the owner picks lending or one. */
    val isPerson: Boolean = false
) {
    /** Nothing decided the category: no remembered choice, no suggestion, not a transfer. */
    val needsCategory: Boolean
        get() = suggestedCategory == Category.OTHER.displayName && !hasRememberedChoice &&
            !suggestedSelfTransfer && !isPerson && !cancelsPayment

    /** A reversal that cancels an earlier payment: no category or lending choice applies. */
    val cancelsPayment: Boolean get() = items.all { it.cancelsKey != null }
}

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
    val stats: ScanStats = ScanStats(0, 0, 0, emptyList()),
    /** Set when the plan came from a statement file rather than the SMS inbox. */
    val statement: StatementSummary? = null
) {
    val candidateCount: Int get() = groups.sumOf { it.items.size }
}

/** What a statement file contained, shown above its review list. */
data class StatementSummary(
    val name: String,
    val period: String?,
    val rows: Int,
    val selfTransfers: Int,
    val paidTotal: Double,
    val receivedTotal: Double,
    /** The rows add up to the totals printed on the statement. */
    val totalsMatch: Boolean
)

/** A transaction found in an SMS or statement row, before review. */
private class Found(val sms: RawSms, val parsed: ParsedBankSms, val key: String, val extraKeys: List<String> = emptyList())

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

    /** Dedupe key of the payment a reversal gives back (same bank and UPI reference, as a debit). */
    fun reversedPaymentKey(parsed: ParsedBankSms): String? {
        if (!parsed.isReversal) return null
        val ref = parsed.upiRef ?: return null
        return "ref:${parsed.bank.name}:${TransactionType.EXPENSE.name}:$ref"
    }

    /** Remembered-choice key: direction + normalised payee, e.g. "expense:annapurna poli bhaji kendra". */
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
        templates: List<SmsTemplate> = emptyList(),
        /** What the owner's categorised payees taught, for suggesting new payees' categories. */
        model: CategoryModel = CategoryModel.EMPTY
    ): ImportPlan {
        val rulesByKey = rules.associateBy { it.counterpartyKey }
        val seenKeys = HashSet<String>()
        // Distinct keys, so an alert the bank sent twice is not counted twice.
        val alreadyImported = HashSet<String>()

        var fromBankSenders = 0
        var recognised = 0
        val unrecognised = ArrayList<RawSms>()

        val allFound = messages
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

        return buildPlan(
            allFound = allFound,
            alreadyImportedCount = alreadyImported.size,
            stats = ScanStats(
                messagesRead = messages.size,
                fromBankSenders = fromBankSenders,
                recognised = recognised,
                unrecognised = unrecognised
            ),
            alreadyImportedKeys = alreadyImportedKeys,
            existingTransactions = existingTransactions,
            rulesByKey = rulesByKey,
            validCategories = validCategories,
            timeZone = timeZone,
            model = model
        )
    }

    /** Shared by SMS scans and statement imports: reversals, self transfers, grouping, rules. */
    private fun buildPlan(
        allFound: List<Found>,
        alreadyImportedCount: Int,
        stats: ScanStats,
        alreadyImportedKeys: Set<String>,
        existingTransactions: List<TransactionEntity>,
        rulesByKey: Map<String, MerchantRuleEntity>,
        validCategories: Set<String>,
        timeZone: TimeZone,
        statement: StatementSummary? = null,
        model: CategoryModel = CategoryModel.EMPTY
    ): ImportPlan {
        // A failed payment and its reversal in the same scan cancel out: neither is offered.
        val foundKeys = allFound.map { it.key }.toHashSet()
        val cancelledInScan = allFound
            .mapNotNull { f -> reversedPaymentKey(f.parsed)?.takeIf { it in foundKeys }?.let { listOf(f.key, it) } }
            .flatten()
            .toHashSet()
        val found = allFound.filterNot { it.key in cancelledInScan }

        // UPI refs that appear as both a debit and a credit of the same amount in this scan.
        // A reversal shares its payment's ref but is not a transfer.
        val selfTransferRefs = found
            .filter { it.parsed.upiRef != null && !it.parsed.isReversal }
            .groupBy { it.parsed.upiRef }
            .filterValues { sides ->
                sides.any { it.parsed.type == TransactionType.EXPENSE } &&
                    sides.any { it.parsed.type == TransactionType.INCOME } &&
                    sides.map { it.parsed.amount }.distinct().size == 1
            }
            .keys

        val candidates = found.map { f ->
            val dateMs = transactionTime(f.parsed.dayMs, f.sms.receivedAt, timeZone)
            val cancelsKey = reversedPaymentKey(f.parsed)?.takeIf { it in alreadyImportedKeys }
            ImportCandidate(
                dedupeKey = f.key,
                parsed = f.parsed,
                receivedAt = f.sms.receivedAt,
                dateMs = dateMs,
                title = f.parsed.counterparty ?: fallbackTitle(f.parsed),
                // A reversal without its payment may still be a real refund: the owner decides.
                needsReview = !f.parsed.isConfident || (f.parsed.isReversal && cancelsKey == null),
                cancelsKey = cancelsKey,
                possibleDuplicate = cancelsKey == null && existingTransactions.any { tx ->
                    tx.type == f.parsed.type.name &&
                        abs(tx.amount - f.parsed.amount) < 0.005 &&
                        sameLocalDay(tx.date, dateMs, timeZone)
                },
                looksLikeSelfTransfer = f.parsed.isSelfTransfer ||
                    (f.parsed.upiRef != null && f.parsed.upiRef in selfTransferRefs),
                extraKeys = f.extraKeys
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
                val remembered = rule?.takeIf { !it.isSelfTransfer }?.category?.takeIf { it in validCategories }
                val selfTransfer = rule?.isSelfTransfer == true || items.all { it.looksLikeSelfTransfer }
                // Nobody chose a category for this payee yet: suggest one from keywords or what
                // the owner taught (reversals and transfers need none).
                val auto = if (remembered == null && !selfTransfer && canonical != null && items.none { it.cancelsKey != null }) {
                    AutoCategorizer.suggest(canonical, type, validCategories, model)
                } else {
                    null
                }
                val guessed = auto as? AutoSuggestion.Category
                ImportGroup(
                    key = key,
                    title = canonical ?: first.title,
                    type = type,
                    ruleKey = ruleKey,
                    items = items,
                    suggestedCategory = remembered ?: guessed?.name ?: Category.OTHER.displayName,
                    suggestedSelfTransfer = selfTransfer,
                    nameVariants = items.map { it.title }.distinct().filter { it != canonical },
                    hasRememberedChoice = rule != null && (rule.isSelfTransfer || rule.category in validCategories),
                    autoCategory = guessed,
                    isPerson = auto is AutoSuggestion.Person
                )
            }
            // Groups still needing a decision first, then people, then the rest; newest first within each.
            .sortedWith(
                compareBy<ImportGroup> { group ->
                    when {
                        group.needsCategory -> 0
                        group.isPerson -> 1
                        else -> 2
                    }
                }.thenByDescending { group -> group.items.maxOf { it.dateMs } }
            )

        return ImportPlan(
            groups = groups,
            alreadyImportedCount = alreadyImportedCount,
            stats = stats,
            statement = statement
        )
    }

    /**
     * Turns the rows of a Google Pay statement into the same reviewable plan as an SMS scan.
     * Rows use the SMS dedupe keys (bank + direction + UPI reference), so payments already
     * imported from SMS are skipped, and a later SMS scan skips what this import added.
     */
    fun planStatement(
        statement: GPayStatement,
        alreadyImportedKeys: Set<String>,
        existingTransactions: List<TransactionEntity>,
        rules: List<MerchantRuleEntity>,
        validCategories: Set<String>,
        timeZone: TimeZone = TimeZone.getDefault(),
        model: CategoryModel = CategoryModel.EMPTY
    ): ImportPlan {
        val alreadyImported = HashSet<String>()
        val seenKeys = HashSet<String>()
        val allFound = statement.rows.sortedByDescending { it.timeMs }.mapNotNull { row ->
            val parsed = ParsedBankSms(
                amount = row.amount,
                type = if (row.kind == StatementRowKind.RECEIVED) TransactionType.INCOME else TransactionType.EXPENSE,
                counterparty = BankSmsParser.cleanCounterparty(row.counterparty),
                dayMs = null, // the row's exact time is used instead
                upiRef = row.upiRef,
                bank = row.bank,
                isConfident = true,
                isSelfTransfer = row.kind == StatementRowKind.SELF_TRANSFER,
                source = STATEMENT_SOURCE
            )
            val source = RawSms(id = 0, sender = STATEMENT_SOURCE, body = "", receivedAt = row.timeMs)
            val key = dedupeKey(source, parsed)
            when {
                key in alreadyImportedKeys -> {
                    alreadyImported += key
                    null
                }
                !seenKeys.add(key) -> null
                else -> {
                    // The credit into the owner's other account carries the same reference.
                    val otherSide = row.targetBank
                        ?.takeIf { row.kind == StatementRowKind.SELF_TRANSFER }
                        ?.let { "ref:${it.name}:${TransactionType.INCOME.name}:${row.upiRef}" }
                    Found(source, parsed, key, listOfNotNull(otherSide))
                }
            }
        }
        return buildPlan(
            allFound = allFound,
            alreadyImportedCount = alreadyImported.size,
            stats = ScanStats(statement.rows.size, statement.rows.size, statement.rows.size, emptyList()),
            alreadyImportedKeys = alreadyImportedKeys,
            existingTransactions = existingTransactions,
            rulesByKey = rules.associateBy { it.counterpartyKey },
            validCategories = validCategories,
            timeZone = timeZone,
            statement = StatementSummary(
                name = STATEMENT_SOURCE,
                period = statement.period,
                rows = statement.rows.size,
                selfTransfers = statement.rows.count { it.kind == StatementRowKind.SELF_TRANSFER },
                paidTotal = statement.paidTotal,
                receivedTotal = statement.receivedTotal,
                totalsMatch = statement.totalsMatch
            ),
            model = model
        )
    }

    const val STATEMENT_SOURCE = "Google Pay statement"

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
        return when {
            parsed.isReversal -> "Payment reversed ($bank)"
            parsed.type == TransactionType.INCOME -> "Money received ($bank)"
            else -> "Payment ($bank)"
        }
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
