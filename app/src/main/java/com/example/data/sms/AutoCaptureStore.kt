package com.example.data.sms

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.TransactionType
import com.example.domain.sms.CaptureState
import com.example.domain.sms.CapturedPayment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The live-capture switch and what capture did since the app was last opened. Small and
 * written from a broadcast receiver, so it lives in SharedPreferences rather than the database.
 */
class AutoCaptureStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun state(): CaptureState = prefs.readState()

    /** Emits the switch and the capture state now and on every change. */
    val updates: Flow<Pair<Boolean, CaptureState>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, _ ->
            trySend(p.getBoolean(KEY_ENABLED, false) to p.readState())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(enabled to state())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /**
     * Read-modify-write of the state. Serialised because SMS broadcasts and the app can update
     * it at the same time; committed synchronously since a receiver's process may end right after.
     */
    suspend fun update(transform: (CaptureState) -> CaptureState): CaptureState = withContext(Dispatchers.IO) {
        lock.withLock {
            val before = prefs.readState()
            val after = transform(before)
            if (after != before) prefs.edit().writeState(after).commit()
            after
        }
    }

    private fun SharedPreferences.readState(): CaptureState {
        val latestTitle = getString(KEY_LATEST_TITLE, null)
        return CaptureState(
            pendingKeys = getStringSet(KEY_PENDING_KEYS, null)?.toSet().orEmpty(),
            pendingSinceMs = getLong(KEY_PENDING_SINCE, NONE).takeIf { it != NONE },
            lastPendingAt = getLong(KEY_LAST_PENDING_AT, NONE).takeIf { it != NONE },
            addedCount = getInt(KEY_ADDED_COUNT, 0),
            addedTotal = getString(KEY_ADDED_TOTAL, null)?.toDoubleOrNull() ?: 0.0,
            latest = latestTitle?.let {
                CapturedPayment(
                    title = it,
                    amount = getString(KEY_LATEST_AMOUNT, null)?.toDoubleOrNull() ?: 0.0,
                    type = if (getString(KEY_LATEST_TYPE, null) == TransactionType.INCOME.name) TransactionType.INCOME else TransactionType.EXPENSE,
                    addedAs = getString(KEY_LATEST_ADDED_AS, null)
                )
            }
        )
    }

    private fun SharedPreferences.Editor.writeState(state: CaptureState): SharedPreferences.Editor {
        putStringSet(KEY_PENDING_KEYS, state.pendingKeys)
        putLong(KEY_PENDING_SINCE, state.pendingSinceMs ?: NONE)
        putLong(KEY_LAST_PENDING_AT, state.lastPendingAt ?: NONE)
        putInt(KEY_ADDED_COUNT, state.addedCount)
        putString(KEY_ADDED_TOTAL, state.addedTotal.toString())
        val latest = state.latest
        if (latest == null) {
            remove(KEY_LATEST_TITLE).remove(KEY_LATEST_AMOUNT).remove(KEY_LATEST_TYPE).remove(KEY_LATEST_ADDED_AS)
        } else {
            putString(KEY_LATEST_TITLE, latest.title)
            putString(KEY_LATEST_AMOUNT, latest.amount.toString())
            putString(KEY_LATEST_TYPE, latest.type.name)
            putString(KEY_LATEST_ADDED_AS, latest.addedAs)
        }
        return this
    }

    private companion object {
        val lock = Mutex()
        const val NONE = -1L
        const val PREFS_NAME = "auto_capture"
        const val KEY_ENABLED = "enabled"
        const val KEY_PENDING_KEYS = "pending_keys"
        const val KEY_PENDING_SINCE = "pending_since"
        const val KEY_LAST_PENDING_AT = "last_pending_at"
        const val KEY_ADDED_COUNT = "added_count"
        const val KEY_ADDED_TOTAL = "added_total"
        const val KEY_LATEST_TITLE = "latest_title"
        const val KEY_LATEST_AMOUNT = "latest_amount"
        const val KEY_LATEST_TYPE = "latest_type"
        const val KEY_LATEST_ADDED_AS = "latest_added_as"
    }
}
