package com.example.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.domain.sms.RawSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads received SMS from the phone's inbox. Requires the READ_SMS permission. */
class SmsInboxReader(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Inbox messages received at or after [sinceMs], newest first. */
    suspend fun readSince(sinceMs: Long): List<RawSms> = withContext(Dispatchers.IO) {
        val messages = ArrayList<RawSms>()
        forEachMessage("${Telephony.Sms.DATE} >= ?", sinceMs) { messages += it; true }
        messages
    }

    /**
     * Newest message received before [beforeMs] that satisfies [predicate], looking at no more
     * than [limit] messages. Used to tell the user when their latest bank SMS actually was.
     */
    suspend fun findNewestBefore(beforeMs: Long, limit: Int, predicate: (RawSms) -> Boolean): RawSms? =
        withContext(Dispatchers.IO) {
            var result: RawSms? = null
            var seen = 0
            forEachMessage("${Telephony.Sms.DATE} < ?", beforeMs) { sms ->
                seen++
                if (predicate(sms)) result = sms
                result == null && seen < limit
            }
            result
        }

    /** Iterates inbox messages newest first; [onMessage] returns false to stop early. */
    private fun forEachMessage(selection: String, dateArg: Long, onMessage: (RawSms) -> Boolean) {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            arrayOf(dateArg.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                val address = cursor.getString(addressCol) ?: continue
                val body = cursor.getString(bodyCol) ?: continue
                val keepGoing = onMessage(
                    RawSms(
                        id = cursor.getLong(idCol),
                        sender = address,
                        body = body,
                        receivedAt = cursor.getLong(dateCol)
                    )
                )
                if (!keepGoing) break
            }
        }
    }
}
