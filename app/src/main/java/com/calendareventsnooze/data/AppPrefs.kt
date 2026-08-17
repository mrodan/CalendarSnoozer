package com.calendareventsnooze.data

import android.content.Context
import android.content.SharedPreferences
import com.calendareventsnooze.model.AlarmScreenStyle
import com.calendareventsnooze.model.AutoDismissAction
import com.calendareventsnooze.model.MissedAlarmRecord
import com.calendareventsnooze.model.RingerMode
import com.calendareventsnooze.model.SnoozePreset
import com.calendareventsnooze.model.SilentHours
import com.calendareventsnooze.model.SilentWindow
import com.calendareventsnooze.model.SnoozedAlarmRecord
import com.calendareventsnooze.model.SoundProfile
import com.calendareventsnooze.model.VibrationPreset
import com.calendareventsnooze.util.CalendarApps
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AppPrefs {

    private const val PREFS_NAME = "ces_prefs"
    private const val KEY_SNOOZE_PRESETS = "snooze_presets"
    private const val KEY_SNOOZED_ALARMS = "snoozed_alarms"
    private const val KEY_MISSED_ALARMS = "missed_alarms"
    private const val KEY_CALENDAR_PACKAGES = "calendar_packages"
    private const val KEY_AUTO_SNOOZE_PREFIX = "auto_snooze_count_"
    private const val KEY_SOUND_PROFILE_PREFIX = "sound_profile_"
    private const val KEY_ALARM_SCREEN_STYLE = "alarm_screen_style"
    private const val KEY_SNOOZER_ENABLED = "snoozer_enabled"
    private const val KEY_SILENT_HOURS = "silent_hours"
    private const val KEY_LAST_SEEN_PACKAGE = "last_seen_package"
    private const val KEY_LAST_SEEN_AT = "last_seen_at"

    private val gson = Gson()

    // UI.25.3 — what "Customize your Buzz" opens with: M / L / 5 / XL / 5.
    const val DEFAULT_BUZZ_ON_MS = 500      // M
    const val DEFAULT_BUZZ_OFF_MS = 1000    // L
    const val DEFAULT_BUZZES_PER_PATTERN = 5
    const val DEFAULT_PATTERN_DELAY_MS = 3000 // XL
    const val DEFAULT_VIBRATION_REPETITIONS = 5

    /**
     * UI.25 — the buzz style a **fresh install** starts with. Medium is the
     * same M/M buzz length the sliders already defaulted to; what changes is
     * that it now keeps buzzing until the alarm resolves instead of stopping
     * after five patterns.
     *
     * Profiles saved before UI.25 migrate to CUSTOM instead (see [toProfile]),
     * so an existing phone keeps behaving exactly as it did.
     */
    val DEFAULT_VIBRATION_PRESET = VibrationPreset.MEDIUM

    // F.10 / UI.25.4 — sound shaping defaults: half volume, no fade, no cut-off.
    const val DEFAULT_ALARM_VOLUME_PERCENT = 50
    const val DEFAULT_FADE_IN_SECONDS = 0
    const val DEFAULT_SOUND_STOPS_AFTER_SECONDS = 0

    /**
     * UI.18 — the gap between the first and second output. It applies to every
     * mode so that whenever Sequencing does become relevant (sound *and*
     * vibration both on) it already reads "Sound first, 5 seconds".
     */
    const val DEFAULT_SECOND_START_DELAY_SECONDS = 5

    // UI.20 / UI.25.4 — the auto-snooze presets, identical in all three ringer
    // modes. The trigger is shared by both branches, so Auto-Dismiss starts at
    // 30 seconds too.
    const val DEFAULT_AUTO_TRIGGER_SECONDS = 30
    const val DEFAULT_AUTO_SNOOZE_MINUTES = 10
    const val DEFAULT_AUTO_SNOOZE_MAX_RETRIES = 2

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------------
    // Snooze Presets
    // ---------------------------------------------------------------------

    private val defaultPresets = listOf(
        SnoozePreset("10 min", 10),
        SnoozePreset("30 min", 30),
        SnoozePreset("1 hour", 60),
        SnoozePreset("1 day", 1440)
    )

    fun getSnoozePresets(ctx: Context): List<SnoozePreset> {
        val json = prefs(ctx).getString(KEY_SNOOZE_PRESETS, null) ?: return defaultPresets
        return try {
            val type = object : TypeToken<List<SnoozePreset>>() {}.type
            gson.fromJson<List<SnoozePreset>>(json, type) ?: defaultPresets
        } catch (e: Exception) {
            defaultPresets
        }
    }

    fun saveSnoozePresets(ctx: Context, presets: List<SnoozePreset>) {
        prefs(ctx).edit().putString(KEY_SNOOZE_PRESETS, gson.toJson(presets)).apply()
    }

    // ---------------------------------------------------------------------
    // Sound Profiles
    // ---------------------------------------------------------------------

    // JSON-safe mirror of SoundProfile. Every vibration field is nullable so a
    // profile saved before F.7 restructured them migrates to the defaults
    // instead of silently becoming 0 (see trap 5 in CLAUDE.md).
    private data class SoundProfileJson(
        val ringerMode: RingerMode,
        val soundEnabled: Boolean,
        val soundUri: String?,
        val alarmVolumePercent: Int?,
        val soundDelaySeconds: Int?,
        val fadeInSeconds: Int?,
        val soundStopsAfterSeconds: Int?,
        val vibrationEnabled: Boolean,
        val vibrationPreset: VibrationPreset?,
        val vibrationDelaySeconds: Int?,
        val vibrationStopsAfterSeconds: Int?,
        val buzzOnMs: Int?,
        val buzzOffMs: Int?,
        val buzzesPerPattern: Int?,
        val delayBetweenPatternsMs: Int?,
        val vibrationRepetitions: Int?,
        val soundStartsFirst: Boolean,
        val secondStartDelaySeconds: Int,
        val autoDismissSeconds: Int,
        val autoDismissAction: AutoDismissAction,
        val autoDismissSnoozeMinutes: Int,
        val autoDismissMaxRetries: Int
    )

    private fun SoundProfile.toJson() = SoundProfileJson(
        ringerMode, soundEnabled, soundUri,
        alarmVolumePercent, soundDelaySeconds, fadeInSeconds, soundStopsAfterSeconds,
        vibrationEnabled, vibrationPreset,
        vibrationDelaySeconds, vibrationStopsAfterSeconds,
        buzzOnMs, buzzOffMs, buzzesPerPattern, delayBetweenPatternsMs,
        vibrationRepetitions, soundStartsFirst,
        secondStartDelaySeconds, autoDismissSeconds, autoDismissAction,
        autoDismissSnoozeMinutes, autoDismissMaxRetries
    )

    private fun SoundProfileJson.toProfile() = SoundProfile(
        ringerMode = ringerMode,
        soundEnabled = soundEnabled,
        soundUri = soundUri,
        alarmVolumePercent = (alarmVolumePercent ?: DEFAULT_ALARM_VOLUME_PERCENT)
            .coerceIn(SoundProfile.MIN_VOLUME_PERCENT, SoundProfile.MAX_VOLUME_PERCENT),
        soundDelaySeconds = (soundDelaySeconds ?: 0).coerceAtLeast(0),
        fadeInSeconds = (fadeInSeconds ?: DEFAULT_FADE_IN_SECONDS).coerceAtLeast(0),
        soundStopsAfterSeconds = (soundStopsAfterSeconds
            ?: DEFAULT_SOUND_STOPS_AFTER_SECONDS).coerceAtLeast(0),
        vibrationEnabled = vibrationEnabled,
        // UI.25 — a profile written before the presets existed was shaped by
        // the five sliders, so CUSTOM is the only migration that preserves it.
        vibrationPreset = vibrationPreset ?: VibrationPreset.CUSTOM,
        vibrationDelaySeconds = (vibrationDelaySeconds ?: 0).coerceAtLeast(0),
        vibrationStopsAfterSeconds = (vibrationStopsAfterSeconds ?: 0).coerceAtLeast(0),
        buzzOnMs = (buzzOnMs ?: DEFAULT_BUZZ_ON_MS).coerceAtLeast(1),
        buzzOffMs = (buzzOffMs ?: DEFAULT_BUZZ_OFF_MS).coerceAtLeast(0),
        buzzesPerPattern = (buzzesPerPattern ?: DEFAULT_BUZZES_PER_PATTERN)
            .coerceIn(1, SoundProfile.MAX_COUNT),
        delayBetweenPatternsMs = (delayBetweenPatternsMs ?: DEFAULT_PATTERN_DELAY_MS)
            .coerceAtLeast(0),
        vibrationRepetitions = (vibrationRepetitions ?: DEFAULT_VIBRATION_REPETITIONS)
            .coerceIn(1, SoundProfile.MAX_COUNT),
        soundStartsFirst = soundStartsFirst,
        secondStartDelaySeconds = secondStartDelaySeconds,
        autoDismissSeconds = autoDismissSeconds,
        autoDismissAction = autoDismissAction,
        autoDismissSnoozeMinutes = autoDismissSnoozeMinutes,
        autoDismissMaxRetries = autoDismissMaxRetries
    )

    fun defaultSoundProfile(mode: RingerMode): SoundProfile = when (mode) {
        RingerMode.SOUND_ON -> SoundProfile(
            ringerMode = RingerMode.SOUND_ON,
            soundEnabled = true,
            soundUri = null,
            alarmVolumePercent = DEFAULT_ALARM_VOLUME_PERCENT,
            soundDelaySeconds = 0,
            fadeInSeconds = DEFAULT_FADE_IN_SECONDS,
            soundStopsAfterSeconds = DEFAULT_SOUND_STOPS_AFTER_SECONDS,
            vibrationEnabled = true,
            vibrationPreset = DEFAULT_VIBRATION_PRESET,
            vibrationDelaySeconds = 0,
            vibrationStopsAfterSeconds = 0,
            buzzOnMs = DEFAULT_BUZZ_ON_MS,
            buzzOffMs = DEFAULT_BUZZ_OFF_MS,
            buzzesPerPattern = DEFAULT_BUZZES_PER_PATTERN,
            delayBetweenPatternsMs = DEFAULT_PATTERN_DELAY_MS,
            vibrationRepetitions = DEFAULT_VIBRATION_REPETITIONS,
            // UI.25.4 — vibration leads, sound follows after the delay below.
            soundStartsFirst = false,
            secondStartDelaySeconds = DEFAULT_SECOND_START_DELAY_SECONDS,
            autoDismissSeconds = DEFAULT_AUTO_TRIGGER_SECONDS,
            autoDismissAction = AutoDismissAction.SNOOZE,
            autoDismissSnoozeMinutes = DEFAULT_AUTO_SNOOZE_MINUTES,
            autoDismissMaxRetries = DEFAULT_AUTO_SNOOZE_MAX_RETRIES
        )
        RingerMode.VIBRATE -> SoundProfile(
            ringerMode = RingerMode.VIBRATE,
            soundEnabled = false,
            soundUri = null,
            alarmVolumePercent = DEFAULT_ALARM_VOLUME_PERCENT,
            soundDelaySeconds = 0,
            fadeInSeconds = DEFAULT_FADE_IN_SECONDS,
            soundStopsAfterSeconds = DEFAULT_SOUND_STOPS_AFTER_SECONDS,
            vibrationEnabled = true,
            vibrationPreset = DEFAULT_VIBRATION_PRESET,
            vibrationDelaySeconds = 0,
            vibrationStopsAfterSeconds = 0,
            buzzOnMs = DEFAULT_BUZZ_ON_MS,
            buzzOffMs = DEFAULT_BUZZ_OFF_MS,
            buzzesPerPattern = DEFAULT_BUZZES_PER_PATTERN,
            delayBetweenPatternsMs = DEFAULT_PATTERN_DELAY_MS,
            vibrationRepetitions = DEFAULT_VIBRATION_REPETITIONS,
            // UI.25.4 — vibration leads, sound follows after the delay below.
            soundStartsFirst = false,
            secondStartDelaySeconds = DEFAULT_SECOND_START_DELAY_SECONDS,
            autoDismissSeconds = DEFAULT_AUTO_TRIGGER_SECONDS,
            autoDismissAction = AutoDismissAction.SNOOZE,
            autoDismissSnoozeMinutes = DEFAULT_AUTO_SNOOZE_MINUTES,
            autoDismissMaxRetries = DEFAULT_AUTO_SNOOZE_MAX_RETRIES
        )
        RingerMode.SILENT -> SoundProfile(
            ringerMode = RingerMode.SILENT,
            soundEnabled = false,
            soundUri = null,
            alarmVolumePercent = DEFAULT_ALARM_VOLUME_PERCENT,
            soundDelaySeconds = 0,
            fadeInSeconds = DEFAULT_FADE_IN_SECONDS,
            soundStopsAfterSeconds = DEFAULT_SOUND_STOPS_AFTER_SECONDS,
            vibrationEnabled = false,
            vibrationPreset = DEFAULT_VIBRATION_PRESET,
            vibrationDelaySeconds = 0,
            vibrationStopsAfterSeconds = 0,
            buzzOnMs = DEFAULT_BUZZ_ON_MS,
            buzzOffMs = DEFAULT_BUZZ_OFF_MS,
            buzzesPerPattern = DEFAULT_BUZZES_PER_PATTERN,
            delayBetweenPatternsMs = DEFAULT_PATTERN_DELAY_MS,
            vibrationRepetitions = DEFAULT_VIBRATION_REPETITIONS,
            // UI.25.4 — vibration leads, sound follows after the delay below.
            soundStartsFirst = false,
            secondStartDelaySeconds = DEFAULT_SECOND_START_DELAY_SECONDS,
            autoDismissSeconds = DEFAULT_AUTO_TRIGGER_SECONDS,
            autoDismissAction = AutoDismissAction.SNOOZE,
            autoDismissSnoozeMinutes = DEFAULT_AUTO_SNOOZE_MINUTES,
            autoDismissMaxRetries = DEFAULT_AUTO_SNOOZE_MAX_RETRIES
        )
    }

    // ---------------------------------------------------------------------
    // Master switch and "is this working?" diagnostics (round 21)
    // ---------------------------------------------------------------------

    /**
     * The Home switch. Off means calendar notifications are left alone — it does
     * **not** cancel alarms the user has already snoozed, which were scheduled
     * deliberately and still fire.
     */
    fun isSnoozerEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SNOOZER_ENABLED, true)

    fun setSnoozerEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SNOOZER_ENABLED, enabled).apply()
    }

    /**
     * Round 22 — the quiet window. Stored through a nullable mirror for the same
     * reason SoundProfile is (trap 5): a field added later must migrate to a
     * sensible default rather than silently becoming 0 or an empty set, which
     * here would mean "silent all day".
     */
    private data class SilentHoursJson(
        val enabled: Boolean?,
        // Round 23 — the two windows. Null on a profile written before the
        // split, which is what the legacy fields below are still read for.
        val weekdayDays: List<Int>?,
        val weekdayStart: Int?,
        val weekdayEnd: Int?,
        val weekendDays: List<Int>?,
        val weekendStart: Int?,
        val weekendEnd: Int?,
        // Round 22's single window. Kept so an existing setting migrates into
        // the split rather than silently reverting to the defaults.
        val days: List<Int>?,
        val startMinute: Int?,
        val endMinute: Int?
    )

    fun getSilentHours(ctx: Context): SilentHours {
        val json = prefs(ctx).getString(KEY_SILENT_HOURS, null) ?: return SilentHours()
        val migrated = readSilentHours(json)
        // Round 24 — upgrade the stored shape as soon as it is read, rather than
        // waiting for the user to edit something. Otherwise a round 22 value
        // would be migrated in memory on every launch and never written back,
        // and the split would look like it had silently lost the weekend half.
        if (json.contains("\"days\"")) saveSilentHours(ctx, migrated)
        return migrated
    }

    private fun readSilentHours(json: String): SilentHours {
        return try {
            val stored = gson.fromJson(json, SilentHoursJson::class.java) ?: return SilentHours()
            val default = SilentHours()

            // A round 22 setting had one set of hours for the whole week; split
            // it across both groups so the times the user chose survive.
            //
            // All three legacy fields stand or fall together, keyed on `days`.
            // Reading them independently let a partial value produce a mongrel —
            // legacy *times* applied to *default* days — which is how a phone
            // ended up silencing weekends that had never been selected.
            val isLegacy = stored.days != null
            val legacyDays = if (isLegacy) stored.days?.toSet() else null
            val legacyStart =
                if (isLegacy) stored.startMinute?.coerceIn(0, 24 * 60 - 1) else null
            val legacyEnd = if (isLegacy) stored.endMinute?.coerceIn(0, 24 * 60 - 1) else null

            SilentHours(
                enabled = stored.enabled ?: default.enabled,
                weekdays = SilentWindow(
                    days = stored.weekdayDays?.toSet()
                        ?: legacyDays?.intersect(SilentHours.WEEKDAYS)
                        ?: default.weekdays.days,
                    startMinute = stored.weekdayStart?.coerceIn(0, 24 * 60 - 1)
                        ?: legacyStart ?: default.weekdays.startMinute,
                    endMinute = stored.weekdayEnd?.coerceIn(0, 24 * 60 - 1)
                        ?: legacyEnd ?: default.weekdays.endMinute
                ),
                weekends = SilentWindow(
                    days = stored.weekendDays?.toSet()
                        ?: legacyDays?.intersect(SilentHours.WEEKEND)
                        ?: default.weekends.days,
                    startMinute = stored.weekendStart?.coerceIn(0, 24 * 60 - 1)
                        ?: legacyStart ?: default.weekends.startMinute,
                    endMinute = stored.weekendEnd?.coerceIn(0, 24 * 60 - 1)
                        ?: legacyEnd ?: default.weekends.endMinute
                )
            )
        } catch (e: Exception) {
            SilentHours()
        }
    }

    fun saveSilentHours(ctx: Context, hours: SilentHours) {
        val json = gson.toJson(
            SilentHoursJson(
                enabled = hours.enabled,
                weekdayDays = hours.weekdays.days.toList(),
                weekdayStart = hours.weekdays.startMinute,
                weekdayEnd = hours.weekdays.endMinute,
                weekendDays = hours.weekends.days.toList(),
                weekendStart = hours.weekends.startMinute,
                weekendEnd = hours.weekends.endMinute,
                days = null, startMinute = null, endMinute = null
            )
        )
        prefs(ctx).edit().putString(KEY_SILENT_HOURS, json).apply()
    }

    /**
     * The last calendar reminder the listener actually intercepted. The hardest
     * question to answer about this app is "is it working at all?", and until
     * an event is genuinely due there is nothing on screen that says so.
     */
    fun recordInterception(ctx: Context, packageName: String) {
        prefs(ctx).edit()
            .putString(KEY_LAST_SEEN_PACKAGE, packageName)
            .putLong(KEY_LAST_SEEN_AT, System.currentTimeMillis())
            .apply()
    }

    /** Package and timestamp of the last interception, or null if never. */
    fun getLastInterception(ctx: Context): Pair<String, Long>? {
        val pkg = prefs(ctx).getString(KEY_LAST_SEEN_PACKAGE, null) ?: return null
        val at = prefs(ctx).getLong(KEY_LAST_SEEN_AT, 0L)
        return if (at > 0L) pkg to at else null
    }

    // ---------------------------------------------------------------------
    // Alarm screen style (UI.29)
    // ---------------------------------------------------------------------

    /** Stored by name, so an unknown or missing value falls back to Dark. */
    fun getAlarmScreenStyle(ctx: Context): AlarmScreenStyle {
        val name = prefs(ctx).getString(KEY_ALARM_SCREEN_STYLE, null)
            ?: return AlarmScreenStyle.DARK
        return runCatching { AlarmScreenStyle.valueOf(name) }
            .getOrDefault(AlarmScreenStyle.DARK)
    }

    fun setAlarmScreenStyle(ctx: Context, style: AlarmScreenStyle) {
        prefs(ctx).edit().putString(KEY_ALARM_SCREEN_STYLE, style.name).apply()
    }

    fun getSoundProfile(ctx: Context, mode: RingerMode): SoundProfile {
        val json = prefs(ctx).getString(KEY_SOUND_PROFILE_PREFIX + mode.name, null)
            ?: return defaultSoundProfile(mode)
        return try {
            val stored = gson.fromJson(json, SoundProfileJson::class.java)
            stored?.toProfile() ?: defaultSoundProfile(mode)
        } catch (e: Exception) {
            defaultSoundProfile(mode)
        }
    }

    fun saveSoundProfile(ctx: Context, profile: SoundProfile) {
        prefs(ctx).edit()
            .putString(KEY_SOUND_PROFILE_PREFIX + profile.ringerMode.name,
                gson.toJson(profile.toJson()))
            .apply()
    }

    // ---------------------------------------------------------------------
    // Snoozed Alarms — stored as Map<alarmId, SnoozedAlarmRecord>
    // ---------------------------------------------------------------------

    private fun readSnoozedMap(ctx: Context): MutableMap<String, SnoozedAlarmRecord> {
        val json = prefs(ctx).getString(KEY_SNOOZED_ALARMS, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, SnoozedAlarmRecord>>() {}.type
            gson.fromJson<MutableMap<String, SnoozedAlarmRecord>>(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeSnoozedMap(ctx: Context, map: Map<String, SnoozedAlarmRecord>) {
        prefs(ctx).edit().putString(KEY_SNOOZED_ALARMS, gson.toJson(map)).apply()
    }

    fun saveSnoozedAlarm(ctx: Context, record: SnoozedAlarmRecord) {
        val map = readSnoozedMap(ctx)
        map[record.alarmId] = record
        writeSnoozedMap(ctx, map)
    }

    fun removeSnoozedAlarm(ctx: Context, alarmId: String) {
        val map = readSnoozedMap(ctx)
        if (map.remove(alarmId) != null) writeSnoozedMap(ctx, map)
    }

    fun getAllSnoozedAlarms(ctx: Context): List<SnoozedAlarmRecord> =
        readSnoozedMap(ctx).values.sortedBy { it.scheduledTimeMs }

    fun getSnoozedAlarm(ctx: Context, alarmId: String): SnoozedAlarmRecord? =
        readSnoozedMap(ctx)[alarmId]

    // ---------------------------------------------------------------------
    // Missed Alarms (F.15) — stored as Map<alarmId, MissedAlarmRecord>
    // ---------------------------------------------------------------------

    private fun readMissedMap(ctx: Context): MutableMap<String, MissedAlarmRecord> {
        val json = prefs(ctx).getString(KEY_MISSED_ALARMS, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, MissedAlarmRecord>>() {}.type
            gson.fromJson<MutableMap<String, MissedAlarmRecord>>(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeMissedMap(ctx: Context, map: Map<String, MissedAlarmRecord>) {
        prefs(ctx).edit().putString(KEY_MISSED_ALARMS, gson.toJson(map)).apply()
    }

    /**
     * Files an alarm as missed. Also clears any snooze record for it: the alarm
     * is over, and leaving both would list it twice.
     */
    fun saveMissedAlarm(ctx: Context, record: MissedAlarmRecord) {
        val map = readMissedMap(ctx)
        map[record.alarmId] = record
        writeMissedMap(ctx, map)
        removeSnoozedAlarm(ctx, record.alarmId)
    }

    fun removeMissedAlarm(ctx: Context, alarmId: String) {
        val map = readMissedMap(ctx)
        if (map.remove(alarmId) != null) writeMissedMap(ctx, map)
    }

    /** Most recently missed first — the opposite order to snoozed alarms. */
    fun getAllMissedAlarms(ctx: Context): List<MissedAlarmRecord> =
        readMissedMap(ctx).values.sortedByDescending { it.missedAtMs }

    // ---------------------------------------------------------------------
    // Auto-snooze retry counter
    // ---------------------------------------------------------------------

    fun getAutoSnoozeCount(ctx: Context, alarmId: String): Int =
        prefs(ctx).getInt(KEY_AUTO_SNOOZE_PREFIX + alarmId, 0)

    fun incrementAutoSnoozeCount(ctx: Context, alarmId: String): Int {
        val next = getAutoSnoozeCount(ctx, alarmId) + 1
        prefs(ctx).edit().putInt(KEY_AUTO_SNOOZE_PREFIX + alarmId, next).apply()
        return next
    }

    fun resetAutoSnoozeCount(ctx: Context, alarmId: String) {
        prefs(ctx).edit().remove(KEY_AUTO_SNOOZE_PREFIX + alarmId).apply()
    }

    // ---------------------------------------------------------------------
    // Calendar packages
    // ---------------------------------------------------------------------

    /**
     * Round 20 — the watched set now defaults to whichever known calendar apps
     * are actually on the phone, rather than a hardcoded three. On a Xiaomi or a
     * Huawei the old default matched nothing, so no notification was ever
     * intercepted while every permission still reported green.
     */
    fun getCalendarPackages(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_CALENDAR_PACKAGES, null)
            ?: CalendarApps.defaultsFor(ctx)

    fun saveCalendarPackages(ctx: Context, packages: Set<String>) {
        prefs(ctx).edit().putStringSet(KEY_CALENDAR_PACKAGES, packages).apply()
    }
}
