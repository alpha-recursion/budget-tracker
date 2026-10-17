package com.example.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.local.PennywiseDatabase
import com.example.data.repository.PennywiseRepository
import com.example.data.repository.SmsImportItem
import com.example.domain.sms.AutoCapture
import com.example.domain.sms.RawSms
import com.example.domain.sms.SmsImportPlanner
import com.example.domain.sms.toTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Live capture: runs when an SMS arrives, even with the app closed. Payments to payees whose
 * category is remembered are added straight away; the rest wait for review. Either way one
 * notification sums it up.
 */
class BankSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val appContext = context.applicationContext
        val store = AutoCaptureStore(appContext)
        if (!store.enabled) return

        val arrivedAt = System.currentTimeMillis()
        val messages = readMessages(intent, arrivedAt)
        // Cheap filter before touching the database: banks send from alphanumeric headers.
        if (messages.none { SmsImportPlanner.isBankSender(it.sender) }) return

        val pending = goAsync()
        scope.launch {
            try {
                process(appContext, store, messages, arrivedAt)
            } catch (e: Exception) {
                // Never crash on an SMS; the message is still in the inbox for a manual scan.
                Log.w(TAG, "Live capture failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun process(context: Context, store: AutoCaptureStore, messages: List<RawSms>, arrivedAt: Long) {
        val repository = PennywiseRepository(PennywiseDatabase.getDatabase(context))
        val templates = repository.smsTemplatesOnce()
        val plan = SmsImportPlanner.plan(
            messages = messages,
            alreadyImportedKeys = repository.smsImportKeys(),
            existingTransactions = repository.handEnteredTransactions(),
            rules = repository.merchantRules(),
            validCategories = repository.categoryNames(),
            templates = templates
        )
        if (plan.candidateCount == 0) return

        val decision = AutoCapture.decide(plan)
        if (decision.autoAdd.isNotEmpty()) {
            repository.importFromSms(
                items = decision.autoAdd.map {
                    val cancelsKey = it.candidate.cancelsKey
                    SmsImportItem(
                        dedupeKey = it.candidate.dedupeKey,
                        receivedAt = it.candidate.receivedAt,
                        transaction = if (it.isSelfTransfer || cancelsKey != null) null else it.candidate.toTransaction(it.category),
                        cancelsKey = cancelsKey
                    )
                },
                rules = emptyList()
            )
        }
        val state = store.update { it.record(decision, arrivedAt, arrivedAt - AutoCapture.SCAN_MARGIN_MS) }
        AutoCaptureNotifier.show(context, state)
    }

    /** One message per sender; long SMS arrive in several parts that are joined back together. */
    private fun readMessages(intent: Intent, arrivedAt: Long): List<RawSms> {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        return parts
            .groupBy { it.displayOriginatingAddress ?: it.originatingAddress }
            .mapNotNull { (sender, pieces) ->
                if (sender.isNullOrBlank()) return@mapNotNull null
                RawSms(
                    id = 0,
                    sender = sender,
                    body = pieces.joinToString("") { it.displayMessageBody.orEmpty() },
                    receivedAt = arrivedAt
                )
            }
    }

    private companion object {
        const val TAG = "BankSmsReceiver"
        // Outlives a single broadcast; each run is bounded by goAsync's time limit.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
