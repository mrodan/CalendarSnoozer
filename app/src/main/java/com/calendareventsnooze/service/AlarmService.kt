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
import android.os.SystemClock
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.calendareventsnooze.R
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AlarmEvent
import com.calendareventsnooze.model.AutoDismissAction
import com.calendareventsnooze.model.SnoozedAlarmRecord
import com.calendareventsnooze.model.SoundProfile
import com.calendareventsnooze.scheduler.AlarmScheduler
import com.calendareventsnooze.ui.AlarmActivity
import com.calendareventsnooze.ui.MainActivity
import com.calendareventsnooze.util.formatEventTime
import com.calendareventsnooze.util.getAlarmEvent
import com.calendareventsnooze.util.ObservanceCalendar
import com.calendareventsnooze.util.RingerModes
import com.calendareventsnooze.util.play
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
         * itself running. The takeover stays up and the notification stays put.
         *
         * Round 26 — it now also restarts the auto-dismiss countdown. "Shhhh"
         * means "not yet, give me a moment", and the old behaviour let the
         * countdown run out seconds later and file the alarm as missed, which
         * is the opposite of what the user just asked for.
         */
        const val ACTION_SILENCE = "com.calendareventsnooze.ACTION_SILENCE"

        /**
         * Round 26 — holds the auto-dismiss countdown while a snooze picker is
         * open on the takeover, and lets it continue from where it stopped when
         * the picker closes. Choosing a snooze time takes longer than the 30-
         * or 60-second countdown allows, so without this the alarm resolved
         * itself out from under the dialog.
         */
        const val ACTION_AUTO_DISMISS_PAUSE = "com.calendareventsnooze.ACTION_AD_PAUSE"
        const val ACTION_AUTO_DISMISS_RESUME = "com.calendareventsnooze.ACTION_AD_RESUME"

        /**
         * Round 27 — the takeover tells the service when it is on screen, so
         * the notification can stop announcing an alarm the user is already
         * looking at. See [fullScreenEscalated].
         */
        const val ACTION_TAKEOVER_SHOWN = "com.calendareventsnooze.ACTION_TAKEOVER_SHOWN"
        const val ACTION_TAKEOVER_GONE = "com.calendareventsnooze.ACTION_TAKEOVER_GONE"

        /**
         * How long the takeover gets to appear before the notification falls
         * back to a full-screen intent. Long enough that a normal launch — or
         * B.1's relaunch after a Home press — wins the race and no banner is
         * ever drawn; short enough to be unnoticeable when the launch really
         * was blocked and the intent is the only way onto the screen.
         */
        private const val FULL_SCREEN_ESCALATION_MS = 1500L

        fun takeoverShown(context: Context) = send(context, ACTION_TAKEOVER_SHOWN)
        fun takeoverGone(context: Context) = send(context, ACTION_TAKEOVER_GONE)

        /** Silences the current alarm's output without resolving it (F.16). */
        fun silenceAlarm(context: Context) = send(context, ACTION_SILENCE)

        /** Round 26 — freeze the auto-dismiss countdown (a picker is open). */
        fun pauseAutoDismiss(context: Context) = send(context, ACTION_AUTO_DISMISS_PAUSE)

        /** Round 26 — let it run on from where [pauseAutoDismiss] stopped it. */
        fun resumeAutoDismiss(context: Context) = send(context, ACTION_AUTO_DISMISS_RESUME)

        private fun send(context: Context, action: String) {
            runCatching {
                context.startService(
                    Intent(context, AlarmService::class.java).setAction(action)
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

        /** Round 27 — the escalation alert; see [buildFullScreenAlert]. */
        const val FULL_SCREEN_ALERT_ID = 1003

        /** Volume steps per second while fading in (F.10). */
        private const val FADE_STEPS_PER_SECOND = 10

        fun cancelActiveAlarmNotification(context: Context) {
            context.getSystemService(NotificationManager::class.java).apply {
                cancel(ACTIVE_ALARM_NOTIF_ID)
                // Round 27 — the escalation alert outlives the service if it is
                // not cleared here; it is a plain notification, not the
                // foreground one, so stopForeground does not take it.
                cancel(FULL_SCREEN_ALERT_ID)
            }
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

    // ------------------------------------------------------------------
    // Auto-dismiss countdown state (round 26)
    //
    // It used to be an anonymous lambda posted to [handler], which could only be
    // cancelled by clearing the whole queue — and that queue also carries the
    // sequencing and per-output delays. Holding the Runnable means the countdown
    // can be paused, resumed and restarted on its own.
    // ------------------------------------------------------------------

    private var autoDismissRunnable: Runnable? = null
    /** elapsedRealtime at which the pending countdown fires. */
    private var autoDismissDeadlineMs = 0L
    /** Non-null only while paused: what was left when it stopped. */
    private var autoDismissRemainingMs: Long? = null
    /** What the countdown would act on — kept so it can be re-armed. */
    private var autoDismissProfile: SoundProfile? = null
    private var autoDismissEvent: AlarmEvent? = null

    // ------------------------------------------------------------------
    // Getting the takeover on screen without announcing it twice (round 27)
    // ------------------------------------------------------------------

    /** True while AlarmActivity reports itself started. */
    private var takeoverVisible = false

    /**
     * Whether the notification currently carries a full-screen intent.
     *
     * The intent is what the heads-up banner is made of. A foreground service
     * must post *some* notification, and this one must stay IMPORTANCE_HIGH so
     * the intent can fire — but a silent high-importance notification with no
     * full-screen intent does not produce a banner, so the banner can be
     * removed without weakening anything by attaching the intent only when it
     * is actually needed: when the takeover is **not** already on screen.
     *
     * Every caller launches AlarmActivity directly in the same breath as
     * starting the service, so the normal path never needs it. It is the
     * backstop for a launch the system refused.
     */
    private var fullScreenEscalated = false
    private var escalationRunnable: Runnable? = null

    /** Round 28 — ends a ringing alarm the moment Shabbat or Yom Tov begins. */
    private var observanceCutoff: Runnable? = null

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
                // F.16 — deliberately does NOT clear the handler queue: it also
                // carries the sequencing and per-output delays, and `silenced`
                // is what makes those a no-op when they fire.
                if (alarmStack.isEmpty()) { stopSelf(); return START_NOT_STICKY }
                silenced = true
                stopSoundAndVibration()
                // Round 26 — "Shhhh" buys time, so give the user the full
                // countdown again rather than the seconds that were left.
                restartAutoDismiss()
                return START_STICKY
            }
            ACTION_AUTO_DISMISS_PAUSE -> {
                if (alarmStack.isEmpty()) { stopSelf(); return START_NOT_STICKY }
                pauseAutoDismissNow()
                return START_STICKY
            }
            ACTION_AUTO_DISMISS_RESUME -> {
                if (alarmStack.isEmpty()) { stopSelf(); return START_NOT_STICKY }
                resumeAutoDismissNow()
                return START_STICKY
            }
            ACTION_TAKEOVER_SHOWN -> {
                if (alarmStack.isEmpty()) { stopSelf(); return START_NOT_STICKY }
                takeoverVisible = true
                // Also takes down the alert if the escalation had already
                // posted one — the user is looking at the takeover now.
                cancelFullScreenEscalation()
                return START_STICKY
            }
            ACTION_TAKEOVER_GONE -> {
                if (alarmStack.isEmpty()) { stopSelf(); return START_NOT_STICKY }
                takeoverVisible = false
                scheduleFullScreenEscalation()
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

        // Round 27 — a fresh alarm needs no full-screen intent, because the
        // caller has just launched the takeover itself. If it does not appear,
        // the escalation posts one. An alarm interrupting one already on screen
        // (B.6) needs no escalation at all.
        if (takeoverVisible) cancelFullScreenEscalation() else scheduleFullScreenEscalation()

        val profile = currentProfile()
        startAlarmOutput(profile)

        autoDismissProfile = profile
        autoDismissEvent = alarmEvent
        scheduleAutoDismiss(profile.autoDismissSeconds * 1000L)
        scheduleObservanceCutoff()
    }

    /**
     * Round 28 — an alarm still ringing at candle lighting must not ring on.
     * Whatever is on the stack goes to Missed Alarms silently, the same as a
     * snooze coming due during Shabbat or Yom Tov.
     */
    private fun scheduleObservanceCutoff() {
        cancelObservanceCutoff()
        val now = System.currentTimeMillis()
        val start = ObservanceCalendar.nextStartAfter(applicationContext, now) ?: return
        val cutoff = Runnable {
            observanceCutoff = null
            if (ObservanceCalendar.isSilenced(applicationContext, System.currentTimeMillis())) {
                endAlarmsForObservance()
            }
        }
        observanceCutoff = cutoff
        handler.postDelayed(cutoff, start - now)
    }

    private fun cancelObservanceCutoff() {
        observanceCutoff?.let { handler.removeCallbacks(it) }
        observanceCutoff = null
    }

    private fun endAlarmsForObservance() {
        silenceCurrentOutput()
        val last = alarmStack.lastOrNull()?.alarmId
        alarmStack.forEach { AlarmScheduler.fileAsMissed(applicationContext, it) }
        alarmStack.clear()
        tearDown(last)
    }

    /**
     * Arms the fallback: if the takeover has not appeared shortly, post the
     * full-screen-intent notification so the alarm still reaches the screen.
     */
    private fun scheduleFullScreenEscalation() {
        cancelFullScreenEscalation()
        val runnable = Runnable {
            escalationRunnable = null
            val top = alarmStack.lastOrNull()
            if (takeoverVisible || top == null) return@Runnable
            fullScreenEscalated = true
            runCatching {
                getSystemService(NotificationManager::class.java)
                    .notify(FULL_SCREEN_ALERT_ID, buildFullScreenAlert(top))
            }
        }
        escalationRunnable = runnable
        handler.postDelayed(runnable, FULL_SCREEN_ESCALATION_MS)
    }

    /** Un-arms the fallback and takes down the alert if it was posted. */
    private fun cancelFullScreenEscalation() {
        escalationRunnable?.let { handler.removeCallbacks(it) }
        escalationRunnable = null
        if (fullScreenEscalated) {
            fullScreenEscalated = false
            runCatching {
                getSystemService(NotificationManager::class.java)
                    .cancel(FULL_SCREEN_ALERT_ID)
            }
        }
    }

    /**
     * The one notification that is allowed to interrupt: it exists only when
     * the takeover could not get on screen by itself, and a full-screen intent
     * needs a high-importance channel to fire. It is cancelled the moment the
     * takeover reports in, so in the normal case it is never posted at all.
     */
    private fun buildFullScreenAlert(alarmEvent: AlarmEvent): Notification {
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

    // ------------------------------------------------------------------
    // Auto-dismiss countdown (round 26)
    // ------------------------------------------------------------------

    /** Arms the countdown to fire in [delayMs]. Replaces any pending one. */
    private fun scheduleAutoDismiss(delayMs: Long) {
        cancelAutoDismiss()
        val profile = autoDismissProfile ?: return
        val alarmEvent = autoDismissEvent ?: return
        if (profile.autoDismissSeconds <= 0 || delayMs <= 0L) return
        val runnable = Runnable {
            autoDismissRunnable = null
            handleAutoDismiss(profile, alarmEvent)
        }
        autoDismissRunnable = runnable
        autoDismissDeadlineMs = SystemClock.elapsedRealtime() + delayMs
        handler.postDelayed(runnable, delayMs)
    }

    /** Un-arms the countdown without forgetting which alarm it belonged to. */
    private fun cancelAutoDismiss() {
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = null
        autoDismissRemainingMs = null
    }

    /** Freezes the countdown with whatever time was left on it. */
    private fun pauseAutoDismissNow() {
        val runnable = autoDismissRunnable ?: return  // not armed, or already paused
        handler.removeCallbacks(runnable)
        autoDismissRunnable = null
        autoDismissRemainingMs =
            (autoDismissDeadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    /** Continues a paused countdown from where it stopped. */
    private fun resumeAutoDismissNow() {
        val remaining = autoDismissRemainingMs ?: return  // never paused
        autoDismissRemainingMs = null
        // A countdown paused with nothing left would fire the instant it
        // resumed, which reads as the dialog's own Cancel dismissing the alarm.
        // Give it a second's grace instead.
        scheduleAutoDismiss(remaining.coerceAtLeast(1000L))
    }

    /** Puts the full countdown back on the clock ("Shhhh"). */
    private fun restartAutoDismiss() {
        val seconds = autoDismissProfile?.autoDismissSeconds ?: return
        scheduleAutoDismiss(seconds * 1000L)
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
        // Clearing the queue already un-posted the countdown; forget the alarm
        // it belonged to as well, or a later pause/resume could re-arm it for an
        // alarm that has been resolved.
        cancelAutoDismiss()
        autoDismissProfile = null
        autoDismissEvent = null
        cancelFullScreenEscalation()
        cancelObservanceCutoff()
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

    private fun currentProfile(): SoundProfile =
        AppPrefs.getSoundProfile(applicationContext, RingerModes.current(applicationContext))

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
        // Round 27 — the low-importance channel, and no full-screen intent. Both
        // are needed: a high-importance notification raises a heads-up banner
        // over the takeover whether or not it carries an intent (measured — a
        // build that only dropped the intent still banner-ed). The full-screen
        // intent now lives on its own notification, posted only when the
        // takeover has failed to appear. See [fullScreenEscalated].
        val notification = NotificationCompat.Builder(this, "ces_alarm_ongoing")
            .setContentTitle("⚠ Calendar Alarm Active")
            .setContentText(alarmEvent.eventTitle.ifBlank { "Tap to open the alarm" })
            .setSubText(subText)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi)
            .setOngoing(true)
            .setAutoCancel(false)
            .setDeleteIntent(reassertPendingIntent())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        // B.6 — three layers, because none of them is sufficient alone (round 27:
        // these still hold on the low-importance channel; importance governs the
        // banner, not dismissibility):
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
        // F.7 — a custom buzz expands its buzz/pattern sliders into one finite
        // waveform, repetitions included, so its repeat index stays at NO_REPEAT.
        // UI.25 — a preset instead loops from the start until something stops
        // it; the profile decides which, so this stays a single call.
        vibrator?.play(profile.buildVibrationWaveform(), profile.vibrationRepeatIndex())
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
     * F.15 — files an alarm the user never acted on into the Missed list.
     *
     * Round 26 — and tells the user, every time. The notification used to be
     * posted by two of the three call sites in [handleAutoDismiss] and not by
     * Force Stop, so alarms could land in the Missed list with nothing on screen
     * to say so. Posting it from here makes "recorded as missed" and "the user
     * was told" the same event, and any future path that files a missed alarm
     * gets the notification for free.
     *
     * Round 28 — the one exception is during Shabbat or Yom Tov, when only a
     * test alarm can reach here and nothing should make a sound.
     */
    private fun recordMissed(alarmEvent: AlarmEvent) {
        AlarmScheduler.fileAsMissed(applicationContext, alarmEvent)
        if (!ObservanceCalendar.isSilenced(applicationContext, System.currentTimeMillis())) {
            showMissedNotification(alarmEvent)
        }
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
            resolveAndAdvance(alarmEvent.alarmId)
            return
        }

        val retryCount = AppPrefs.incrementAutoSnoozeCount(
            applicationContext, alarmEvent.alarmId)

        if (retryCount > profile.autoDismissMaxRetries) {
            // Max retries exceeded — dismiss completely
            AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
            recordMissed(alarmEvent)
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

    private fun showMissedNotification(alarmEvent: AlarmEvent) {
        // F.17 — the notification had no content intent, so tapping it did
        // nothing at all. It now opens the Home tab, which is where the alarm
        // it is telling the user about has just been filed (F.15).
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_HOME, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = alarmEvent.eventTitle.ifBlank { "Calendar event" }
        // The event's own day and time, not when it was given up on: "Missed
        // Alarm: Dentist / Today at 3:00 PM" is what identifies which reminder
        // went by. The event's description comes first when it has one.
        val detail = listOfNotNull(
            alarmEvent.eventText.takeIf { it.isNotBlank() },
            formatEventTime(alarmEvent.eventTimeMs)
        ).joinToString("  ·  ")
        val notif = NotificationCompat.Builder(this, "ces_missed")
            .setContentTitle("Missed Alarm: $title")
            .setContentText(detail)
            // Long event descriptions are cut off on one line, so let the
            // expanded notification show the whole thing.
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(alarmEvent.eventTimeMs.takeIf { it > 0L } ?: System.currentTimeMillis())
            .setShowWhen(true)
            .build()
        // Keyed on the alarm, not the clock: an alarm that is auto-snoozed and
        // then missed again replaces its own notification instead of stacking a
        // second copy for the same event.
        getSystemService(NotificationManager::class.java)
            .notify(alarmEvent.alarmId.hashCode(), notif)
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
