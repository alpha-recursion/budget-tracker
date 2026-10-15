package com.example.data.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.domain.DailyStreaks

/** Runs every evening (see [ReminderScheduler]); nudges only if today is not checked in yet. */
class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!RemindersStore(applicationContext).reviewEnabled()) return Result.success()
        val days = DailyReviewStore(applicationContext).days()
        val today = DailyStreaks.dayNumber(System.currentTimeMillis())
        if (today in days) return Result.success()
        ReminderNotifier.showReview(applicationContext, DailyStreaks.review(days, today).current)
        return Result.success()
    }
}
