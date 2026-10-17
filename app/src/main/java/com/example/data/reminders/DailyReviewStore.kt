package com.example.data.reminders

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The days (see DailyStreaks.dayNumber) on which the owner confirmed their daily check-in. */
class DailyReviewStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("daily_review", Context.MODE_PRIVATE)

    val reviewedDays: Flow<Set<Long>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_DAYS) trySend(days())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(days())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun days(): Set<Long> =
        prefs.getStringSet(KEY_DAYS, null)?.mapNotNull { it.toLongOrNull() }?.toSet().orEmpty()

    fun markReviewed(day: Long) {
        // Copy first: the set returned by SharedPreferences must not be modified.
        val updated = (prefs.getStringSet(KEY_DAYS, null).orEmpty() + day.toString()).toSet()
        prefs.edit().putStringSet(KEY_DAYS, updated).apply()
    }

    /** Replaces every check-in day (backup restore; an empty set erases them). */
    fun replaceAll(days: Set<Long>) {
        prefs.edit().putStringSet(KEY_DAYS, days.map { it.toString() }.toSet()).apply()
    }

    private companion object {
        const val KEY_DAYS = "reviewed_days"
    }
}
