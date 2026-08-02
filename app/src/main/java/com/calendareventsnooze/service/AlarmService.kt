package com.calendareventsnooze.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AlarmEvent
import com.calendareventsnooze.model.AutoDismissAction
import com.calendareventsnooze.model.RingerMode
import com.calendareventsnooze.model.SnoozedAlarmRecord
import com.calendareventsnooze.model.SoundProfile
import com.calendareventsnooze.scheduler.AlarmScheduler
import com.calendareventsnooze.ui.AlarmActivity
import com.calendareventsnooze.util.getAlarmEvent
import com.calendareventsnooze.util.putAlarmEvent

/**
 * Owns every currently-ringing alarm.
 *
 * B.6 — alarms are held in a **stack**. A newly triggered alarm takes over the
 * screen and the audio, but the one it interrupted stays alive underneath;
 * resolving the top alarm (snooze / dismiss / auto-dismiss) brings the previous
 * one back to the foreground. Nothing is ever discarded without the user acting
 * on it.
 */
class AlarmService : Service() {

    companion object {
        /** Stops every alarm at once (Force Stop). */
        const val ACTION_STOP = "com.calendareventsnooze.ACTION_STOP"

        /** Resolves ONE alarm (by [EXTRA_ALARM_ID]) and resumes the one beneath it. */
        const val ACTION_RESOLVE_ALARM = "com.calendareventsnooze.ACTION_RESOLVE_ALARM"

        /** Broadcast: no alarms remain, the takeover screen may close. */
        const val ACTION_ALARM_RESOLVED = "com.calendareventsnooze.ALARM_RESOLVED"

        const val EXTRA_ALARM_ID = "ces_resolved_alarm_id"
        const val ACTIVE_ALARM_NOTIF_ID = 1002

        /** Vibrator repeat index meaning "play the waveform once". */
        private const val NO_REPEAT = -1

        fun cancelActiveAlarmNotification(context: Context) {
            context.getSystemService(NotificationManager::class.java)
                .cancel(ACTIVE_ALARM_NOTIF_ID)
        }

        /** The user acted on [alarmId]; hand control back to any alarm beneath it. */
        fun resolveAlarm(context: Context, alarmId: String) {
            runCatching {
                context.startService(Intent(context, AlarmService::class.java).apply {
                    action = ACTION_RESOLVE_ALARM
                    putExtra(EXTRA_ALARM_ID, alarmId)
                })
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, AlarmService::class.java).apply {
                    action = ACTION_STOP
                })
            }
        }

        /**
         * Emergency stop for the Home-screen "Force Stop" button: silence everything,
         * clear every notification, and stop the service.
         */
        fun forceStopEverything(context: Context) {
            runCatching {
                context.getSystemService(NotificationManager::class.java).cancelAll()
            }
            stop(context)
            runCatching { context.stopService(Intent(context, AlarmService::class.java)) }
        }
    }

    /** Ringing alarms, oldest first. The last entry owns the screen and the audio. */
    private val alarmStack = mutableListOf<AlarmEvent>()

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_RESOLVE_ALARM -> {
                val alarmId = intent.getStringExtra(EXTRA_ALARM_ID)
                if (alarmId != null) resolveAndAdvance(alarmId) else stopEverything()
                return START_STICKY
            }
        }

        // A null intent means a START_STICKY restart with no alarm data — there is
        // nothing to ring, so stop quietly (prevents "ghost" restarts).
        val alarmEvent = intent?.getAlarmEvent() ?: run {
            if (alarmStack.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }

        // B.6 — a new alarm interrupts the current one instead of replacing it.
        // Silence whatever is ringing, then push the newcomer on top.
        silenceCurrentOutput()
        alarmStack.removeAll { it.alarmId == alarmEvent.alarmId } // guard re-delivery
        alarmStack.add(alarmEvent)

        // The caller (receiver / notification listener) already launched the
        // takeover screen for this alarm, so we only take over sound + notification.
        presentTopAlarm(launchActivity = false)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Intentionally empty — alarm must survive task removal
    }

    // ------------------------------------------------------------------
    // Alarm stack
    // ------------------------------------------------------------------

    /** Starts sound, notification and auto-dismiss for whatever is on top. */
    private fun presentTopAlarm(launchActivity: Boolean) {
        val alarmEvent = alarmStack.lastOrNull() ?: return

        startForeground(ACTIVE_ALARM_NOTIF_ID, buildAlarmNotification(alarmEvent))

        if (launchActivity) {
            runCatching {
                startActivity(
                    Intent(this, AlarmActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }.putAlarmEvent(alarmEvent)
                )
            }
        }

        val profile = currentProfile()
        startAlarmOutput(profile)

        if (profile.autoDismissSeconds > 0) {
            handler.postDelayed(
                { handleAutoDismiss(profile, alarmEvent) },
                profile.autoDismissSeconds * 1000L
            )
        }
    }

    /**
     * The user (or auto-dismiss) finished with [alarmId]: drop it from the stack
     * and either resume the alarm underneath or shut the service down.
     */
    private fun resolveAndAdvance(alarmId: String) {
        silenceCurrentOutput()
        alarmStack.removeAll { it.alarmId == alarmId }

        if (alarmStack.isEmpty()) {
            notifyResolved(alarmId)
            cancelActiveAlarmNotification(applicationContext)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            // Bring the interrupted alarm back so the user can act on it (B.6).
            presentTopAlarm(launchActivity = true)
        }
    }

    /** Silences audio/vibration and cancels any pending auto-dismiss. */
    private fun silenceCurrentOutput() {
        handler.removeCallbacksAndMessages(null)
        stopSoundAndVibration()
    }

    private fun stopEverything() {
        silenceCurrentOutput()
        val last = alarmStack.lastOrNull()?.alarmId
        alarmStack.clear()
        notifyResolved(last)
        cancelActiveAlarmNotification(applicationContext)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyResolved(alarmId: String?) {
        runCatching {
            sendBroadcast(Intent(ACTION_ALARM_RESOLVED).apply {
                setPackage(packageName)
                putExtra(EXTRA_ALARM_ID, alarmId)
            })
        }
    }

    private fun currentProfile(): SoundProfile {
        val audioManager = getSystemService(AudioManager::class.java)
        val ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL  -> RingerMode.SOUND_ON
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else                             -> RingerMode.SILENT
        }
        return AppPrefs.getSoundProfile(applicationContext, ringerMode)
    }

    // ------------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------------

    private fun alarmPendingIntent(alarmEvent: AlarmEvent): PendingIntent {
        val returnIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }.putAlarmEvent(alarmEvent)
        return PendingIntent.getActivity(
            this, ACTIVE_ALARM_NOTIF_ID, returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildAlarmNotification(alarmEvent: AlarmEvent): Notification {
        val pi = alarmPendingIntent(alarmEvent)
        val waiting = alarmStack.size - 1
        val subText = if (waiting > 0) "+$waiting more alarm${if (waiting > 1) "s" else ""} waiting"
                      else null
        return NotificationCompat.Builder(this, "ces_alarm_active")
            .setContentTitle("⚠ Calendar Alarm Active")
            .setContentText(alarmEvent.eventTitle.ifBlank { "Tap to open the alarm" })
            .setSubText(subText)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    // ------------------------------------------------------------------
    // Sound & vibration
    // ------------------------------------------------------------------

    private fun startAlarmOutput(profile: SoundProfile) {
        if (profile.soundStartsFirst) {
            if (profile.soundEnabled) startSound(profile)
            if (profile.vibrationEnabled) {
                if (profile.secondStartDelaySeconds > 0)
                    handler.postDelayed({ startVibration(profile) },
                        profile.secondStartDelaySeconds * 1000L)
                else startVibration(profile)
            }
        } else {
            if (profile.vibrationEnabled) startVibration(profile)
            if (profile.soundEnabled) {
                if (profile.secondStartDelaySeconds > 0)
                    handler.postDelayed({ startSound(profile) },
                        profile.secondStartDelaySeconds * 1000L)
                else startSound(profile)
            }
        }
    }

    private fun startSound(profile: SoundProfile) {
        try {
            val uri = if (!profile.soundUri.isNullOrEmpty()) Uri.parse(profile.soundUri)
                      else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                setDataSource(applicationContext, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) { /* log and continue */ }
    }

    private fun startVibration(profile: SoundProfile) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator

        // F.7 — the buzz/pattern sliders are expanded into one finite waveform,
        // repetitions included, so the vibrator's repeat index stays at
        // NO_REPEAT (it would otherwise loop forever).
        val pattern = profile.buildVibrationWaveform()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, NO_REPEAT))
        else @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, NO_REPEAT)
    }

    private fun stopSoundAndVibration() {
        runCatching { mediaPlayer?.stop(); mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    // ------------------------------------------------------------------
    // Auto-dismiss / auto-snooze
    // ------------------------------------------------------------------

    private fun handleAutoDismiss(profile: SoundProfile, alarmEvent: AlarmEvent) {
        // Special case: maxRetries == 0 means dismiss immediately, no snooze
        if (profile.autoDismissMaxRetries == 0 ||
            profile.autoDismissAction == AutoDismissAction.DISMISS) {
            AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
            AppPrefs.removeSnoozedAlarm(applicationContext, alarmEvent.alarmId)
            showMissedNotification(alarmEvent.eventTitle)
            resolveAndAdvance(alarmEvent.alarmId)
            return
        }

        val retryCount = AppPrefs.incrementAutoSnoozeCount(
            applicationContext, alarmEvent.alarmId)

        if (retryCount > profile.autoDismissMaxRetries) {
            // Max retries exceeded — dismiss completely
            AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
            AppPrefs.removeSnoozedAlarm(applicationContext, alarmEvent.alarmId)
            showMissedNotification(alarmEvent.eventTitle)
        } else {
            // Auto-snooze — schedule and save to snoozed list
            val snoozeMs = System.currentTimeMillis() +
                           profile.autoDismissSnoozeMinutes * 60_000L
            AlarmScheduler.scheduleAt(applicationContext, alarmEvent, snoozeMs)
            AppPrefs.saveSnoozedAlarm(applicationContext,
                SnoozedAlarmRecord(
                    alarmId    = alarmEvent.alarmId,
                    eventTitle = alarmEvent.eventTitle,
                    eventText  = alarmEvent.eventText,
                    eventId    = alarmEvent.eventId,
                    eventTimeMs = alarmEvent.eventTimeMs,
                    scheduledTimeMs = snoozeMs
                ))
        }
        resolveAndAdvance(alarmEvent.alarmId)
    }

    private fun showMissedNotification(title: String) {
        val notif = NotificationCompat.Builder(this, "ces_missed")
            .setContentTitle("Missed Calendar Alarm")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notif)
    }

    override fun onDestroy() {
        silenceCurrentOutput()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
