package com.calendareventsnooze.service

import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AlarmEvent
import com.calendareventsnooze.ui.AlarmActivity
import com.calendareventsnooze.util.putAlarmEvent

class CalendarNotificationListener : NotificationListenerService() {

    /**
     * Round 20 — Android unbinds notification listeners under memory pressure,
     * and OEMs that kill background processes aggressively (Xiaomi, Oppo, vivo,
     * OnePlus, Huawei, and Samsung's "Sleeping apps") do it routinely. Without
     * this the service stays unbound until the phone reboots or the user toggles
     * the permission by hand — the app looks alive, every permission reads
     * granted, and no calendar notification is ever seen again.
     *
     * `requestRebind` asks the system to bind us again; it is the documented
     * remedy and is safe to call even if the disconnect was deliberate.
     */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        runCatching {
            requestRebind(ComponentName(this, CalendarNotificationListener::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg !in AppPrefs.getCalendarPackages(applicationContext)) return
        // Round 21 — the Home master switch. Checked after the package filter so
        // an unrelated notification can never be mistaken for a suppressed one.
        if (!AppPrefs.isSnoozerEnabled(applicationContext)) return

        // Recorded before anything can fail, so the diagnostic line on Home
        // still answers "did a reminder reach us?" even if the alarm misfires.
        AppPrefs.recordInterception(applicationContext, pkg)

        val extras = sbn.notification.extras
        val title = extras.getString("android.title")
            ?: extras.getString("android.subject") ?: ""
        val text = extras.getString("android.text") ?: ""
        val eventId = extras.getLong("eventId", -1L)

        // Try multiple sources for the event's scheduled time
        val eventTimeMs = listOf(
            extras.getLong("when", -1L),
            extras.getLong("android.when", -1L),
            sbn.notification.`when`.takeIf { it > 0L } ?: -1L
        ).firstOrNull { it > 0L } ?: -1L

        val alarmId = "alarm_${System.currentTimeMillis()}"
        val alarmEvent = AlarmEvent(alarmId, title, text, eventId, eventTimeMs)

        // CRITICAL: Do NOT call cancelNotification(sbn.key).
        // The original calendar notification must remain visible in the panel.

        // Start alarm service
        val serviceIntent = Intent(applicationContext, AlarmService::class.java)
            .putAlarmEvent(alarmEvent)
        applicationContext.startForegroundService(serviceIntent)

        // Launch alarm activity
        val activityIntent = Intent(applicationContext, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }.putAlarmEvent(alarmEvent)
        applicationContext.startActivity(activityIntent)
    }
}
