package com.calendareventsnooze.util

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.calendareventsnooze.model.RingerMode

/**
 * Which of the three sound profiles the phone is currently asking for.
 *
 * **`AudioManager.getRingerMode()` is not the answer, and that was a bug.**
 * Android keeps two ringer modes: the *internal* one, which is what the user
 * actually set, and the *external* one, which is what apps are told. Turning on
 * Do Not Disturb forces the external one to SILENT while the internal one is
 * untouched. `getRingerMode()` returns the external value, so with DND on the
 * app chose the SILENT profile no matter how the phone was set — permanently,
 * for anyone who keeps DND on a schedule.
 *
 * Measured on the Pixel 10a with DND on and the phone on Sound:
 *
 *     Ringer mode:
 *       - mode (internal) = NORMAL
 *       - mode (external) = SILENT
 *       - zen mode: ZEN_MODE_IMPORTANT_INTERRUPTIONS
 *
 * `getRingerModeInternal()` is the API for this and is not public, so the
 * internal value is read from the setting `AudioService` persists it to. That
 * setting is the framework's own record of the internal mode and reads the same
 * whether DND is on or off, which makes it the right primary source rather than
 * a DND-only special case. `getRingerMode()` stays as the fallback for any
 * device that does not expose it.
 *
 * Alarms are deliberately **not** silenced by DND here. DND governs
 * interruptions; an alarm the user asked for is not one, which is why the
 * platform exempts the alarm stream too. Quiet hours are the app's own Silent
 * Hours & Days setting.
 */
object RingerModes {

    /** `Settings.Global.MODE_RINGER` — public as a value, not as a constant. */
    private const val SETTING_MODE_RINGER = "mode_ringer"

    fun current(context: Context): RingerMode =
        fromPersistedInternalMode(context) ?: fromAudioManager(context)

    /** Null when the setting is missing or holds something unexpected. */
    private fun fromPersistedInternalMode(context: Context): RingerMode? = runCatching {
        toRingerMode(
            Settings.Global.getInt(context.contentResolver, SETTING_MODE_RINGER)
        )
    }.getOrNull()

    private fun fromAudioManager(context: Context): RingerMode =
        toRingerMode(context.getSystemService(AudioManager::class.java)?.ringerMode)
            ?: RingerMode.SILENT

    private fun toRingerMode(value: Int?): RingerMode? = when (value) {
        AudioManager.RINGER_MODE_NORMAL  -> RingerMode.SOUND_ON
        AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
        AudioManager.RINGER_MODE_SILENT  -> RingerMode.SILENT
        else -> null
    }
}
