package com.calendareventsnooze.model

enum class RingerMode { SOUND_ON, VIBRATE, SILENT }
enum class AutoDismissAction { DISMISS, SNOOZE }

/**
 * UI.25 — the vibration character, chosen from a menu instead of five sliders.
 *
 * The four named presets are a single buzz length repeated for as long as the
 * alarm lasts; only [CUSTOM] uses the pattern/repetition machinery behind
 * "Customize your Buzz".
 */
enum class VibrationPreset(val label: String) {
    LITTLE("Little Buzzes"),
    MEDIUM("Medium Buzzes"),
    LONG("Long Buzzes"),
    XXL("XXL Buzzes"),
    CUSTOM("Custom Buzz");

    /** Buzz-on / buzz-off lengths in ms, taken from the same S…XXL stops. */
    val buzzOnMs: Int
        get() = when (this) {
            LITTLE -> SoundProfile.BUZZ_LENGTH_OPTIONS[0]  // S
            MEDIUM -> SoundProfile.BUZZ_LENGTH_OPTIONS[1]  // M
            LONG   -> SoundProfile.BUZZ_LENGTH_OPTIONS[3]  // XL
            XXL    -> SoundProfile.BUZZ_LENGTH_OPTIONS[4]  // XXL
            CUSTOM -> SoundProfile.BUZZ_LENGTH_OPTIONS[1]
        }

    val buzzOffMs: Int
        get() = when (this) {
            LITTLE -> SoundProfile.BUZZ_LENGTH_OPTIONS[0]  // S
            MEDIUM -> SoundProfile.BUZZ_LENGTH_OPTIONS[1]  // M
            LONG   -> SoundProfile.BUZZ_LENGTH_OPTIONS[2]  // L
            XXL    -> SoundProfile.BUZZ_LENGTH_OPTIONS[3]  // XL
            CUSTOM -> SoundProfile.BUZZ_LENGTH_OPTIONS[1]
        }
}

