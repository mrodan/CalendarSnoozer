package com.calendareventsnooze.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

/**
 * Opens the real calendar event behind an alarm.
 *
 * Extracted from AlarmActivity so the Manage-snoozed-alarm sheet (F.12) can use
 * the same implementation. Both traps this logic exists for — that Google
 * Calendar never puts an `eventId` in its notification extras (trap 3), and that
 * a bare calendar URI gets claimed by unrelated apps (trap 4) — are easy to
 * reintroduce by writing a second copy, so there must only ever be one.
 */
object CalendarLauncher {

    /**
     * Round 20 — the candidate list moved to [CalendarApps] so it covers the
     * OEM calendars too, and so it cannot drift from the set of apps whose
     * notifications are intercepted. Only the *dedicated* calendars are opened:
     * handing a calendar event URI to Gmail achieves nothing. Trap 4 still
     * holds — only packages on this list are ever targeted, never "whatever
     * handles the URI".
     */
    private val CALENDAR_PACKAGES: List<String>
        get() = CalendarApps.KNOWN.keys.filter { CalendarApps.isDedicatedCalendar(it) }

    /**
     * Round 27 — the provider lookup moved to [CalendarEvents] so the account
     * filter could use it without writing a second matcher. Trap 3's reasoning
     * moved with it; traps 4's (never launch an unknown package) stays here.
     */
    private fun hitFor(context: Context, eventId: Long, title: String, timeMs: Long) =
        if (eventId > 0L) CalendarEvents.Hit(eventId, timeMs, timeMs, 0L)
        else CalendarEvents.resolve(context, title, timeMs)

    /**
     * Opens [eventTitle]'s calendar entry, never an app chooser.
     *
     * Event intents are offered to each installed calendar app explicitly (a
     * failure just falls through to the next candidate), because handing a bare
     * `content://com.android.calendar/...` intent to the system lets unrelated
     * apps claim it — that is what produced the "Open with Google" chooser and
     * the "Couldn't load object" error.
     */
    fun open(context: Context, eventId: Long, eventTitle: String, eventTimeMs: Long) {
        val installedCalendars = CALENDAR_PACKAGES.filter { pkg ->
            runCatching { context.packageManager.getLaunchIntentForPackage(pkg) != null }
                .getOrDefault(false)
        }

        // 1. The specific event — from the notification, or looked up in the provider.
        val hit = hitFor(context, eventId, eventTitle, eventTimeMs)

        if (hit != null) {
            val eventIntent = Intent(Intent.ACTION_VIEW,
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, hit.eventId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (hit.begin > 0L) {
                eventIntent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, hit.begin)
                if (hit.end > 0L) {
                    eventIntent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, hit.end)
                }
            }
            for (pkg in installedCalendars) {
                if (tryStart(context, Intent(eventIntent).setPackage(pkg))) return
            }
            calendarPackageFor(context, eventIntent)?.let {
                if (tryStart(context, Intent(eventIntent).setPackage(it))) return
            }
        }

        // 2. Open the calendar at the event's day and time.
        if (eventTimeMs > 0L) {
            val timeIntent = Intent(Intent.ACTION_VIEW,
                CalendarContract.CONTENT_URI.buildUpon()
                    .appendPath("time")
                    .appendPath(eventTimeMs.toString())
                    .build()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            for (pkg in installedCalendars) {
                if (tryStart(context, Intent(timeIntent).setPackage(pkg))) return
            }
        }

        // 3. Last resort — just open the calendar app.
        for (pkg in installedCalendars) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            if (tryStart(context, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
        }
        tryStart(context, Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun tryStart(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent); true }.getOrDefault(false)

    /**
     * Returns a **known calendar** package able to handle [intent], or null.
     *
     * Deliberately never falls back to "whatever app happens to claim the URI":
     * on real devices `content://com.android.calendar/...` is often claimed by
     * unrelated apps (Messages claims it on stock images), which is what produced
     * the "Open with Google" chooser and the "Couldn't load object" failure.
     */
    private fun calendarPackageFor(context: Context, intent: Intent): String? {
        val handlers = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        return CALENDAR_PACKAGES.firstOrNull { it in handlers }
    }
}
