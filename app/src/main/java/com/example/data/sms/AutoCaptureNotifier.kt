package com.example.data.sms

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
import com.example.domain.sms.CaptureState
import com.example.ui.util.Formatters

/** The single, updating "bank payments" notification. */
object AutoCaptureNotifier {

    private const val CHANNEL_ID = "bank_payments"
    private const val NOTIFICATION_ID = 1001

    /** Intent extra telling [MainActivity] what to open. */
    const val EXTRA_OPEN = "com.example.extra.OPEN"
    const val OPEN_SMS_REVIEW = "sms_review"
    const val OPEN_TRANSACTIONS = "transactions"

    fun show(context: Context, state: CaptureState) {
        if (!state.hasNews) {
            cancel(context)
            return
        }
        val manager = NotificationManagerCompat.from(context)
        // Without the notification permission (Android 13+) the Home card still shows the news.
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)

        val (title, text) = texts(state)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN, if (state.pendingCount > 0) OPEN_SMS_REVIEW else OPEN_TRANSACTIONS)
        }
        val tap = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_payment)
            .setColor(ContextCompat.getColor(context, R.color.pennywise_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing else to do.
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun texts(state: CaptureState): Pair<String, String> {
        val latest = state.latest
        val latestLine = latest?.let { "${Formatters.formatCurrency(it.amount)} · ${it.title}" }
        return if (state.pendingCount > 0) {
            val title = "${state.pendingCount} bank payment${plural(state.pendingCount)} to review"
            val parts = listOfNotNull(
                latestLine?.takeIf { latest.addedAs == null }?.let { "Latest: $it" },
                if (state.addedCount > 0) "${state.addedCount} added automatically" else null,
                "Tap to review"
            )
            title to parts.joinToString(" · ")
        } else if (state.addedCount == 1 && latest?.addedAs != null) {
            "Added $latestLine" to "As ${latest.addedAs}. Tap to see it."
        } else {
            val title = "Added ${state.addedCount} payment${plural(state.addedCount)} · ${Formatters.formatCurrency(state.addedTotal)}"
            title to (latestLine?.let { "Latest: $it" + (latest.addedAs?.let { c -> " ($c)" } ?: "") } ?: "Tap to see them.")
        }
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Bank payments", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Payments picked up from Kotak and BOI SMS"
            }
        )
    }
}
