package com.calendareventsnooze.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Vibrator repeat index meaning "play the waveform once". */
const val NO_REPEAT = -1

/** The system vibrator, via whichever API this SDK level exposes. */
fun vibratorOf(context: Context): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

/**
 * How long a once-through [waveform] takes, in ms — the initial delay plus
 * every on and off segment. F.18 uses it to know when a test has finished so
 * the button can go back to saying "Test Vibration"; the vibrator itself gives
 * no completion callback.
 */
fun waveformDurationMs(waveform: LongArray): Long = waveform.sum()

/** Plays [waveform] exactly once. Shared by the alarm and the F.9 test button. */
fun Vibrator.playOnce(waveform: LongArray) = play(waveform, NO_REPEAT)

/**
 * Plays [waveform] with the vibrator's own [repeat] index — `-1` for once,
 * `0` to loop from the start.
 *
 * Looping is only ever used by a UI.25 preset, which is meant to buzz for as
 * long as the alarm lasts; a custom buzz unrolls its repetitions into the array
 * instead (trap 6).
 */
fun Vibrator.play(waveform: LongArray, repeat: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrate(VibrationEffect.createWaveform(waveform, repeat))
    } else {
        @Suppress("DEPRECATION")
        vibrate(waveform, repeat)
    }
}
