package com.example.ui.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Material3's DatePicker works with UTC-midnight timestamps, while transactions store local
 * wall-clock timestamps. Converting between the two keeps the picker from showing (or saving)
 * the neighbouring day in time zones away from UTC.
 */
object DateConversions {

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    /** Local timestamp -> UTC midnight of the same calendar day (for DatePicker state). */
    fun localToUtcDay(localMs: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
        val local = Calendar.getInstance(timeZone).apply { timeInMillis = localMs }
        return Calendar.getInstance(UTC).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    /**
     * UTC midnight picked in the DatePicker -> local timestamp on that calendar day, keeping the
     * time of day from [keepTimeOfMs] so ordering among same-day transactions stays sensible.
     */
    fun utcDayToLocal(
        utcDayMs: Long,
        keepTimeOfMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val picked = Calendar.getInstance(UTC).apply { timeInMillis = utcDayMs }
        val time = Calendar.getInstance(timeZone).apply { timeInMillis = keepTimeOfMs }
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(
                picked.get(Calendar.YEAR),
                picked.get(Calendar.MONTH),
                picked.get(Calendar.DAY_OF_MONTH),
                time.get(Calendar.HOUR_OF_DAY),
                time.get(Calendar.MINUTE),
                time.get(Calendar.SECOND)
            )
        }.timeInMillis
    }
}
