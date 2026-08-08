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
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AlarmEvent
import com.calendareventsnooze.model.AutoDismissAction
import com.calendareventsnooze.model.MissedAlarmRecord
import com.calendareventsnooze.model.RingerMode
import com.calendareventsnooze.model.SnoozedAlarmRecord
import com.calendareventsnooze.model.SoundProfile
import com.calendareventsnooze.scheduler.AlarmScheduler
import com.calendareventsnooze.ui.AlarmActivity
import com.calendareventsnooze.util.getAlarmEvent
import com.calendareventsnooze.util.playOnce
import com.calendareventsnooze.util.putAlarmEvent
import com.calendareventsnooze.util.vibratorOf

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

        /**
         * F.16 — "Shhhh": silences sound and vibration but leaves the alarm
         * itself running. The takeover stays up, the notification stays put and
         * the auto-snooze countdown keeps ticking.
         */
        const val ACTION_SILENCE = "com.calendareventsnooze.ACTION_SILENCE"

        /** Silences the current alarm's output without resolving it (F.16). */
        fun silenceAlarm(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AlarmService::class.java).setAction(ACTION_SILENCE)
                )
            }
        }

        /**
         * B.6 — the user swiped the active-alarm notification away. Since Android
         * 13 a foreground-service notification is dismissible no matter what
         * `setOngoing` says, so the only way to keep it up until the alarm is
         * actually resolved is to notice the dismissal and post it again.
         */
        const val ACTION_REASSERT_NOTIFICATION =
            "com.calendareventsnooze.ACTION_REASSERT_NOTIFICATION"

        /** Broadcast: no alarms remain, the takeover screen may close. */
        const val ACTION_ALARM_RESOLVED = "com.calendareventsnooze.ALARM_RESOLVED"

        const val EXTRA_ALARM_ID = "ces_resolved_alarm_id"
        const val ACTIVE_ALARM_NOTIF_ID = 1002

        /** Volume steps per second while fading in (F.10). */
        private const val FADE_STEPS_PER_SECOND = 10

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

    /** True from the moment teardown begins, so it ignores its own delete intent. */
    private var resolving = false

    /** F.10 — the phone's alarm volume before we overrode it, to restore after. */
    private var previousAlarmVolume: Int? = null

    /** F.16 — the user hit "Shhhh"; suppress output without ending the alarm. */
    private var silenced = false

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
            ACTION_SILENCE -> {
                // F.16 — deliberately does NOT touch the handler queue: the
                // auto-snooze countdown must survive. `silenced` instead makes
                // any pending delayed start (sequencing, per-output delay) a
                // no-op when it fires.
                silenced = true
                stopSoundAndVibration()
                return START_STICKY
            }
            ACTION_REASSERT_NOTIFICATION -> {
                // B.6 — swiped away while an alarm is still unresolved: put it back.
                val top = if (resolving) null else alarmStack.lastOrNull()
                if (top != null) {
                    startForeground(ACTIVE_ALARM_NOTIF_ID, buildAlarmNotification(top))
                    return START_STICKY
                }
                // Nothing left to ring: the dismissal raced a resolution, or this is
                // the teardown's own delete intent. Leaving the service started
                // without calling startForeground is itself a violation on
                // Android 14+, so clear up and go away.
                cancelActiveAlarmNotification(applicationContext)
                stopSelf()
                return START_NOT_STICKY
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
        resolving = false
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
        // A newly presented alarm always starts audible, even if the one before
        // it had been silenced (F.16).
        silenced = false

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
            tearDown(alarmId)
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
        // F.15 — Force Stop kills alarms the user never resolved, so every one
        // still on the stack counts as missed.
        alarmStack.forEach { recordMissed(it) }
        alarmStack.clear()
        tearDown(last)
    }

    /**
     * Shuts the service down and takes the notification with it.
     *
     * **Order matters.** Cancelling first and detaching second leaves the
     * notification stranded on Android 17: the B.6 hardening puts FLAG_NO_CLEAR
     * on it, and a no-clear notification is not removed by
     * `stopForeground(STOP_FOREGROUND_REMOVE)`. The result was a permanent
     * "Calendar Alarm Active" entry that the user could not even swipe away,
     * because no-clear is exactly what stops them. So: leave the foreground
     * state first, *then* cancel, and cancel once more from onDestroy as a
     * backstop.
     *
     * [resolving] makes the teardown ignore its own delete intent — detaching
     * the notification can look like a user dismissal, which would otherwise
     * re-post it.
     */
    private fun tearDown(resolvedAlarmId: String?) {
        resolving = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        cancelActiveAlarmNotification(applicationContext)
        notifyResolved(resolvedAlarmId)
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

    /**
     * B.6 — fires when the user swipes the notification away, so the service can
     * put it straight back while the alarm is still unresolved.
     */
    private fun reassertPendingIntent(): PendingIntent {
        val intent = Intent(this, AlarmService::class.java)
            .setAction(ACTION_REASSERT_NOTIFICATION)
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildAlarmNotification(alarmEvent: AlarmEvent): Notification {
        val pi = alarmPendingIntent(alarmEvent)
        val waiting = alarmStack.size - 1
        val subText = if (waiting > 0) "+$waiting more alarm${if (waiting > 1) "s" else ""} waiting"
                      else null
        val notification = NotificationCompat.Builder(this, "ces_alarm_active")
            .setContentTitle("⚠ Calendar Alarm Active")
            .setContentText(alarmEvent.eventTitle.ifBlank { "Tap to open the alarm" })
            .setSubText(subText)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setDeleteIntent(reassertPendingIntent())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        // B.6 — three layers, because none of them is sufficient alone:
        // ongoing hides the swipe affordance, NO_CLEAR survives "Clear all", and
        // the delete intent re-posts if the system lets it through anyway
        // (Android 13+ allows dismissing foreground-service notifications).
        // Verified on Android 17: this still clears correctly on resolve.
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
        return notification
    }

    // ------------------------------------------------------------------
    // Sound & vibration
    // ------------------------------------------------------------------

    /**
     * Works out when each output starts and schedules it.
     *
     * Two things stack here. Sequencing decides which output goes first and how
     * long the *other* one waits; UI.21/UI.22 then add a per-output delay on top
     * of that. Both default to 0, so a profile that has never touched them
     * behaves exactly as it did before.
     */
    private fun startAlarmOutput(profile: SoundProfile) {
        val sequencingGap = profile.secondStartDelaySeconds.coerceAtLeast(0)
        val soundOffset = profile.soundDelaySeconds.coerceAtLeast(0) +
            (if (profile.soundStartsFirst) 0 else sequencingGap)
        val vibrationOffset = profile.vibrationDelaySeconds.coerceAtLeast(0) +
            (if (profile.soundStartsFirst) sequencingGap else 0)

        if (profile.soundEnabled) startAfter(soundOffset) { startSound(profile) }
        if (profile.vibrationEnabled) startAfter(vibrationOffset) { startVibration(profile) }
    }

    /** Runs [block] now or after [seconds], unless the alarm has been silenced. */
    private fun startAfter(seconds: Int, block: () -> Unit) {
        if (seconds <= 0) {
            if (!silenced) block()
        } else {
            handler.postDelayed({ if (!silenced) block() }, seconds * 1000L)
        }
    }

    private fun startSound(profile: SoundProfile) {
        try {
            val uri = if (!profile.soundUri.isNullOrEmpty()) Uri.parse(profile.soundUri)
                      else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            applyAlarmVolume(profile)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                setDataSource(applicationContext, uri)
                isLooping = true
                prepare()
                // F.10 — fade in from silence rather than starting at full blast.
                if (profile.fadeInSeconds > 0) setVolume(0f, 0f)
                start()
            }
            if (profile.fadeInSeconds > 0) startFadeIn(profile.fadeInSeconds)
            // F.10 — optionally silence the sound after N seconds. The alarm
            // itself keeps running: the takeover stays up and vibration
            // continues, only the audio stops.
            if (profile.soundStopsAfterSeconds > 0) {
                handler.postDelayed(
                    { stopSoundOnly() },
                    profile.soundStopsAfterSeconds * 1000L
                )
            }
        } catch (e: Exception) { /* log and continue */ }
    }

    /**
     * F.10 — the slider is meant to override the phone's alarm volume, so it
     * sets the ALARM stream itself rather than merely attenuating below whatever
     * the phone happens to be at. The previous level is remembered and restored
     * when the alarm stops, so the phone is left as it was found.
     */
    private fun applyAlarmVolume(profile: SoundProfile) {
        runCatching {
            val audio = getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (previousAlarmVolume == null) {
                previousAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            }
            val percent = profile.alarmVolumePercent
                .coerceIn(SoundProfile.MIN_VOLUME_PERCENT, SoundProfile.MAX_VOLUME_PERCENT)
            val target = Math.round(max * percent / 100f).coerceIn(1, max)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        }
    }

    /** Puts the phone's alarm volume back exactly as it was. */
    private fun restoreAlarmVolume() {
        val previous = previousAlarmVolume ?: return
        previousAlarmVolume = null
        runCatching {
            getSystemService(AudioManager::class.java)
                .setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
        }
    }

    /** F.10 — ramps the player from silence to full over [seconds]. */
    private fun startFadeIn(seconds: Int) {
        val steps = (seconds * FADE_STEPS_PER_SECOND).coerceAtLeast(1)
        val stepMs = (seconds * 1000L) / steps
        for (step in 1..steps) {
            handler.postDelayed({
                val level = step.toFloat() / steps
                runCatching { mediaPlayer?.setVolume(level, level) }
            }, stepMs * step)
        }
    }

    /** Stops only the audio, leaving the alarm (and its vibration) running. */
    private fun stopSoundOnly() {
        runCatching { mediaPlayer?.stop(); mediaPlayer?.release() }
        mediaPlayer = null
        restoreAlarmVolume()
    }

    private fun startVibration(profile: SoundProfile) {
        vibrator = vibratorOf(this)
        // F.7 — the buzz/pattern sliders are expanded into one finite waveform,
        // repetitions included, so the vibrator's repeat index stays at
        // NO_REPEAT (it would otherwise loop forever).
        vibrator?.playOnce(profile.buildVibrationWaveform())
        // UI.22 — optionally cut the buzzing after N seconds. Like the sound
        // equivalent this leaves the alarm itself running.
        if (profile.vibrationStopsAfterSeconds > 0) {
            handler.postDelayed(
                { stopVibrationOnly() },
                profile.vibrationStopsAfterSeconds * 1000L
            )
        }
    }

    /** Stops only the buzzing, leaving the alarm (and its sound) running. */
    private fun stopVibrationOnly() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun stopSoundAndVibration() {
        runCatching { mediaPlayer?.stop(); mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        // F.10 — hand the phone's alarm volume back. This runs on every stop
        // path (resolve, interrupt, force-stop, onDestroy) so the override can
        // never outlive the alarm.
        restoreAlarmVolume()
    }

    /**
     * F.15 — files an alarm the user never acted on into the Missed list. Also
     * clears its snooze record, so it appears in exactly one of the two.
     */
    private fun recordMissed(alarmEvent: AlarmEvent) {
        AppPrefs.saveMissedAlarm(
            applicationContext,
            MissedAlarmRecord(
                alarmId     = alarmEvent.alarmId,
                eventTitle  = alarmEvent.eventTitle,
                eventText   = alarmEvent.eventText,
                eventId     = alarmEvent.eventId,
                eventTimeMs = alarmEvent.eventTimeMs,
                missedAtMs  = System.currentTimeMillis()
            )
        )
    }

    // ------------------------------------------------------------------
    // Auto-dismiss / auto-snooze
    // ------------------------------------------------------------------

    private fun handleAutoDismiss(profile: SoundProfile, alarmEvent: AlarmEvent) {
        // Special case: maxRetries == 0 means dismiss immediately, no snooze
        if (profile.autoDismissMaxRetries == 0 ||
            profile.autoDismissAction == AutoDismissAction.DISMISS) {
            AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
            recordMissed(alarmEvent)
            showMissedNotification(alarmEvent.eventTitle)
            resolveAndAdvance(alarmEvent.alarmId)
            return
        }

        val retryCount = AppPrefs.incrementAutoSnoozeCount(
            applicationContext, alarmEvent.alarmId)

        if (retryCount > profile.autoDismissMaxRetries) {
            // Max retries exceeded — dismiss completely
            AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
            recordMissed(alarmEvent)
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
        // Backstop: never leave the alarm notification behind, whatever route the
        // service died by. FLAG_NO_CLEAR means the user cannot remove it either.
        if (alarmStack.isEmpty()) cancelActiveAlarmNotification(applicationContext)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
