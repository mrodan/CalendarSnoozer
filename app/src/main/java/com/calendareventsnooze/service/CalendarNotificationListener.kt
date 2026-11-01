package com.calendareventsnooze.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AlarmEvent
import com.calendareventsnooze.ui.AlarmActivity
import com.calendareventsnooze.util.CalendarEvents
import com.calendareventsnooze.util.ObservanceCalendar
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
        // Round 22 — inside a quiet window the reminder is left exactly as the
        // calendar app posted it, which is the behaviour the user had before
        // installing this. Snoozed alarms are unaffected: they were scheduled
        // deliberately and still fire.
        if (AppPrefs.getSilentHours(applicationContext)
                .isSilentAt(System.currentTimeMillis())
        ) return

        val notification = sbn.notification
        val extras = notification.extras
        // Round 27 — `Bundle.getString` returns null for a CharSequence that is
        // not a String, and notification titles are often SpannableStrings. This
        // may be why the reported "ghost alarm" had no title, but that is not
        // confirmed: the interception log records what real reminders contain.
        val title = extras.text("android.title") ?: extras.text("android.subject") ?: ""
        val text = extras.text("android.text") ?: extras.text("android.bigText") ?: ""

        // Round 29 — how old a notification was on arrival is logged rather than
        // acted on, so a late delivery will show up if one is behind a ghost.
        val postedAgo = "posted ${(System.currentTimeMillis() - sbn.postTime) / 1000}s ago"

        /** Records why this notification was ignored, then stops. */
        fun skip(reason: String) =
            AppPrefs.logInterception(applicationContext, pkg, title, "$reason, $postedAgo")

        // Round 28 — like Silent Hours, the reminder is left as the calendar app
        // posted it, and it is not recorded as missed.
        if (ObservanceCalendar.isSilenced(applicationContext, System.currentTimeMillis())) {
            return skip("skipped: Shabbat & Yom Tov Mode")
        }

        // A group summary carries no reminder of its own — it stands for the
        // ones underneath it, which arrive as their own notifications.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            return skip("skipped: group summary")
        }
        // Ongoing notifications are status, not reminders: a calendar app uses
        // them for "event in progress" and for its own sync work.
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
            return skip("skipped: ongoing")
        }
        // Nothing to put on the takeover. An alarm with no title and no text
        // cannot tell the user what it is for, which is how a ghost looks.
        if (title.isBlank() && text.isBlank()) {
            return skip("skipped: no title or text")
        }

        val eventId = extras.getLong("eventId", -1L)

        // Try multiple sources for the event's scheduled time
        val eventTimeMs = listOf(
            extras.getLong("when", -1L),
            extras.getLong("android.when", -1L),
            notification.`when`.takeIf { it > 0L } ?: -1L
        ).firstOrNull { it > 0L } ?: -1L

        // An update to a notification already acted on is not a second
        // reminder. Keyed on content as well as identity, so a genuine follow-up
        // ("starting now" after "in 10 minutes") still gets through.
        val fingerprint = "${sbn.key}|$title|$text|$eventTimeMs"
        if (!AppPrefs.claimNotification(applicationContext, fingerprint)) {
            return skip("skipped: repeat of one already alarmed")
        }

        // Round 27 — the account filter. Which account an event belongs to has
        // to be looked up, because the notification does not say; when it
        // cannot be resolved the alarm goes ahead, since a missed reminder is
        // worse than one the user did not strictly need.
        if (isIgnoredAccount(title, eventTimeMs)) {
            return skip("skipped: ignored account")
        }

        // Recorded before anything can fail, so the diagnostic line on Home
        // still answers "did a reminder reach us?" even if the alarm misfires.
        AppPrefs.recordInterception(applicationContext, pkg)
        AppPrefs.logInterception(applicationContext, pkg, title, "alarm, $postedAgo")

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

    /**
     * True only when the event behind this reminder is **known** to belong to an
     * account the user switched off.
     *
     * Deliberately fails open at every step: no accounts switched off, no
     * calendar permission, no matching event, or an event on a calendar whose
     * account cannot be read all mean "let the alarm through". Suppressing an
     * alarm on a guess is the one outcome worth avoiding.
     */
    private fun isIgnoredAccount(title: String, eventTimeMs: Long): Boolean {
        val ignored = AppPrefs.getIgnoredCalendarAccounts(applicationContext)
        if (ignored.isEmpty()) return false
        val hit = CalendarEvents.resolve(applicationContext, title, eventTimeMs)
            ?: return false
        val account = CalendarEvents.accountForCalendar(applicationContext, hit.calendarId)
            ?: return false
        return account in ignored
    }
}

/**
 * Reads a notification extra as text whatever type it was stored as.
 * See the note at the call site — this is what `getString` gets wrong.
 */
private fun Bundle.text(key: String): String? =
    getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
