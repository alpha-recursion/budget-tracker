package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.MerchantRuleEntity
import com.example.data.local.PennywiseDatabase
import com.example.data.local.TransactionEntity
import com.example.data.model.TransactionType
import com.example.data.repository.PennywiseRepository
import com.example.data.repository.SmsImportItem
import com.example.data.sms.SmsInboxReader
import com.example.domain.sms.ImportCandidate
import com.example.domain.sms.ImportGroup
import com.example.domain.sms.ImportPlan
import com.example.domain.sms.SmsBank
import com.example.domain.sms.SmsImportPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SmsImportPhase { CHOOSE_PERIOD, SCANNING, REVIEW, IMPORTING }

data class SmsImportState(
    val phase: SmsImportPhase = SmsImportPhase.CHOOSE_PERIOD,
    val plan: ImportPlan? = null,
    /** Dedupe keys of ticked items. */
    val selected: Set<String> = emptySet(),
    val categoryByGroup: Map<String, String> = emptyMap(),
    val selfTransferGroups: Set<String> = emptySet(),
    val error: String? = null
) {
    private fun ImportGroup.selectedItems(): List<ImportCandidate> = items.filter { it.dedupeKey in selected }

    val selectedCount: Int get() = selected.size

    /** Totals of what will actually be counted (self transfers excluded). */
    val selectedSpent: Double
        get() = plan?.groups.orEmpty()
            .filter { it.type == TransactionType.EXPENSE && it.key !in selfTransferGroups }
            .sumOf { group -> group.selectedItems().sumOf { it.parsed.amount } }

    val selectedReceived: Double
        get() = plan?.groups.orEmpty()
            .filter { it.type == TransactionType.INCOME && it.key !in selfTransferGroups }
            .sumOf { group -> group.selectedItems().sumOf { it.parsed.amount } }
}

class SmsImportViewModel(
    application: Application,
    private val repository: PennywiseRepository = PennywiseRepository(PennywiseDatabase.getDatabase(application)),
    private val reader: SmsInboxReader = SmsInboxReader(application)
) : AndroidViewModel(application) {

    companion object {
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
        _state.value = SmsImportState(phase = SmsImportPhase.SCANNING)
        viewModelScope.launch {
            try {
                val messages = reader.readSince(sinceMs)
                val plan = withContext(Dispatchers.Default) {
                    SmsImportPlanner.plan(
                        messages = messages,
                        alreadyImportedKeys = repository.smsImportKeys(),
                        existingTransactions = repository.transactionsOnce(),
                        rules = repository.merchantRules(),
                        validCategories = validCategories
                    )
                }
                _state.value = SmsImportState(
                    phase = SmsImportPhase.REVIEW,
                    plan = plan,
                    selected = plan.groups.flatMap { g -> g.items.filter { it.selectedByDefault }.map { it.dedupeKey } }.toSet(),
                    categoryByGroup = plan.groups.associate { it.key to it.suggestedCategory },
                    selfTransferGroups = plan.groups.filter { it.suggestedSelfTransfer }.map { it.key }.toSet()
                )
            } catch (e: SecurityException) {
                _state.value = SmsImportState(error = "SMS access was not granted.")
            } catch (e: Exception) {
                _state.value = SmsImportState(error = "Could not read SMS: ${e.message ?: "unknown error"}")
            }
        }
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
        s.copy(selfTransferGroups = if (isSelfTransfer) s.selfTransferGroups + groupKey else s.selfTransferGroups - groupKey)
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
            val isSelfTransfer = group.key in current.selfTransferGroups
            val category = current.categoryByGroup[group.key] ?: group.suggestedCategory
            chosen.forEach { item ->
                items += SmsImportItem(
                    dedupeKey = item.dedupeKey,
                    receivedAt = item.receivedAt,
                    transaction = if (isSelfTransfer) null else item.toTransaction(category)
                )
            }
            group.ruleKey?.let { rules += MerchantRuleEntity(it, category, isSelfTransfer) }
        }

        viewModelScope.launch {
            try {
                val created = repository.importFromSms(items, rules)
                val selfTransfers = items.size - created
                _state.value = SmsImportState()
                onDone(
                    buildString {
                        append("Imported $created transaction${if (created == 1) "" else "s"}")
                        if (selfTransfers > 0) append(", $selfTransfers self transfer${if (selfTransfers == 1) "" else "s"} skipped")
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

    private fun ImportCandidate.toTransaction(category: String): TransactionEntity {
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
            note = "From $bank SMS" + (parsed.upiRef?.let { " · UPI Ref $it" } ?: "")
        )
    }
}
