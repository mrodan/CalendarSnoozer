package com.calendareventsnooze.util

import android.content.Intent
import com.calendareventsnooze.model.AlarmEvent

private const val EXTRA_ALARM_ID     = "ces_alarm_id"
private const val EXTRA_EVENT_TITLE  = "ces_event_title"
private const val EXTRA_EVENT_TEXT   = "ces_event_text"
private const val EXTRA_EVENT_ID     = "ces_event_id"
private const val EXTRA_EVENT_TIME   = "ces_event_time"

fun Intent.putAlarmEvent(event: AlarmEvent): Intent {
    putExtra(EXTRA_ALARM_ID, event.alarmId)
    putExtra(EXTRA_EVENT_TITLE, event.eventTitle)
    putExtra(EXTRA_EVENT_TEXT, event.eventText)
    putExtra(EXTRA_EVENT_ID, event.eventId)
    putExtra(EXTRA_EVENT_TIME, event.eventTimeMs)
    return this
}

fun Intent.getAlarmEvent(): AlarmEvent? {
    val alarmId = getStringExtra(EXTRA_ALARM_ID) ?: return null
    return AlarmEvent(
        alarmId = alarmId,
        eventTitle = getStringExtra(EXTRA_EVENT_TITLE) ?: "",
        eventText = getStringExtra(EXTRA_EVENT_TEXT) ?: "",
        eventId = getLongExtra(EXTRA_EVENT_ID, -1L),
        eventTimeMs = getLongExtra(EXTRA_EVENT_TIME, -1L)
    )
}
