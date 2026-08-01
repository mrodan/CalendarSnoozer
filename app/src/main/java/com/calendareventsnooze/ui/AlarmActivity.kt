package com.calendareventsnooze.ui

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
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
import com.calendareventsnooze.util.getAlarmEvent
import com.calendareventsnooze.util.putAlarmEvent

class AlarmActivity : ComponentActivity() {

    private var userActionTaken = false
    // Set true once the alarm has been resolved by the service (auto-dismiss /
    // auto-snooze / stop) so we stop re-asserting the takeover and let it close.
    private var alarmResolved = false
    private var alarmEventState by mutableStateOf<AlarmEvent?>(null)

    // Closes the takeover when the service reports the alarm is over (B.2).
    private val resolvedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            alarmResolved = true
            if (!isFinishing) finish()
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
                        onOpenCalendar = { performOpenCalendar(event) },
                        onSnooze = { scheduledTimeMs -> performSnooze(scheduledTimeMs, event) },
                        onDismiss = { performDismiss(event) },
                        // The AlarmService owns auto-dismiss; it broadcasts ALARM_RESOLVED
                        // which closes this screen. The on-screen countdown is display-only.
                        onAutoDismissTimeout = { }
                    )
                }
            }
        }
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
     * calendar) or the service resolving the alarm lets it close. Allowed here
     * because the app holds the "Display over other apps" permission.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (userActionTaken || alarmResolved || isFinishing) return
        val event = alarmEventState ?: return
        startActivity(
            Intent(this, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }.putAlarmEvent(event)
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getAlarmEvent()?.let { alarmEventState = it }
        hideSystemBars()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(resolvedReceiver) }
        super.onDestroy()
        if (userActionTaken) {
            // User explicitly acted — stop the alarm service
            startService(Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP
            })
        }
        // If userActionTaken == false and not resolved: destroyed by the system.
        // Do NOT stop the service — the alarm keeps running and the sticky
        // full-screen notification brings the user back.
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

    private fun performSnooze(scheduledTimeMs: Long, alarmEvent: AlarmEvent) {
        userActionTaken = true
        AlarmScheduler.scheduleAt(applicationContext, alarmEvent, scheduledTimeMs)
        AppPrefs.saveSnoozedAlarm(applicationContext, SnoozedAlarmRecord(
            alarmId         = alarmEvent.alarmId,
            eventTitle      = alarmEvent.eventTitle,
            eventText       = alarmEvent.eventText,
            eventId         = alarmEvent.eventId,
            eventTimeMs     = alarmEvent.eventTimeMs,
            scheduledTimeMs = scheduledTimeMs
        ))
        AlarmService.cancelActiveAlarmNotification(applicationContext)
        finish()  // onDestroy will stop AlarmService via userActionTaken flag
    }

    private fun performDismiss(alarmEvent: AlarmEvent) {
        userActionTaken = true
        AppPrefs.removeSnoozedAlarm(applicationContext, alarmEvent.alarmId)
        AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
        AlarmService.cancelActiveAlarmNotification(applicationContext)
        finish()
    }

    private fun performOpenCalendar(alarmEvent: AlarmEvent) {
        userActionTaken = true
        AppPrefs.removeSnoozedAlarm(applicationContext, alarmEvent.alarmId)
        AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
        AlarmService.cancelActiveAlarmNotification(applicationContext)

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { launchCalendar(alarmEvent.eventId); finish() }
            override fun onDismissCancelled() { finish() }
            override fun onDismissError()     { launchCalendar(alarmEvent.eventId); finish() }
        })
    }

    private fun launchCalendar(eventId: Long) {
        try {
            val intent = if (eventId > 0L) {
                Intent(Intent.ACTION_VIEW,
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
            } else {
                packageManager.getLaunchIntentForPackage("com.google.android.calendar")
                    ?: Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (ignored: Exception) {}
        }
    }
}
