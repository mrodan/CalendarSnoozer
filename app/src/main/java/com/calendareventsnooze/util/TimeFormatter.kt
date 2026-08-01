package com.calendareventsnooze.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

fun nextHourDefaultMs(): Long {
    return Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
