package com.calendareventsnooze.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun formatEventTime(eventTimeMs: Long): String? {
    if (eventTimeMs <= 0L) return null
    val eventCal = Calendar.getInstance().apply { timeInMillis = eventTimeMs }
    val today    = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

    fun isSameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(eventTimeMs))
    return when {
        isSameDay(eventCal, today)    -> "Today at $timeStr"
        isSameDay(eventCal, tomorrow) -> "Tomorrow at $timeStr"
        else -> SimpleDateFormat("EEE MMM d 'at' h:mm a", Locale.getDefault())
                    .format(Date(eventTimeMs))
    }
}

fun formatScheduledTime(scheduledTimeMs: Long): String =
    formatEventTime(scheduledTimeMs) ?: "Unknown time"

/**
 * Combines a date coming from a Material 3 [androidx.compose.material3.DatePicker]
 * with a wall-clock hour/minute, producing a local-time timestamp.
 *
 * CRITICAL (fixes the "snoozes to the previous day" bug): `selectedDateMillis`
 * is **UTC midnight** of the chosen day. Reading it with a default (local)
 * Calendar shifts the calendar day backwards for every timezone behind UTC —
 * e.g. UTC midnight Aug 5 is Aug 4, 8:00 PM in UTC-4. The year/month/day must
 * therefore be extracted in UTC, and only then applied to a local calendar.
 */
fun combineDateAndTime(datePickerUtcMillis: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = datePickerUtcMillis
    }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

fun nextHourDefaultMs(): Long {
    return Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
