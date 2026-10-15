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
    private const val REVIEW_WORK = "daily_review_reminder"
    private const val DUE_HOUR = 10
    private const val REVIEW_HOUR = 21

    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        // KEEP: the first run time stays put even if the app opens again before it.
        manager.enqueueUniquePeriodicWork(
            DUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DueReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(DUE_HOUR, 0), TimeUnit.MILLISECONDS)
                .build()
        )
        manager.enqueueUniquePeriodicWork(
            REVIEW_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReviewReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(REVIEW_HOUR, 0), TimeUnit.MILLISECONDS)
                .build()
        )
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
