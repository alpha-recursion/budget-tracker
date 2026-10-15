package com.example.data.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.PennywiseDatabase
import com.example.domain.DueReminders
import com.example.domain.FinancialCalculations
import com.example.domain.Lending
import com.example.domain.Recurring

/**
 * Runs daily at 10 AM (see [ReminderScheduler]): money due tomorrow, and subscriptions or bills
 * due in [Recurring.ALERT_DAYS] days. Each has its own switch in Settings.
 */
class DueReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = RemindersStore(applicationContext)
        return try {
            val now = System.currentTimeMillis()
            val database = PennywiseDatabase.getDatabase(applicationContext)
            if (settings.dueDatesEnabled()) {
                val balances = Lending.balances(database.debtDao().getAllOnce(), FinancialCalculations.startOfDay(now))
                ReminderNotifier.showDue(applicationContext, DueReminders.dueTomorrow(balances, now))
            }
            if (settings.recurringEnabled()) {
                ReminderNotifier.showRecurring(
                    applicationContext,
                    Recurring.dueForAlert(database.recurringDao().getAllOnce(), now)
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
