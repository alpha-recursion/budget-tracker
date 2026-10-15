package com.example.data.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Schedules the daily background checks. Safe to call on every app start. */
object ReminderScheduler {

    private const val DUE_WORK = "due_date_reminders"
    private const val DUE_HOUR = 10

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DueReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(DUE_HOUR, 0), TimeUnit.MILLISECONDS)
            .build()
        // KEEP: the first run time stays at the next 10:00 even if the app opens again before it.
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(DUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Milliseconds from now to the next [hour]:[minute] local time. */
    internal fun delayUntil(hour: Int, minute: Int, nowMs: Long = System.currentTimeMillis()): Long {
        val next = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMs) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - nowMs
    }
}
