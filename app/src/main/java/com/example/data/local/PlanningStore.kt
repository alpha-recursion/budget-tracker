package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.PaySettings
import com.example.domain.QuickAdd
import com.example.domain.QuickPin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Everything the owner sets for quick-add chips and payday, kept in SharedPreferences. */
data class PlanningPrefs(
    val pins: List<QuickPin> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val pay: PaySettings = PaySettings(),
    /** Start of the pay cycle whose salary card was dismissed with "Not yet". */
    val salaryDismissedCycle: Long = 0L
)

class PlanningStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("planning", Context.MODE_PRIVATE)

    fun read(): PlanningPrefs = PlanningPrefs(
        pins = QuickAdd.decodePins(prefs.getString(KEY_PINS, null)),
        hidden = prefs.getStringSet(KEY_HIDDEN, null)?.toSet().orEmpty(),
        pay = PaySettings(
            monthlyIncome = prefs.getString(KEY_INCOME, null)?.toDoubleOrNull() ?: 0.0,
            payday = prefs.getInt(KEY_PAYDAY, 0)
        ),
        salaryDismissedCycle = prefs.getLong(KEY_SALARY_DISMISSED, 0L)
    )

    val updates: Flow<PlanningPrefs> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    @Synchronized
    fun update(transform: (PlanningPrefs) -> PlanningPrefs) {
        val next = transform(read())
        prefs.edit()
            .putString(KEY_PINS, QuickAdd.encodePins(next.pins))
            .putStringSet(KEY_HIDDEN, next.hidden)
            .putString(KEY_INCOME, next.pay.monthlyIncome.toString())
            .putInt(KEY_PAYDAY, next.pay.payday)
            .putLong(KEY_SALARY_DISMISSED, next.salaryDismissedCycle)
            .apply()
    }

    private companion object {
        const val KEY_PINS = "quick_pins"
        const val KEY_HIDDEN = "quick_hidden"
        const val KEY_INCOME = "monthly_income"
        const val KEY_PAYDAY = "payday"
        const val KEY_SALARY_DISMISSED = "salary_dismissed_cycle"
    }
}
