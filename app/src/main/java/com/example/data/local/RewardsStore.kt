package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Small reward settings: whether celebrations play a sound and vibrate, which badges the owner
 * has already been shown, and the last level celebrated.
 */
class RewardsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("rewards", Context.MODE_PRIVATE)

    val feedbackEnabled: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == KEY_FEEDBACK) trySend(p.getBoolean(KEY_FEEDBACK, true))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getBoolean(KEY_FEEDBACK, true))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun isFeedbackEnabled(): Boolean = prefs.getBoolean(KEY_FEEDBACK, true)

    fun setFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FEEDBACK, enabled).apply()
    }

    /** Badge names already celebrated; only ever grows. */
    fun seenBadges(): Set<String> = prefs.getStringSet(KEY_SEEN_BADGES, null)?.toSet().orEmpty()

    fun markBadgesSeen(names: Collection<String>) {
        prefs.edit().putStringSet(KEY_SEEN_BADGES, seenBadges() + names).apply()
    }

    /** Highest level celebrated so far (1 = none). */
    fun lastLevel(): Int = prefs.getInt(KEY_LAST_LEVEL, 1)

    fun setLastLevel(level: Int) {
        prefs.edit().putInt(KEY_LAST_LEVEL, level).apply()
    }

    /** Replaces the celebration history (backup restore or erase); the sound switch is kept. */
    fun replaceProgress(seenBadges: Set<String>, lastLevel: Int) {
        prefs.edit().putStringSet(KEY_SEEN_BADGES, seenBadges).putInt(KEY_LAST_LEVEL, lastLevel).apply()
    }

    private companion object {
        const val KEY_FEEDBACK = "celebration_feedback"
        const val KEY_SEEN_BADGES = "seen_badges"
        const val KEY_LAST_LEVEL = "last_level"
    }
}
