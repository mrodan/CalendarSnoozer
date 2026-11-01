package com.calendareventsnooze.util

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Round 27 — reading the calendar provider: the accounts it holds, and which
 * event a notification refers to.
 *
 * The event lookup used to live inside [CalendarLauncher], whose comment warned
 * that a second copy would reintroduce traps 3 and 4. Account filtering needs
 * the same lookup, so it moved here and both callers share it rather than one
 * of them growing its own.
 */
object CalendarEvents {

    /** An account the calendar provider has calendars for. */
    data class Account(
        val name: String,
        val type: String,
        val calendarNames: List<String>
    )

    /** A concrete event instance resolved from the provider. */
    data class Hit(
        val eventId: Long,
        val begin: Long,
        val end: Long,
        val calendarId: Long
    )

    private fun canRead(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Every account the calendar provider knows about, with the calendars it
     * owns. Ordered by name so the Settings list does not reshuffle itself.
     *
     * The provider is not partitioned by app, so this is "the accounts whose
     * events a calendar app could be reminding you about" — which is the
     * question the Settings toggle is really asking.
     */
    fun accounts(context: Context): List<Account> {
        if (!canRead(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        val byAccount = linkedMapOf<Pair<String, String>, MutableList<String>>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0)?.trim().orEmpty()
                    if (name.isEmpty()) continue
                    val type = c.getString(1)?.trim().orEmpty()
                    val display = c.getString(2)?.trim().orEmpty()
                    byAccount.getOrPut(name to type) { mutableListOf() }
                        .also { if (display.isNotEmpty()) it.add(display) }
                }
            }
        }
        return byAccount
            .map { (key, calendars) -> Account(key.first, key.second, calendars) }
            .sortedBy { it.name.lowercase() }
    }

    /** The account that owns [calendarId], or null if it cannot be read. */
    fun accountForCalendar(context: Context, calendarId: Long): String? {
        if (!canRead(context) || calendarId <= 0L) return null
        return runCatching {
            context.contentResolver.query(
                ContentUris.withAppendedId(
                    CalendarContract.Calendars.CONTENT_URI, calendarId),
                arrayOf(CalendarContract.Calendars.ACCOUNT_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.trim()?.takeIf { it.isNotEmpty() }
                else null
            }
        }.getOrNull()
    }

    /**
     * B.5 — finds the real calendar event behind an alarm.
     *
     * Google Calendar does **not** put an `eventId` in its notification extras
     * (it is always -1, trap 3), so the event has to be looked up in the
     * provider instead. Searches instances around the event's time and matches
     * on title, preferring the instance closest to that time.
     *
     * Title matching is why this is best-effort: a notification whose title
     * cannot be read, or an event since edited, resolves to nothing. Callers
     * must decide what that means for them — [CalendarLauncher] falls back to
     * opening the day, and account filtering lets the alarm through.
     */
    fun resolve(context: Context, eventTitle: String, eventTimeMs: Long): Hit? {
        if (!canRead(context)) return null
        val wanted = eventTitle.trim()
        if (wanted.isEmpty()) return null

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
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.CALENDAR_ID
        )

        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                var best: Hit? = null
                var bestDelta = Long.MAX_VALUE
                while (c.moveToNext()) {
                    val title = c.getString(3)?.trim().orEmpty()
                    if (title.isEmpty()) continue
                    val matches = title.equals(wanted, ignoreCase = true) ||
                        title.contains(wanted, ignoreCase = true) ||
                        wanted.contains(title, ignoreCase = true)
                    if (!matches) continue
                    val begin = c.getLong(1)
                    val delta = abs(begin - anchor)
                    if (delta < bestDelta) {
                        bestDelta = delta
                        best = Hit(c.getLong(0), begin, c.getLong(2), c.getLong(4))
                    }
                }
                best
            }
        }.getOrNull()
    }
}
