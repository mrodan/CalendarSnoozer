package com.calendareventsnooze.util

import android.content.Context
import android.content.Intent
import com.calendareventsnooze.model.AlarmEvent
import com.calendareventsnooze.scheduler.AlarmScheduler
import com.calendareventsnooze.service.AlarmService
import com.calendareventsnooze.ui.AlarmActivity
import java.util.concurrent.atomic.AtomicInteger

object TestAlarmHelper {

    private val fakeEvents = listOf(
        "Team Standup" to "Conference Room B · 15 min",
        "Doctor Appointment" to "Dr. Martinez · Bring insurance card",
        "Call with Client" to "Zoom link in calendar description",
        "Lunch with Sarah" to "The Corner Bistro · 12:30 PM",
        "Project Deadline" to "Submit final report by end of day",
        "Gym Session" to "Don't forget water bottle!",
        "Flight to New York" to "Terminal 3 · Check-in closes in 2h",
        "Birthday Dinner" to "Reservation confirmed for 7:30 PM"
    )

    private val counter = AtomicInteger(0)

    private fun nextEvent(): AlarmEvent {
        val idx = counter.getAndIncrement() % fakeEvents.size
        val (title, text) = fakeEvents[idx]
        return AlarmEvent(
            alarmId = "test_alarm_${System.currentTimeMillis()}",
            eventTitle = title,
            eventText = text,
            eventId = -1L,
            eventTimeMs = System.currentTimeMillis()
        )
    }

    /** Fire a test alarm immediately (starts service + full-screen activity). */
    fun fireTestAlarmNow(ctx: Context) {
        val event = nextEvent()
        ctx.startForegroundService(
            Intent(ctx, AlarmService::class.java).putAlarmEvent(event))
        ctx.startActivity(
            Intent(ctx, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }.putAlarmEvent(event))
    }

    /**
     * Schedule a test alarm to fire after [delaySeconds] via AlarmManager so the
     * user can lock the screen first and verify lock-screen takeover.
     */
    fun fireTestAlarmDelayed(ctx: Context, delaySeconds: Int = 5) {
        val event = nextEvent()
        AlarmScheduler.scheduleAt(
            ctx, event, System.currentTimeMillis() + delaySeconds * 1000L)
    }
}
