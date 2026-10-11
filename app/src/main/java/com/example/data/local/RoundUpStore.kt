package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.RoundUpSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Round-up jar settings: a handful of numbers, kept in SharedPreferences. */
class RoundUpStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("round_ups", Context.MODE_PRIVATE)

    fun read(): RoundUpSettings = RoundUpSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        unit = prefs.getInt(KEY_UNIT, 10),
        startedAt = prefs.getLong(KEY_STARTED_AT, 0L),
        banked = prefs.getString(KEY_BANKED, null)?.toDoubleOrNull() ?: 0.0,
        moved = prefs.getString(KEY_MOVED, null)?.toDoubleOrNull() ?: 0.0
    )

    @Synchronized
    fun update(transform: (RoundUpSettings) -> RoundUpSettings): RoundUpSettings {
        val next = transform(read())
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putInt(KEY_UNIT, next.unit)
            .putLong(KEY_STARTED_AT, next.startedAt)
            .putString(KEY_BANKED, next.banked.toString())
            .putString(KEY_MOVED, next.moved.toString())
            .apply()
        return next
    }

    val settings: Flow<RoundUpSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_UNIT = "unit"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_BANKED = "banked"
        const val KEY_MOVED = "moved"
    }
}
