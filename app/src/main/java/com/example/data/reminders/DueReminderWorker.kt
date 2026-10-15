package com.example.data.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.PennywiseDatabase
import com.example.domain.DueReminders
import com.example.domain.FinancialCalculations
import com.example.domain.Lending

/** Runs daily (see [ReminderScheduler]) and notifies about money due tomorrow. */
class DueReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!RemindersStore(applicationContext).dueDatesEnabled()) return Result.success()
        return try {
            val now = System.currentTimeMillis()
            val entries = PennywiseDatabase.getDatabase(applicationContext).debtDao().getAllOnce()
            val balances = Lending.balances(entries, FinancialCalculations.startOfDay(now))
            ReminderNotifier.showDue(applicationContext, DueReminders.dueTomorrow(balances, now))
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
