package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DebtEntryEntity
import com.example.data.local.MerchantRuleEntity
import com.example.data.local.PennywiseDatabase
import com.example.data.model.TransactionType
import com.example.data.repository.PennywiseRepository
import com.example.data.repository.SmsImportItem
import com.example.data.sms.AutoCaptureStore
import com.example.data.sms.SmsInboxReader
import com.example.data.statement.PdfTextExtractor
import com.example.domain.statement.GPayStatementParser
import com.example.domain.statement.StatementFormatException
import android.net.Uri
import com.example.domain.DebtKind
import com.example.domain.Lending
import com.example.domain.sms.ImportCandidate
import com.example.domain.sms.ImportGroup
import com.example.domain.sms.ImportPlan
import com.example.domain.sms.SmsImportPlanner
import com.example.domain.sms.toTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SmsImportPhase { CHOOSE_PERIOD, SCANNING, REVIEW, IMPORTING }

/** A payee group marked as money lent / repaid: recorded under [person], not as spending. */
data class LendingChoice(val person: String, val kind: DebtKind)

data class SmsImportState(
    val phase: SmsImportPhase = SmsImportPhase.CHOOSE_PERIOD,
    val plan: ImportPlan? = null,
    /** Dedupe keys of ticked items. */
    val selected: Set<String> = emptySet(),
    val categoryByGroup: Map<String, String> = emptyMap(),
    val selfTransferGroups: Set<String> = emptySet(),
    /** Groups recorded in Money lent instead of as transactions. */
    val lendingByGroup: Map<String, LendingChoice> = emptyMap(),
    val error: String? = null,
    /** When nothing new was found: arrival time of the newest Kotak/BOI alert before the period. */
    val newestOlderAlertAt: Long? = null,
    /** Start of the last scan, so it can be repeated after teaching a new format. */
    val lastSinceMs: Long? = null,
    /** When the last scan read the inbox: messages arriving later were not part of it. */
    val scannedAtMs: Long? = null,
    /** The review comes from a statement file, not the SMS inbox. */
    val fromStatement: Boolean = false
) {
    private fun ImportGroup.selectedItems(): List<ImportCandidate> = items.filter { it.dedupeKey in selected }

    val selectedCount: Int get() = selected.size

    private fun ImportGroup.isCounted(): Boolean =
        key !in selfTransferGroups && key !in lendingByGroup && !cancelsPayment

    /** Totals of what will actually be counted (self transfers and lending excluded). */
    val selectedSpent: Double
        get() = plan?.groups.orEmpty()
            .filter { it.type == TransactionType.EXPENSE && it.isCounted() }
            .sumOf { group -> group.selectedItems().sumOf { it.parsed.amount } }

    val selectedReceived: Double
        get() = plan?.groups.orEmpty()
            .filter { it.type == TransactionType.INCOME && it.isCounted() }
            .sumOf { group -> group.selectedItems().sumOf { it.parsed.amount } }

    /** Ticked money going to Money lent (either direction). */
    val selectedLending: Double
        get() = plan?.groups.orEmpty()
            .filter { it.key in lendingByGroup }
            .sumOf { group -> group.selectedItems().sumOf { it.parsed.amount } }
}

