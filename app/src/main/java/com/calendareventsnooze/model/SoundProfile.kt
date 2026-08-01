package com.calendareventsnooze.model

enum class RingerMode { SOUND_ON, VIBRATE, SILENT }
enum class AutoDismissAction { DISMISS, SNOOZE }

data class SoundProfile(
    val ringerMode: RingerMode,
    val soundEnabled: Boolean,
    val soundUri: String?,            // null = use system default alarm sound
    val vibrationEnabled: Boolean,
    val vibrationPattern: LongArray,  // ms: [delay, on, off, on, ...] e.g. [0,500,200,500]
    val vibrationRepeat: Int,         // -1 = no repeat; 0 = repeat from index 0
    val vibrationRepetitions: Int,    // F.7 — how many times the pattern plays (min 1)
    val soundStartsFirst: Boolean,
    val secondStartDelaySeconds: Int, // 0 = start both simultaneously
    val autoDismissSeconds: Int,      // 0 = never auto-dismiss
    val autoDismissAction: AutoDismissAction,
    val autoDismissSnoozeMinutes: Int,
    val autoDismissMaxRetries: Int    // 0 = auto-dismiss immediately, no snooze attempt
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SoundProfile
        return ringerMode == other.ringerMode &&
            soundEnabled == other.soundEnabled &&
            soundUri == other.soundUri &&
            vibrationEnabled == other.vibrationEnabled &&
            vibrationPattern.contentEquals(other.vibrationPattern) &&
            vibrationRepeat == other.vibrationRepeat &&
            vibrationRepetitions == other.vibrationRepetitions &&
            soundStartsFirst == other.soundStartsFirst &&
            secondStartDelaySeconds == other.secondStartDelaySeconds &&
            autoDismissSeconds == other.autoDismissSeconds &&
            autoDismissAction == other.autoDismissAction &&
            autoDismissSnoozeMinutes == other.autoDismissSnoozeMinutes &&
            autoDismissMaxRetries == other.autoDismissMaxRetries
    }

    override fun hashCode(): Int {
        var result = ringerMode.hashCode()
        result = 31 * result + soundEnabled.hashCode()
        result = 31 * result + (soundUri?.hashCode() ?: 0)
        result = 31 * result + vibrationEnabled.hashCode()
        result = 31 * result + vibrationPattern.contentHashCode()
        result = 31 * result + vibrationRepeat
        result = 31 * result + vibrationRepetitions
        result = 31 * result + soundStartsFirst.hashCode()
        result = 31 * result + secondStartDelaySeconds
        result = 31 * result + autoDismissSeconds
        result = 31 * result + autoDismissAction.hashCode()
        result = 31 * result + autoDismissSnoozeMinutes
        result = 31 * result + autoDismissMaxRetries
        return result
    }
}
