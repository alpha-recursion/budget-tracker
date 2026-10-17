package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * App lock: when on, Pennywise asks for the phone's own unlock (fingerprint, face or screen PIN)
 * when it opens or returns after a short time away, and hides its content from screenshots and
 * the recent-apps view.
 */
class AppLockStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    val enabled: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ENABLED) trySend(isEnabled())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(isEnabled())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** False when the phone has no screen lock (nothing to unlock with). */
    fun isAvailable(): Boolean =
        BiometricManager.from(appContext).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    companion object {
        private const val KEY_ENABLED = "enabled"

        /** Fingerprint / face, or the phone's PIN, pattern or password. */
        const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        /** Time in the background after which the app locks again. */
        const val RELOCK_AFTER_MS = 30_000L
    }
}
