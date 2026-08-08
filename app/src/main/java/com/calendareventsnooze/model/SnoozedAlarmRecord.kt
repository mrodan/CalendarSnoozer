package com.calendareventsnooze.model

data class SnoozedAlarmRecord(
    val alarmId: String,
    val eventTitle: String,
    val eventText: String,
    val eventId: Long,
    val eventTimeMs: Long,
    val scheduledTimeMs: Long  // Unix timestamp when alarm will next fire
)

/**
 * F.15 — an alarm that stopped without the user acting on it: auto-dismissed,
 * out of auto-snooze retries, or killed by Force Stop.
 *
 * Deliberately a separate type from [SnoozedAlarmRecord] even though the fields
 * overlap: a missed alarm has no future firing time, so carrying a
 * `scheduledTimeMs` would invite code to treat it as still armed. [missedAtMs]
 * is when it was given up on; the list shows the *event's* own time.
 */
data class MissedAlarmRecord(
    val alarmId: String,
    val eventTitle: String,
    val eventText: String,
    val eventId: Long,
    val eventTimeMs: Long,
    val missedAtMs: Long
)
