package com.calendareventsnooze.ui

import android.Manifest
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.CalendarContract
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AlarmEvent
import com.calendareventsnooze.model.RingerMode
import com.calendareventsnooze.model.SnoozedAlarmRecord
import com.calendareventsnooze.scheduler.AlarmScheduler
import com.calendareventsnooze.service.AlarmService
import com.calendareventsnooze.ui.screens.AlarmScreen
import com.calendareventsnooze.ui.theme.CalendarEventSnoozeTheme
import com.calendareventsnooze.util.CalendarLauncher
import com.calendareventsnooze.util.getAlarmEvent
import com.calendareventsnooze.util.putAlarmEvent

class AlarmActivity : ComponentActivity() {

    private var userActionTaken = false
    // True once we have told the service the user finished with this alarm. The
    // service then either pushes the next queued alarm into onNewIntent (B.6) or
    // broadcasts ALARM_RESOLVED so we can close.
    private var resolveSent = false
    private var alarmEventState by mutableStateOf<AlarmEvent?>(null)

    /** Closes the takeover only when no alarms remain (B.6). */
    private val resolvedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val resolvedId = intent?.getStringExtra(AlarmService.EXTRA_ALARM_ID)
            val showing = alarmEventState?.alarmId
            if (resolvedId == null || showing == null || resolvedId == showing) {
                closeTakeover()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        ContextCompat.registerReceiver(
            this, resolvedReceiver,
            IntentFilter(AlarmService.ACTION_ALARM_RESOLVED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        alarmEventState = intent.getAlarmEvent()

        setContent {
            CalendarEventSnoozeTheme {
                BackHandler(enabled = true) { /* intentionally empty */ }

                val event = alarmEventState
                if (event != null) {
                    val presets = androidx.compose.runtime.remember(event.alarmId) {
                        AppPrefs.getSnoozePresets(applicationContext)
                    }
                    val autoDismissSeconds = androidx.compose.runtime.remember(event.alarmId) {
                        currentAutoDismissSeconds()
                    }
                    AlarmScreen(
                        alarmEvent = event,
                        presets = presets,
                        autoDismissSeconds = autoDismissSeconds,
                        onSnooze = { scheduledTimeMs, openCalendar ->
                            performSnooze(scheduledTimeMs, event, openCalendar)
                        },
                        onDismiss = { openCalendar -> performDismiss(event, openCalendar) },
                        // F.16 — silences output only; the alarm is untouched,
                        // so no userActionTaken / resolveSent here.
                        onQuiet = { AlarmService.silenceAlarm(applicationContext) },
                        // The AlarmService owns auto-dismiss; it resolves the alarm and
                        // either closes this screen or shows the next one.
                        onAutoDismissTimeout = { }
                    )
                }
            }
        }
    }

    /**
     * B.7 — closing the takeover has to take its whole task with it. This
     * activity is `singleInstance`, so it is the root of a task of its own; a
     * plain `finish()` leaves that task in recents with the alarm intent as its
     * base, and returning to the app from the overview replays it — the takeover
     * reappears for an alarm the user already snoozed or dismissed.
     * `finishAndRemoveTask()` drops the task record along with the activity, so
     * the only CalendarSnoozer entry left in recents is MainActivity.
     */
    private fun closeTakeover() {
        if (isFinishing || isDestroyed) return
        finishAndRemoveTask()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * B.1 — while an alarm is ringing, any attempt to leave (home / recents /
     * quick app-switch gestures) is undone by immediately bringing the takeover
     * back to the front. Only an explicit user action (snooze / dismiss / open
     * calendar) or the service resolving the alarm lets it close.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        reassertTakeover()
    }

    /**
     * B.1 on Android 17 — the overview / recents route does **not** deliver
     * `onUserLeaveHint()`, so that gesture alone used to leave the takeover
     * sitting in the background (verified on a Pixel 10a: home bounced back,
     * recents did not). `onStop` fires whichever way the activity is backgrounded,
     * so it is the reliable backstop.
     */
    override fun onStop() {
        super.onStop()
        reassertTakeover()
    }

    private fun reassertTakeover() {
        if (userActionTaken || resolveSent || isFinishing || isDestroyed) return
        val event = alarmEventState ?: return
        // Don't fight a deliberate screen-off. The alarm keeps ringing either
        // way, and the full-screen-intent notification brings the user back.
        val power = getSystemService(PowerManager::class.java)
        if (power != null && !power.isInteractive) return
        startActivity(
            Intent(this, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }.putAlarmEvent(event)
        )
    }

    /** A queued alarm was pushed to the front — swap the screen over to it (B.6). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getAlarmEvent()?.let {
            alarmEventState = it
            // A different alarm now owns the screen; allow acting on it.
            userActionTaken = false
            resolveSent = false
        }
        hideSystemBars()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(resolvedReceiver) }
        super.onDestroy()
        if (userActionTaken && !resolveSent) {
            // Safety net: the user acted but we never told the service.
            alarmEventState?.let { AlarmService.resolveAlarm(applicationContext, it.alarmId) }
        }
        // If userActionTaken == false: destroyed by the system. Do NOT stop the
        // service — the alarm keeps running and the sticky full-screen
        // notification brings the user back.
    }

    private fun currentAutoDismissSeconds(): Int {
        val audioManager = getSystemService(AudioManager::class.java)
        val ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL  -> RingerMode.SOUND_ON
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else                             -> RingerMode.SILENT
        }
        return AppPrefs.getSoundProfile(applicationContext, ringerMode).autoDismissSeconds
    }

    /**
     * Hands the alarm back to the service. The service decides what happens to
     * this screen: swap in the next queued alarm, or broadcast that we may close.
     */
    private fun finishAlarm(alarmEvent: AlarmEvent) {
        userActionTaken = true
        resolveSent = true
        AlarmService.resolveAlarm(applicationContext, alarmEvent.alarmId)
    }

    private fun performSnooze(
        scheduledTimeMs: Long,
        alarmEvent: AlarmEvent,
        openCalendar: Boolean
    ) {
        AlarmScheduler.scheduleAt(applicationContext, alarmEvent, scheduledTimeMs)
        AppPrefs.saveSnoozedAlarm(applicationContext, SnoozedAlarmRecord(
            alarmId         = alarmEvent.alarmId,
            eventTitle      = alarmEvent.eventTitle,
            eventText       = alarmEvent.eventText,
            eventId         = alarmEvent.eventId,
            eventTimeMs     = alarmEvent.eventTimeMs,
            scheduledTimeMs = scheduledTimeMs
        ))
        finishAlarm(alarmEvent)
        if (openCalendar) openCalendarThenFinish(alarmEvent)
    }

    private fun performDismiss(alarmEvent: AlarmEvent, openCalendar: Boolean) {
        AppPrefs.removeSnoozedAlarm(applicationContext, alarmEvent.alarmId)
        AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
        finishAlarm(alarmEvent)
        if (openCalendar) openCalendarThenFinish(alarmEvent)
    }

    /**
     * F.14 — the follow-up when "Open Calendar Event After" was ticked. The
     * alarm has already been resolved by the caller; this only takes the user to
     * the event. The keyguard has to come down first or the calendar would open
     * behind the lock screen.
     */
    private fun openCalendarThenFinish(alarmEvent: AlarmEvent) {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { launchCalendar(alarmEvent); closeTakeover() }
            override fun onDismissCancelled() { closeTakeover() }
            override fun onDismissError()     { launchCalendar(alarmEvent); closeTakeover() }
        })
    }

    /** F.12 — the lookup and launch logic is shared with the Manage sheet. */
    private fun launchCalendar(alarmEvent: AlarmEvent) {
        CalendarLauncher.open(
            context = this,
            eventId = alarmEvent.eventId,
            eventTitle = alarmEvent.eventTitle,
            eventTimeMs = alarmEvent.eventTimeMs
        )
    }

}
