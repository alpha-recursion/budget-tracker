package com.example.data.reminders

import android.content.Context
import android.content.SharedPreferences

/** Which phone reminders the owner has switched on. Read by background workers, so no Flows. */
class RemindersStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun dueDatesEnabled(): Boolean = prefs.getBoolean(KEY_DUE_DATES, true)

    fun setDueDatesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DUE_DATES, enabled).apply()
    }

    private companion object {
        const val KEY_DUE_DATES = "due_dates"
    }
}
