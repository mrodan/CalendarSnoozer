package com.calendareventsnooze.model

data class SnoozedAlarmRecord(
    val alarmId: String,
    val eventTitle: String,
    val eventText: String,
    val eventId: Long,
    val eventTimeMs: Long,
    val scheduledTimeMs: Long  // Unix timestamp when alarm will next fire
)
