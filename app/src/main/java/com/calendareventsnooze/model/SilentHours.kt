package com.calendareventsnooze.model

import java.util.Calendar

/**
 * Round 22 — a recurring window in which calendar notifications are left alone.
 *
 * Deliberately narrow: this suppresses *interception*, so a reminder inside the
 * window behaves the way it did before the app was installed. It does not touch
 * alarms the user already snoozed — those were scheduled on purpose, and having
 * a snooze silently evaporate because it landed in the quiet window would be
 * the kind of surprise this app exists to prevent.
 *
 * [days] holds `java.util.Calendar` day constants (SUNDAY = 1 … SATURDAY = 7).
 * [startMinute] and [endMinute] are minutes from midnight.
 */
data class SilentHours(
    val enabled: Boolean = false,
    val days: Set<Int> = ALL_DAYS,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60
) {

    /**
     * True when [timeMs] falls inside the window.
     *
     * A window that ends before it starts wraps past midnight, and the day it is
     * matched against is the day it **started** — a 22:00–07:00 Friday window
     * covers Saturday 01:00, which is what someone picking "Friday night" means.
     */
    fun isSilentAt(timeMs: Long): Boolean {
        if (!enabled || days.isEmpty() || startMinute == endMinute) return false

        val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
        val day = cal.get(Calendar.DAY_OF_WEEK)
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        return if (startMinute < endMinute) {
            day in days && minute >= startMinute && minute < endMinute
        } else {
            // Wrapped: either the tail of a selected day, or the small hours of
            // the morning after one.
            (day in days && minute >= startMinute) ||
                (previousDay(day) in days && minute < endMinute)
        }
    }

    val isEveryDay: Boolean get() = days.size == ALL_DAYS.size

    companion object {
        val ALL_DAYS = setOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        )

        /** Monday-first order, which is how the day chips read. */
        val DISPLAY_ORDER = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )

        fun shortName(day: Int): String = when (day) {
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            else -> "Sun"
        }

        private fun previousDay(day: Int): Int = if (day == Calendar.SUNDAY) {
            Calendar.SATURDAY
        } else {
            day - 1
        }
    }
}