data class SoundProfile(
    val ringerMode: RingerMode,
    val soundEnabled: Boolean,
    val soundUri: String?,
    // F.10 / UI.21 — sound playback shaping.
    val alarmVolumePercent: Int,      // 1..100; overrides the phone's alarm volume
    val soundDelaySeconds: Int,       // 0 = start with the alarm
    val fadeInSeconds: Int,           // 0 = start at full volume
    val soundStopsAfterSeconds: Int,  // 0 = keep sounding until the alarm resolves
    val vibrationEnabled: Boolean,
    // UI.25 — which of the five vibration characters is selected. Everything
    // below (buzzOnMs … vibrationRepetitions) only applies when this is CUSTOM.
    val vibrationPreset: VibrationPreset,
    // UI.22 — the vibration equivalents of the two sound timings above.
    val vibrationDelaySeconds: Int,      // 0 = start with the alarm
    val vibrationStopsAfterSeconds: Int, // 0 = keep buzzing until the alarm resolves
    // F.7 — the waveform is described by these five values instead of a raw
    // comma-separated pattern. buildVibrationWaveform() turns them into the
    // LongArray the vibrator wants.
    val buzzOnMs: Int,               // how long one buzz vibrates
    val buzzOffMs: Int,              // gap between buzzes inside one pattern
    val buzzesPerPattern: Int,       // buzzes in one pattern (1..10)
    val delayBetweenPatternsMs: Int, // gap between pattern repetitions
    val vibrationRepetitions: Int,   // how many times the pattern plays (1..10)
    val soundStartsFirst: Boolean,
    val secondStartDelaySeconds: Int, // 0 = start both simultaneously
    val autoDismissSeconds: Int,      // 0 = never auto-dismiss
    val autoDismissAction: AutoDismissAction,
    val autoDismissSnoozeMinutes: Int,
    val autoDismissMaxRetries: Int    // 0 = auto-dismiss immediately, no snooze attempt
) {
    /**
     * Builds the `[delay, ON, off, ON, off, ...]` waveform.
     *
     * A waveform strictly alternates off/on starting with an off value, so the
     * gap after the last buzz of a pattern *is* the between-patterns delay —
     * two off values can never sit next to each other or the whole pattern
     * inverts from that point on.
     *
     * Repetitions are concatenated here rather than handled by the vibrator's
     * `repeat` index, which loops forever (see trap 6 in CLAUDE.md).
     */
    fun buildVibrationWaveform(): LongArray {
        // UI.25 — a preset is one buzz length alternating on/off, looped by
        // [vibrationRepeatIndex] rather than unrolled, because it has to last
        // as long as the alarm does.
        if (vibrationPreset != VibrationPreset.CUSTOM) {
            return longArrayOf(
                0L,
                vibrationPreset.buzzOnMs.toLong(),
                vibrationPreset.buzzOffMs.toLong()
            )
        }

        val buzzes = buzzesPerPattern.coerceIn(1, MAX_COUNT)
        val reps = vibrationRepetitions.coerceIn(1, MAX_COUNT)
        val on = buzzOnMs.toLong().coerceAtLeast(1L)
        val off = buzzOffMs.toLong().coerceAtLeast(0L)
        val between = delayBetweenPatternsMs.toLong().coerceAtLeast(0L)

        val out = ArrayList<Long>(1 + reps * buzzes * 2)
        out.add(0L) // no delay before the first buzz
        repeat(reps) { r ->
            repeat(buzzes) { b ->
                out.add(on)
                if (b < buzzes - 1) out.add(off)
            }
            if (r < reps - 1) out.add(between)
        }
        return out.toLongArray()
    }

    /**
     * The vibrator's `repeat` index for this profile's waveform.
     *
     * [REPEAT_NONE] for a custom buzz — its repetitions are already unrolled
     * into the array (trap 6). A preset returns [REPEAT_FROM_START], which is
     * the one place looping forever is what we want: the preset buzzes until
     * "Vibration Stops After" fires, the alarm auto-resolves, or the user acts,
     * and every one of those paths already cancels the vibrator.
     */
    fun vibrationRepeatIndex(): Int =
        if (vibrationPreset == VibrationPreset.CUSTOM) REPEAT_NONE else REPEAT_FROM_START

    companion object {
        /** Vibrator repeat indices: play once, or loop from the first element. */
        const val REPEAT_NONE = -1
        const val REPEAT_FROM_START = 0

        /** Upper bound of the "number of buzzes" / "repetitions" sliders. */
        const val MAX_COUNT = 10

        /**
         * F.10 — the alarm volume slider never reaches silence; "Enable sound
         * alarm" is what turns sound off, and a 0% alarm would be inaudible
         * with nothing on screen explaining why.
         *
         * UI.25.5 — it moves in steps of 10, so the lowest stop is 10%.
         */
        const val MIN_VOLUME_PERCENT = 10
        const val MAX_VOLUME_PERCENT = 100
        const val VOLUME_STEP_PERCENT = 10

        /** Snaps a stored volume onto the nearest UI.25.5 stop. */
        fun snapVolume(percent: Int): Int =
            (Math.round(percent / VOLUME_STEP_PERCENT.toFloat()) * VOLUME_STEP_PERCENT)
                .coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)

        /** Buzz-On / Buzz-Off slider stops, in ms (S, M, L, XL, XXL). */
        val BUZZ_LENGTH_OPTIONS = listOf(250, 500, 1000, 2000, 3000)

        /** Delay-Between-Patterns slider stops, in ms (S, M, L, XL, XXL). */
        val PATTERN_DELAY_OPTIONS = listOf(500, 1000, 2000, 3000, 5000)

        /** Labels shared by all three millisecond sliders. */
        val SIZE_LABELS = listOf("S", "M", "L", "XL", "XXL")
    }
}
