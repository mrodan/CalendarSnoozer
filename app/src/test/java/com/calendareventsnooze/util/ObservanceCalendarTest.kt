package com.calendareventsnooze.util

import com.calendareventsnooze.model.ObservanceCity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Runs against the real bundled schedules, so a bad asset fails the build. */
class ObservanceCalendarTest {

    private fun windows(city: ObservanceCity): List<ObservanceCalendar.Window> =
        File("src/main/assets/shabbat_yomtov/${city.assetName}.tsv").useLines { rows ->
            ObservanceCalendar.mergeRows(rows, ZoneId.of(city.zoneId))
        }

    private fun millis(city: ObservanceCity, time: String): Long =
        LocalDateTime.parse(time).atZone(ZoneId.of(city.zoneId)).toInstant().toEpochMilli()

    private fun List<ObservanceCalendar.Window>.covers(atMs: Long) =
        any { atMs >= it.startMs && atMs < it.endMs }

    @Test
    fun everyCityMergesToTheSamePeriods() {
        ObservanceCity.entries.forEach { city ->
            assertEquals(city.label, 1109, windows(city).size)
        }
    }

    @Test
    fun periodsAreOrderedAndNeverTouch() {
        val twentyHours = 20L * 60 * 60 * 1000
        ObservanceCity.entries.forEach { city ->
            windows(city).zipWithNext().forEach { (earlier, later) ->
                assertTrue(city.label, earlier.startMs < earlier.endMs)
                assertTrue(city.label, later.startMs - earlier.endMs > twentyHours)
            }
        }
    }

    @Test
    fun theMiamiGapBetweenShabbatAndYomTovIsClosed() {
        // Shabbat ends 20:43 and Shavuot starts 20:51 in the supplied rows.
        val miami = windows(ObservanceCity.MIAMI)
        assertTrue(miami.covers(millis(ObservanceCity.MIAMI, "2029-05-19T20:47:00")))
    }

    @Test
    fun boundariesFollowTheSchedule() {
        val city = ObservanceCity.NEW_YORK_CITY
        val nyc = windows(city)
        assertFalse(nyc.covers(millis(city, "2026-11-06T16:27:00")))
        assertTrue(nyc.covers(millis(city, "2026-11-06T16:28:00")))
        assertTrue(nyc.covers(millis(city, "2026-11-07T17:26:00")))
        assertFalse(nyc.covers(millis(city, "2026-11-07T17:27:00")))
    }

    @Test
    fun timesAreReadInTheCitysOwnZone() {
        // Candle lighting is 17:48 in Caracas that Friday, which is 21:48 UTC.
        val caracas = windows(ObservanceCity.CARACAS)
        val friday = Instant.parse("2026-12-11T21:47:00Z").toEpochMilli()
        assertFalse(caracas.covers(friday))
        val afterStart = Instant.parse("2026-12-11T21:48:00Z").toEpochMilli()
        assertTrue(caracas.covers(afterStart))
    }

    @Test
    fun aYomTovRunningIntoShabbatIsOnePeriod() {
        // Passover starts Wed 21 Apr 2027 and ends with Shabbat on the 24th.
        val city = ObservanceCity.NEW_YORK_CITY
        val nyc = windows(city)
        val start = millis(city, "2027-04-21T19:23:00")
        val period = nyc.single { it.startMs == start }
        assertEquals(millis(city, "2027-04-24T20:26:00"), period.endMs)
    }
}
