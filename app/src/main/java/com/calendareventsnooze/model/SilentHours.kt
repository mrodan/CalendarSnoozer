package com.calendareventsnooze.model

import java.util.Calendar

/**
 * One recurring quiet window: a set of days and a time range.
 *
 * A range whose end precedes its start wraps past midnight, and is matched
 * against the day it **started** — a Friday 22:00–07:00 window covers Saturday
 * 01:00, which is what someone picking "Friday night" means. That is also why
 * the weekday window can legitimately silence a Saturday morning.
 */
data class SilentWindow(
    val days: Set<Int>,
    val startMinute: Int,
    val endMinute: Int
) {
    fun isSilentAt(day: Int, minuteOfDay: Int): Boolean {
        if (days.isEmpty() || startMinute == endMinute) return false
        return if (startMinute < endMinute) {
            day in days && minuteOfDay >= startMinute && minuteOfDay < endMinute
        } else {
            (day in days && minuteOfDay >= startMinute) ||
                (previousDay(day) in days && minuteOfDay < endMinute)
        }
    }

    private fun previousDay(day: Int): Int =
        if (day == Calendar.SUNDAY) Calendar.SATURDAY else day - 1
}

/**
 * Round 23 — quiet time is now specified separately for weekdays and weekends,
 * because those are the two schedules people actually keep. Each half owns its
 * own days *and* its own hours, so "10pm–7am on work nights, 1am–10am at the
 * weekend" is expressible; before, one window had to serve both.
 *
 * This suppresses *interception* only: a reminder arriving inside a window
 * behaves the way it did before the app was installed. Alarms already snoozed
 * still fire — those were scheduled deliberately, and losing one because it
 * landed in the quiet window would be the surprise this app exists to prevent.
 */
data class SilentHours(
    val enabled: Boolean = false,
    val weekdays: SilentWindow = SilentWindow(WEEKDAYS, 22 * 60, 7 * 60),
    val weekends: SilentWindow = SilentWindow(WEEKEND, 23 * 60, 9 * 60)
) {
    fun isSilentAt(timeMs: Long): Boolean {
        if (!enabled) return false
        val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
        val day = cal.get(Calendar.DAY_OF_WEEK)
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return weekdays.isSilentAt(day, minute) || weekends.isSilentAt(day, minute)
    }

    /** True when nothing at all is selected, which reads as "never silent". */
    val hasNoDays: Boolean get() = weekdays.days.isEmpty() && weekends.days.isEmpty()

    companion object {
        val WEEKDAYS = setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY
        )
        val WEEKEND = setOf(Calendar.SATURDAY, Calendar.SUNDAY)

        /** Chip order within each group. */
        val WEEKDAY_ORDER = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY
        )
        val WEEKEND_ORDER = listOf(Calendar.SATURDAY, Calendar.SUNDAY)

        fun shortName(day: Int): String = when (day) {
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            else -> "Sun"
        }
    }
}