class SmsImportViewModel(
    application: Application,
    private val repository: PennywiseRepository = PennywiseRepository(PennywiseDatabase.getDatabase(application)),
    private val reader: SmsInboxReader = SmsInboxReader(application),
    private val captureStore: AutoCaptureStore = AutoCaptureStore(application),
    private val pdfExtractor: PdfTextExtractor = PdfTextExtractor(application)
) : AndroidViewModel(application) {

    companion object {
        /** How many older messages to look through for the newest bank alert. */
        private const val OLDER_SEARCH_LIMIT = 5000

        /** Review window when nothing is waiting (e.g. the notification was stale). */
        private const val PENDING_FALLBACK_MS = 24 * 60 * 60 * 1000L

        fun Factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SmsImportViewModel(application) as T
                }
            }
    }

    private val _state = MutableStateFlow(SmsImportState())
    val state: StateFlow<SmsImportState> = _state

    fun hasPermission(): Boolean = reader.hasPermission()

    /** Scans messages received since [sinceMs]; [validCategories] are the names currently offered. */
    fun scan(sinceMs: Long, validCategories: Set<String>) {
        if (_state.value.phase == SmsImportPhase.SCANNING) return
        val scannedAt = System.currentTimeMillis()
        _state.value = SmsImportState(phase = SmsImportPhase.SCANNING, lastSinceMs = sinceMs)
        viewModelScope.launch {
            try {
                val messages = reader.readSince(sinceMs)
                val templates = repository.smsTemplatesOnce()
                val plan = withContext(Dispatchers.Default) {
                    SmsImportPlanner.plan(
                        messages = messages,
                        alreadyImportedKeys = repository.smsImportKeys(),
                        existingTransactions = repository.handEnteredTransactions(),
                        rules = repository.merchantRules(),
                        validCategories = validCategories,
                        templates = templates
                    )
                }
                // Nothing in the period: find when the latest bank alert actually arrived, so the
                // user can widen the scan instead of seeing an unexplained empty result.
                val newestOlder = if (plan.stats.recognised == 0) {
                    reader.findNewestBefore(sinceMs, limit = OLDER_SEARCH_LIMIT) {
                        SmsImportPlanner.isSupportedTransaction(it, templates = templates)
                    }?.receivedAt
                } else {
                    null
                }
                _state.value = SmsImportState(
                    phase = SmsImportPhase.REVIEW,
                    plan = plan,
                    selected = plan.groups.flatMap { g -> g.items.filter { it.selectedByDefault }.map { it.dedupeKey } }.toSet(),
                    categoryByGroup = plan.groups.associate { it.key to it.suggestedCategory },
                    selfTransferGroups = plan.groups.filter { it.suggestedSelfTransfer }.map { it.key }.toSet(),
                    newestOlderAlertAt = newestOlder,
                    lastSinceMs = sinceMs,
                    scannedAtMs = scannedAt
                )
                // Nothing left to review (e.g. imported meanwhile): drop the "to review" note.
                if (plan.candidateCount == 0) captureStore.update { it.afterReview(sinceMs, scannedAt) }
            } catch (e: SecurityException) {
                _state.value = SmsImportState(error = "SMS access was not granted.")
            } catch (e: Exception) {
                _state.value = SmsImportState(error = "Could not read SMS: ${e.message ?: "unknown error"}")
            }
        }
    }

    /**
     * Reads a Google Pay statement PDF and shows its transactions in the same review as an SMS
     * scan. Needs no SMS permission.
     */
    fun importStatement(uri: Uri, validCategories: Set<String>) {
        if (_state.value.phase == SmsImportPhase.SCANNING || _state.value.phase == SmsImportPhase.IMPORTING) return
        _state.value = SmsImportState(phase = SmsImportPhase.SCANNING, fromStatement = true)
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { pdfExtractor.extract(uri) }
                val plan = withContext(Dispatchers.Default) {
                    SmsImportPlanner.planStatement(
                        statement = GPayStatementParser.parse(text),
                        alreadyImportedKeys = repository.smsImportKeys(),
                        existingTransactions = repository.handEnteredTransactions(),
                        rules = repository.merchantRules(),
                        validCategories = validCategories
                    )
                }
                _state.value = SmsImportState(
                    phase = SmsImportPhase.REVIEW,
                    plan = plan,
                    selected = plan.groups.flatMap { g -> g.items.filter { it.selectedByDefault }.map { it.dedupeKey } }.toSet(),
                    categoryByGroup = plan.groups.associate { it.key to it.suggestedCategory },
                    selfTransferGroups = plan.groups.filter { it.suggestedSelfTransfer }.map { it.key }.toSet(),
                    fromStatement = true
                )
            } catch (e: StatementFormatException) {
                _state.value = SmsImportState(error = e.message)
            } catch (e: Exception) {
                _state.value = SmsImportState(error = "Couldn't read the statement: ${e.message ?: "unknown error"}")
            }
        }
    }

    /** Scans from the oldest payment live capture left for review (notification or Home card). */
    fun reviewPending() {
        viewModelScope.launch {
            val since = captureStore.state().pendingSinceMs
                ?: (System.currentTimeMillis() - PENDING_FALLBACK_MS)
            scan(since, repository.categoryNames())
        }
    }

    /** Repeats the last scan (e.g. after a new format was taught); no-op if none ran yet. */
    fun rescan(validCategories: Set<String>) {
        val since = _state.value.lastSinceMs ?: return
        if (_state.value.phase == SmsImportPhase.IMPORTING) return
        scan(since, validCategories)
    }

    fun toggleItem(dedupeKey: String) = _state.update { s ->
        s.copy(selected = if (dedupeKey in s.selected) s.selected - dedupeKey else s.selected + dedupeKey)
    }

    /** Ticks every item in the group, or unticks them all if all were ticked. */
    fun toggleGroup(group: ImportGroup) = _state.update { s ->
        val keys = group.items.map { it.dedupeKey }
        s.copy(selected = if (s.selected.containsAll(keys)) s.selected - keys.toSet() else s.selected + keys)
    }

    fun setGroupCategory(groupKey: String, category: String) = _state.update { s ->
        s.copy(categoryByGroup = s.categoryByGroup + (groupKey to category))
    }

    fun setGroupSelfTransfer(groupKey: String, isSelfTransfer: Boolean) = _state.update { s ->
        s.copy(
            selfTransferGroups = if (isSelfTransfer) s.selfTransferGroups + groupKey else s.selfTransferGroups - groupKey,
            lendingByGroup = if (isSelfTransfer) s.lendingByGroup - groupKey else s.lendingByGroup
        )
    }

    /** Marks a group as money lent / repaid ([choice]), or back to normal spending (null). */
    fun setGroupLending(groupKey: String, choice: LendingChoice?) = _state.update { s ->
        s.copy(
            lendingByGroup = if (choice == null) s.lendingByGroup - groupKey else s.lendingByGroup + (groupKey to choice),
            selfTransferGroups = if (choice != null) s.selfTransferGroups - groupKey else s.selfTransferGroups
        )
    }

    /** Imports the ticked items; [onDone] receives a summary message for the user. */
    fun importSelected(onDone: (String) -> Unit) {
        val current = _state.value
        val plan = current.plan ?: return
        if (current.phase != SmsImportPhase.REVIEW || current.selected.isEmpty()) return
        _state.update { it.copy(phase = SmsImportPhase.IMPORTING) }

        val items = ArrayList<SmsImportItem>()
        val rules = ArrayList<MerchantRuleEntity>()
        for (group in plan.groups) {
            val chosen = group.items.filter { it.dedupeKey in current.selected }
            if (chosen.isEmpty()) continue
            if (group.cancelsPayment) {
                chosen.forEach { item ->
                    items += SmsImportItem(
                        dedupeKey = item.dedupeKey,
                        receivedAt = item.receivedAt,
                        transaction = null,
                        cancelsKey = item.cancelsKey
                    )
                }
                continue
            }
            val lending = current.lendingByGroup[group.key]
            if (lending != null) {
                // Lending is a one-off decision: the payee's remembered category is left as is.
                val name = Lending.displayName(lending.person)
                chosen.forEach { item ->
                    items += SmsImportItem(
                        dedupeKey = item.dedupeKey,
                        receivedAt = item.receivedAt,
                        transaction = null,
                        debt = DebtEntryEntity(
                            person = name,
                            personKey = Lending.personKey(name),
                            kind = lending.kind.name,
                            amount = item.parsed.amount,
                            date = item.dateMs,
                            note = item.toTransaction("").note
                        )
                    )
                }
                continue
            }
            val isSelfTransfer = group.key in current.selfTransferGroups
            val category = current.categoryByGroup[group.key] ?: group.suggestedCategory
            chosen.forEach { item ->
                items += SmsImportItem(
                    dedupeKey = item.dedupeKey,
                    receivedAt = item.receivedAt,
                    transaction = if (isSelfTransfer) null else item.toTransaction(category),
                    // Only a real self transfer has a credit side in the owner's other account.
                    extraKeys = if (isSelfTransfer) item.extraKeys else emptyList()
                )
            }
            group.ruleKey?.let { rules += MerchantRuleEntity(it, category, isSelfTransfer) }
        }

        viewModelScope.launch {
            try {
                val created = repository.importFromSms(items, rules)
                val selfTransfers = items.count { it.transaction == null && it.debt == null && it.cancelsKey == null }
                val lent = items.count { it.debt != null }
                val reversed = items.count { it.cancelsKey != null }
                val from = current.lastSinceMs
                val at = current.scannedAtMs
                if (from != null && at != null) captureStore.update { it.afterReview(from, at) }
                _state.value = SmsImportState()
                onDone(
                    buildString {
                        append("Imported $created transaction${if (created == 1) "" else "s"}")
                        if (lent > 0) append(", $lent to money lent")
                        if (selfTransfers > 0) append(", $selfTransfers self transfer${if (selfTransfers == 1) "" else "s"} skipped")
                        if (reversed > 0) append(", $reversed reversed payment${if (reversed == 1) "" else "s"} removed")
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(phase = SmsImportPhase.REVIEW, error = "Import failed, nothing was changed.") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Discards any scan in progress or under review. */
    fun reset() {
        if (_state.value.phase != SmsImportPhase.IMPORTING) _state.value = SmsImportState()
    }
}
