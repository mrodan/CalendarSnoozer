package com.calendareventsnooze.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import com.calendareventsnooze.model.SoundProfile

/**
 * UI.25.3 — plays the chosen alarm sound once so it can be heard from the
 * settings screen, the way "Test Vibration" plays the buzz.
 *
 * It overrides the phone's ALARM stream exactly as the real alarm does (F.10),
 * because `MediaPlayer.setVolume` only scales *within* the current stream
 * volume and would preview the wrong thing. That makes restoring mandatory:
 * [stop] is the single exit and puts the level back, and every caller — the
 * Stop Test button, playback finishing, and leaving the screen — goes through
 * it (trap 15).
 */
class SoundTest(private val context: Context) {

    private var player: MediaPlayer? = null
    private var previousVolume: Int? = null

    val isPlaying: Boolean get() = player != null

    /**
     * Starts playback and calls [onFinished] when the sound ends on its own.
     * Returns false if the sound could not be played at all.
     */
    fun start(profile: SoundProfile, onFinished: () -> Unit): Boolean {
        stop()
        return try {
            val uri = if (!profile.soundUri.isNullOrEmpty()) Uri.parse(profile.soundUri)
                      else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            applyVolume(profile.alarmVolumePercent)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context.applicationContext, uri)
                // Deliberately not looping: a test has to end by itself even if
                // the user walks away from the screen.
                isLooping = false
                setOnCompletionListener { stop(); onFinished() }
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        restoreVolume()
    }

    private fun applyVolume(percent: Int) {
        runCatching {
            val audio = context.getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (previousVolume == null) {
                previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            }
            val target = (max * percent / 100f).toInt().coerceIn(1, max)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        }
    }

    private fun restoreVolume() {
        val previous = previousVolume ?: return
        previousVolume = null
        runCatching {
            context.getSystemService(AudioManager::class.java)
                .setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
        }
    }
}
