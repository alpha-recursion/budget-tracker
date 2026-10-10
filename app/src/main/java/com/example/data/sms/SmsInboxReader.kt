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
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val messages = ArrayList<RawSms>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMs.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                val address = cursor.getString(addressCol) ?: continue
                val body = cursor.getString(bodyCol) ?: continue
                messages += RawSms(
                    id = cursor.getLong(idCol),
                    sender = address,
                    body = body,
                    receivedAt = cursor.getLong(dateCol)
                )
            }
        }
        messages
    }
}
