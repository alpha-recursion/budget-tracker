package com.example.data.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.sms.AutoCaptureNotifier
import com.example.domain.DueReminder
import com.example.ui.util.Formatters

/** Phone notifications for reminders (separate from the bank payments notification). */
object ReminderNotifier {

    private const val DUE_CHANNEL_ID = "due_dates"
    private const val DUE_NOTIFICATION_ID = 1002
    private const val REVIEW_CHANNEL_ID = "daily_review"
    private const val REVIEW_NOTIFICATION_ID = 1003

    /** Intent extra value that opens the Money lent screen. */
    const val OPEN_LENDING = "lending"

    /** Intent extra value that opens Home, where the daily check-in card is. */
    const val OPEN_REVIEW = "review"

    fun showDue(context: Context, reminders: List<DueReminder>) {
        if (reminders.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context, DUE_CHANNEL_ID, "Due dates", "Money lent or borrowed that is due tomorrow")

        val lines = reminders.map { line(it) }
        val title = if (reminders.size == 1) "Due tomorrow" else "${reminders.size} payments due tomorrow"
        val text = lines.joinToString("\n")
        val notification = NotificationCompat.Builder(context, DUE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_payment)
            .setColor(ContextCompat.getColor(context, R.color.pennywise_accent))
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context, OPEN_LENDING, requestCode = 1))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            manager.notify(DUE_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing else to do.
        }
    }

    fun showReview(context: Context, streakDays: Int) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context, REVIEW_CHANNEL_ID, "Daily check-in", "Evening nudge to log the day's spending")
        val text = if (streakDays > 0) {
            "Confirm today to keep your $streakDays-day streak going."
        } else {
            "Add anything you missed, then confirm you've logged everything."
        }
        val notification = NotificationCompat.Builder(context, REVIEW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_payment)
            .setColor(ContextCompat.getColor(context, R.color.pennywise_accent))
            .setContentTitle("Log today's spending")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context, OPEN_REVIEW, requestCode = 2))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            manager.notify(REVIEW_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing else to do.
        }
    }

    fun cancelReview(context: Context) {
        NotificationManagerCompat.from(context).cancel(REVIEW_NOTIFICATION_ID)
    }

    private fun line(reminder: DueReminder): String {
        val amount = Formatters.formatCurrency(reminder.amount)
        return if (reminder.theyOweMe) "${reminder.person} should pay you $amount"
        else "You owe ${reminder.person} $amount"
    }

    private fun openApp(context: Context, open: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AutoCaptureNotifier.EXTRA_OPEN, open)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(context: Context, id: String, name: String, description: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(id) != null) return
        manager.createNotificationChannel(
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                this.description = description
            }
        )
    }
}
