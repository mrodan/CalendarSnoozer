package com.calendareventsnooze.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
