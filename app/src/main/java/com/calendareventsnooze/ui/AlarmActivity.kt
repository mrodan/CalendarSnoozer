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
                if (!isFinishing) finish()
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
                        onOpenCalendar = { performOpenCalendar(event) },
                        onSnooze = { scheduledTimeMs -> performSnooze(scheduledTimeMs, event) },
                        onDismiss = { performDismiss(event) },
                        // The AlarmService owns auto-dismiss; it resolves the alarm and
                        // either closes this screen or shows the next one.
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

    private fun performSnooze(scheduledTimeMs: Long, alarmEvent: AlarmEvent) {
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
    }

    private fun performDismiss(alarmEvent: AlarmEvent) {
        AppPrefs.removeSnoozedAlarm(applicationContext, alarmEvent.alarmId)
        AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
        finishAlarm(alarmEvent)
    }

    private fun performOpenCalendar(alarmEvent: AlarmEvent) {
        AppPrefs.removeSnoozedAlarm(applicationContext, alarmEvent.alarmId)
        AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
        finishAlarm(alarmEvent)

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { launchCalendar(alarmEvent); finish() }
            override fun onDismissCancelled() { finish() }
            override fun onDismissError()     { launchCalendar(alarmEvent); finish() }
        })
    }

    /** A concrete calendar event instance resolved from the calendar provider. */
    private data class CalendarHit(val eventId: Long, val begin: Long, val end: Long)

    /**
     * B.5 — finds the real calendar event behind this alarm.
     *
     * Google Calendar does **not** put an `eventId` in its notification extras
     * (it is always -1), so the event has to be looked up in the calendar
     * provider instead. Searches instances around the event's time and matches
     * on title, preferring the instance closest to that time.
     */
    private fun resolveCalendarEvent(alarmEvent: AlarmEvent): CalendarHit? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) return null

        val anchor = if (alarmEvent.eventTimeMs > 0L) alarmEvent.eventTimeMs
                     else System.currentTimeMillis()
        val window = 12L * 60 * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, anchor - window)
            ContentUris.appendId(this, anchor + window)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE
        )
        val wanted = alarmEvent.eventTitle.trim()
        if (wanted.isEmpty()) return null

        return runCatching {
            contentResolver.query(uri, projection, null, null, null)?.use { c ->
                var best: CalendarHit? = null
                var bestDelta = Long.MAX_VALUE
                while (c.moveToNext()) {
                    val title = c.getString(3)?.trim().orEmpty()
                    if (title.isEmpty()) continue
                    val matches = title.equals(wanted, ignoreCase = true) ||
                            title.contains(wanted, ignoreCase = true) ||
                            wanted.contains(title, ignoreCase = true)
                    if (!matches) continue
                    val begin = c.getLong(1)
                    val delta = kotlin.math.abs(begin - anchor)
                    if (delta < bestDelta) {
                        bestDelta = delta
                        best = CalendarHit(c.getLong(0), begin, c.getLong(2))
                    }
                }
                best
            }
        }.getOrNull()
    }

    /**
     * B.5 — opens the specific calendar event, never an app chooser.
     *
     * Event intents are offered to each installed calendar app explicitly (a
     * failure just falls through to the next candidate), because handing a bare
     * `content://com.android.calendar/...` intent to the system lets unrelated
     * apps claim it — that is what produced the "Open with Google" chooser and
     * the "Couldn't load object" error.
     */
    private fun launchCalendar(alarmEvent: AlarmEvent) {
        val installedCalendars = CALENDAR_PACKAGES.filter { pkg ->
            runCatching { packageManager.getLaunchIntentForPackage(pkg) != null }
                .getOrDefault(false)
        }

        // 1. The specific event — from the notification, or looked up in the provider.
        val hit = if (alarmEvent.eventId > 0L)
            CalendarHit(alarmEvent.eventId, alarmEvent.eventTimeMs, alarmEvent.eventTimeMs)
        else resolveCalendarEvent(alarmEvent)

        if (hit != null) {
            val eventIntent = Intent(Intent.ACTION_VIEW,
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, hit.eventId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (hit.begin > 0L) {
                eventIntent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, hit.begin)
                if (hit.end > 0L) {
                    eventIntent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, hit.end)
                }
            }
            for (pkg in installedCalendars) {
                if (tryStart(Intent(eventIntent).setPackage(pkg))) return
            }
            calendarPackageFor(eventIntent)?.let {
                if (tryStart(Intent(eventIntent).setPackage(it))) return
            }
        }

        // 2. Open the calendar at the event's day and time.
        if (alarmEvent.eventTimeMs > 0L) {
            val timeIntent = Intent(Intent.ACTION_VIEW,
                CalendarContract.CONTENT_URI.buildUpon()
                    .appendPath("time")
                    .appendPath(alarmEvent.eventTimeMs.toString())
                    .build()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            for (pkg in installedCalendars) {
                if (tryStart(Intent(timeIntent).setPackage(pkg))) return
            }
        }

        // 3. Last resort — just open the calendar app.
        for (pkg in installedCalendars) {
            val launch = packageManager.getLaunchIntentForPackage(pkg) ?: continue
            if (tryStart(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
        }
        tryStart(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun tryStart(intent: Intent): Boolean =
        runCatching { startActivity(intent); true }.getOrDefault(false)

    /**
     * Returns a **known calendar** package able to handle [intent], or null.
     *
     * Deliberately never falls back to "whatever app happens to claim the URI":
     * on real devices `content://com.android.calendar/...` is often claimed by
     * unrelated apps (Messages claims it on stock images), which is what produced
     * the "Open with Google" chooser and the "Couldn't load object" failure.
     * When no calendar app claims a URI we skip that candidate and fall through
     * to simply opening the calendar app.
     */
    private fun calendarPackageFor(intent: Intent): String? {
        val handlers = runCatching {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        return CALENDAR_PACKAGES.firstOrNull { it in handlers }
    }

    private companion object {
        val CALENDAR_PACKAGES = listOf(
            "com.google.android.calendar",
            "com.android.calendar",
            "com.samsung.android.calendar"
        )
    }
}
