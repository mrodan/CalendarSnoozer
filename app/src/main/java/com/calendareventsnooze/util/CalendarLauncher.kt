package com.calendareventsnooze.util

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

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

    /** A concrete calendar event instance resolved from the calendar provider. */
    private data class CalendarHit(val eventId: Long, val begin: Long, val end: Long)

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
        val hit = if (eventId > 0L) CalendarHit(eventId, eventTimeMs, eventTimeMs)
                  else resolveCalendarEvent(context, eventTitle, eventTimeMs)

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

    /**
     * B.5 — finds the real calendar event behind this alarm.
     *
     * Google Calendar does **not** put an `eventId` in its notification extras
     * (it is always -1), so the event has to be looked up in the calendar
     * provider instead. Searches instances around the event's time and matches
     * on title, preferring the instance closest to that time.
     */
    private fun resolveCalendarEvent(
        context: Context,
        eventTitle: String,
        eventTimeMs: Long
    ): CalendarHit? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) return null

        val anchor = if (eventTimeMs > 0L) eventTimeMs else System.currentTimeMillis()
        val window = 12L * 60 * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, anchor - window)
            ContentUris.appendId(this, anchor + window)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE
        )
        val wanted = eventTitle.trim()
        if (wanted.isEmpty()) return null

        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                var best: CalendarHit? = null
                var bestDelta = Long.MAX_VALUE
                while (c.moveToNext()) {
                    val title = c.getString(3)?.trim().orEmpty()
                    if (title.isEmpty()) continue
                    val matches = title.equals(wanted, ignoreCase = true) ||
                            title.contains(wanted, ignoreCase = true) ||
                            wanted.contains(title, ignoreCase = true)
                    if (!matches) continue
                    val begin = c.getLong(1)
                    val delta = kotlin.math.abs(begin - anchor)
                    if (delta < bestDelta) {
                        bestDelta = delta
                        best = CalendarHit(c.getLong(0), begin, c.getLong(2))
                    }
                }
                best
            }
        }.getOrNull()
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
