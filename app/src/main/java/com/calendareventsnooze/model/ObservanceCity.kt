package com.calendareventsnooze.model

/**
 * Round 28 — the places Shabbat & Yom Tov Mode has a schedule for.
 *
 * [zoneId] is the city's own zone, not the phone's: the schedule's times are
 * wall-clock times in that city, so the moment Shabbat begins in Miami is the
 * same moment wherever the phone happens to be set.
 */
enum class ObservanceCity(val label: String, val assetName: String, val zoneId: String) {
    CARACAS("Caracas", "caracas", "America/Caracas"),
    MIAMI("Miami", "miami", "America/New_York"),
    NEW_YORK_CITY("New York City", "new_york_city", "America/New_York")
}
