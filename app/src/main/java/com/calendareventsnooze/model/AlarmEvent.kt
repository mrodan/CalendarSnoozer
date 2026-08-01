package com.calendareventsnooze.model

data class AlarmEvent(
    val alarmId: String,
    val eventTitle: String,
    val eventText: String,
    val eventId: Long,       // calendar event ID; -1 if unavailable
    val eventTimeMs: Long    // scheduled start time of the event; -1 if unavailable
)
