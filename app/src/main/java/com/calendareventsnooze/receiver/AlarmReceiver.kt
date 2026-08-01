package com.calendareventsnooze.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.scheduler.AlarmScheduler
import com.calendareventsnooze.service.AlarmService
import com.calendareventsnooze.ui.AlarmActivity
import com.calendareventsnooze.util.getAlarmEvent
import com.calendareventsnooze.util.putAlarmEvent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.calendareventsnooze.TRIGGER_ALARM" -> {
                val alarmEvent = intent.getAlarmEvent() ?: return

                // Remove from snoozed list — alarm is now firing, not pending
                AppPrefs.removeSnoozedAlarm(context, alarmEvent.alarmId)

                context.startForegroundService(
                    Intent(context, AlarmService::class.java).putAlarmEvent(alarmEvent))

                context.startActivity(
                    Intent(context, AlarmActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }.putAlarmEvent(alarmEvent))
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // B.7 — AlarmManager does NOT persist alarms across a reboot, so
                // every snoozed alarm must be re-armed here. Without this, the
                // saved records survive but their alarms are gone forever, which
                // is what left past-dated rows in the Snoozed Alarms tab.
                AlarmScheduler.rescheduleAllSnoozed(context)
            }
        }
    }
}
