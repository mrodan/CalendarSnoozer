package com.calendareventsnooze.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AlarmEvent
import com.calendareventsnooze.receiver.AlarmReceiver
import com.calendareventsnooze.util.putAlarmEvent

object AlarmScheduler {

    fun scheduleSnooze(ctx: Context, alarmEvent: AlarmEvent, minutes: Int) {
        scheduleAt(ctx, alarmEvent,
            System.currentTimeMillis() + minutes * 60_000L)
    }

    fun scheduleAt(ctx: Context, alarmEvent: AlarmEvent, triggerAtMs: Long) {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = "com.calendareventsnooze.TRIGGER_ALARM"
            putAlarmEvent(alarmEvent)
        }
        val pi = PendingIntent.getBroadcast(
            ctx,
            alarmEvent.alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = ctx.getSystemService(AlarmManager::class.java)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (am.canScheduleExactAlarms())
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, pi), pi)
                else
                    am.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            }
            else -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }
    }

    /**
     * B.7 — re-arms every saved snoozed alarm.
     *
     * AlarmManager alarms are **lost on reboot** (and whenever the OS clears the
     * app's pending intents), while the snoozed records live on in
     * SharedPreferences. That combination is what left entries sitting in the
     * Snoozed Alarms tab with a time in the past that could never fire again.
     *
     * Future alarms are simply re-armed at their original time. Alarms whose
     * time already passed while they were un-armed are re-armed a short moment
     * from now — staggered so several don't fire at once — and their stored time
     * is corrected, so the user still gets the reminder they asked for instead of
     * a dead row.
     *
     * @return how many past-due alarms were repaired.
     */
    fun rescheduleAllSnoozed(ctx: Context): Int {
        val now = System.currentTimeMillis()
        var repaired = 0
        var stagger = 0L
        AppPrefs.getAllSnoozedAlarms(ctx).forEach { record ->
            val event = AlarmEvent(
                alarmId = record.alarmId,
                eventTitle = record.eventTitle,
                eventText = record.eventText,
                eventId = record.eventId,
                eventTimeMs = record.eventTimeMs
            )
            if (record.scheduledTimeMs > now) {
                scheduleAt(ctx, event, record.scheduledTimeMs)
            } else {
                stagger += 30_000L
                val revived = now + stagger
                scheduleAt(ctx, event, revived)
                AppPrefs.saveSnoozedAlarm(ctx, record.copy(scheduledTimeMs = revived))
                repaired++
            }
        }
        return repaired
    }

    fun cancelAlarm(ctx: Context, alarmId: String) {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = "com.calendareventsnooze.TRIGGER_ALARM"
        }
        val pi = PendingIntent.getBroadcast(
            ctx,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { ctx.getSystemService(AlarmManager::class.java).cancel(it) }
    }
}
