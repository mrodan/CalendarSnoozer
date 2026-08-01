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

class AlarmService : Service() {

    companion object {
        const val ACTION_STOP = "com.calendareventsnooze.ACTION_STOP"
        // Broadcast sent when an alarm has been resolved (stopped / auto-dismissed /
        // auto-snoozed) so AlarmActivity can close itself. In-app only.
        const val ACTION_ALARM_RESOLVED = "com.calendareventsnooze.ALARM_RESOLVED"
        const val ACTIVE_ALARM_NOTIF_ID = 1002

        fun cancelActiveAlarmNotification(context: Context) {
            context.getSystemService(NotificationManager::class.java)
                .cancel(ACTIVE_ALARM_NOTIF_ID)
        }

        /** Ask the running alarm service (if any) to stop. */
        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, AlarmService::class.java).apply {
                    action = ACTION_STOP
                })
            }
        }

        /**
         * Emergency stop for the Home-screen "Force Stop" button: silence any alarm,
         * clear every notification, and stop the service. The caller may additionally
         * kill the process for a guaranteed reset.
         */
        fun forceStopEverything(context: Context) {
            runCatching {
                context.getSystemService(NotificationManager::class.java).cancelAll()
            }
            stop(context)
            runCatching { context.stopService(Intent(context, AlarmService::class.java)) }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentAlarmEvent: AlarmEvent? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }

        // A null intent means a START_STICKY restart with no alarm data — there is
        // nothing to ring, so stop quietly (prevents "ghost" restarts).
        val alarmEvent = intent?.getAlarmEvent() ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        // B.2 — never let a previous alarm's sound/vibration/handler callbacks bleed
        // into a new one. Tear everything down before starting fresh.
        handler.removeCallbacksAndMessages(null)
        stopSoundAndVibration()

        currentAlarmEvent = alarmEvent

        // Single ongoing, high-priority, full-screen-intent notification. It is both
        // the foreground-service notification AND the sticky "return to alarm"
        // notification (F.3). The full-screen intent re-surfaces the takeover even if
        // the OS blocks a background activity start (fixes B.2's "sound with no screen").
        startForeground(ACTIVE_ALARM_NOTIF_ID, buildAlarmNotification(alarmEvent))

        val audioManager = getSystemService(AudioManager::class.java)
        val ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL  -> RingerMode.SOUND_ON
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else                             -> RingerMode.SILENT
        }
        val profile = AppPrefs.getSoundProfile(applicationContext, ringerMode)

        startAlarmOutput(profile)

        if (profile.autoDismissSeconds > 0) {
            handler.postDelayed(
                { handleAutoDismiss(profile, alarmEvent) },
                profile.autoDismissSeconds * 1000L
            )
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Intentionally empty — alarm must survive task removal
    }

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
        return NotificationCompat.Builder(this, "ces_alarm_active")
            .setContentTitle("⚠ Calendar Alarm Active")
            .setContentText(alarmEvent.eventTitle.ifBlank { "Tap to open the alarm" })
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator?.vibrate(VibrationEffect.createWaveform(
                profile.vibrationPattern, profile.vibrationRepeat))
        else @Suppress("DEPRECATION")
            vibrator?.vibrate(profile.vibrationPattern, profile.vibrationRepeat)
    }

    private fun handleAutoDismiss(profile: SoundProfile, alarmEvent: AlarmEvent) {
        // Cancel any still-pending delayed output before resolving.
        handler.removeCallbacksAndMessages(null)
        stopSoundAndVibration()
        cancelActiveAlarmNotification(applicationContext)

        // Special case: maxRetries == 0 means dismiss immediately, no snooze
        if (profile.autoDismissMaxRetries == 0 ||
            profile.autoDismissAction == AutoDismissAction.DISMISS) {
            AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
            AppPrefs.removeSnoozedAlarm(applicationContext, alarmEvent.alarmId)
            showMissedNotification(alarmEvent.eventTitle)
            finishService()
            return
        }

        val retryCount = AppPrefs.incrementAutoSnoozeCount(
            applicationContext, alarmEvent.alarmId)

        if (retryCount > profile.autoDismissMaxRetries) {
            // Max retries exceeded — dismiss completely
            AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
            AppPrefs.removeSnoozedAlarm(applicationContext, alarmEvent.alarmId)
            showMissedNotification(alarmEvent.eventTitle)
            finishService()
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
            finishService()
        }
    }

    fun stopAlarm() {
        handler.removeCallbacksAndMessages(null)
        stopSoundAndVibration()
        cancelActiveAlarmNotification(applicationContext)
        finishService()
    }

    /** Notify any visible AlarmActivity that the alarm is over, then stop. */
    private fun finishService() {
        notifyResolved()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyResolved() {
        runCatching {
            sendBroadcast(Intent(ACTION_ALARM_RESOLVED).setPackage(packageName))
        }
    }

    private fun stopSoundAndVibration() {
        runCatching { mediaPlayer?.stop(); mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        vibrator = null
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
        handler.removeCallbacksAndMessages(null)
        stopSoundAndVibration()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
