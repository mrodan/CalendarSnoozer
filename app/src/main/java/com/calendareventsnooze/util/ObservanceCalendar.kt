package com.calendareventsnooze.util

import android.content.Context
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.ObservanceCity
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Round 28 — when Shabbat & Yom Tov Mode silences the app.
 *
 * The schedule is bundled in `assets/shabbat_yomtov`, one file per city, with
 * the rows exactly as the user supplied them: event, start, end. They run from
 * 30 Oct 2026 to 27 Oct 2046, and past the last row nothing is silenced.
 */
object ObservanceCalendar {

    /** One unbroken silent period. [endMs] is exclusive. */
    data class Window(val startMs: Long, val endMs: Long)

    private val ROW_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private val windowsByCity = ConcurrentHashMap<ObservanceCity, List<Window>>()

    /** True when the selected city is in Shabbat or Yom Tov at [atMs]. */
    fun isSilenced(context: Context, atMs: Long): Boolean {
        val city = AppPrefs.getObservanceCity(context) ?: return false
        val windows = windowsFor(context, city)
        return windows.binarySearch { window ->
            when {
                atMs < window.startMs -> 1
                atMs >= window.endMs -> -1
                else -> 0
            }
        } >= 0
    }

    /** When the selected city's next silent period begins, or null if none is left. */
    fun nextStartAfter(context: Context, atMs: Long): Long? {
        val city = AppPrefs.getObservanceCity(context) ?: return null
        return windowsFor(context, city).firstOrNull { it.startMs > atMs }?.startMs
    }

    private fun windowsFor(context: Context, city: ObservanceCity): List<Window> =
        windowsByCity.getOrPut(city) {
            context.assets.open("shabbat_yomtov/${city.assetName}.tsv")
                .bufferedReader()
                .useLines { rows -> mergeRows(rows, ZoneId.of(city.zoneId)) }
        }

    /**
     * Turns the schedule's rows into unbroken silent periods.
     *
     * Rows overlap on purpose: a Yom Tov that runs into Shabbat keeps both.
     * A row also joins the previous period when it starts on the date that
     * period ended, which only happens when Yom Tov begins as Shabbat goes out.
     * Those two are one continuous period even when the times do not touch: on
     * eight Saturday nights the Miami schedule starts Yom Tov 8 minutes after
     * Shabbat ends, and a reminder landing in that gap would ring.
     */
    internal fun mergeRows(rows: Sequence<String>, zone: ZoneId): List<Window> {
        val periods = rows
            .filter { it.isNotBlank() }
            .map { row ->
                val (_, start, end) = row.trim().split('\t')
                LocalDateTime.parse(start, ROW_TIME) to LocalDateTime.parse(end, ROW_TIME)
            }
            .sortedBy { it.first }
            .fold(mutableListOf<Pair<LocalDateTime, LocalDateTime>>()) { merged, (start, end) ->
                val last = merged.lastOrNull()
                if (last != null && !start.toLocalDate().isAfter(last.second.toLocalDate())) {
                    merged[merged.lastIndex] = last.first to maxOf(last.second, end)
                } else {
                    merged.add(start to end)
                }
                merged
            }
        return periods.map { (start, end) ->
            Window(
                startMs = start.atZone(zone).toInstant().toEpochMilli(),
                endMs = end.atZone(zone).toInstant().toEpochMilli()
            )
        }
    }
}
