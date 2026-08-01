## 9. APPLICATION CLASS — CalendarEventSnoozeApp.kt

Create notification channels in `onCreate()` on API 26+:

| Channel ID | Name | Importance | Extra |
|---|---|---|---|
| `ces_foreground` | "Alarm Service" | LOW | `setSound(null,null)`, `enableVibration(false)` |
| `ces_alarm_active` | "Active Alarm" | HIGH | `setSound(null,null)`, `enableVibration(false)` — no sound on channel; alarm audio handled by MediaPlayer |
| `ces_missed` | "Missed Alarms" | HIGH | Default |

---

## 10. AlarmScheduler.kt

```kotlin
package com.calendareventsnooze.scheduler

object AlarmScheduler {

    fun scheduleSnooze(ctx: Context, alarmEvent: AlarmEvent, minutes: Int) {
        scheduleAt(ctx, alarmEvent,
            System.currentTimeMillis() + minutes * 60_000L)
    }

    fun scheduleAt(ctx: Context, alarmEvent: AlarmEvent, triggerAtMs: Long) {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = "com.calendareventsnooze.TRIGGER_ALARM"
            putAlarmEvent(alarmEvent)
        }
        val pi = PendingIntent.getBroadcast(
            ctx,
            alarmEvent.alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = ctx.getSystemService(AlarmManager::class.java)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (am.canScheduleExactAlarms())
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, pi), pi)
                else
                    am.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            }
            else -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }
    }

    fun cancelAlarm(ctx: Context, alarmId: String) {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = "com.calendareventsnooze.TRIGGER_ALARM"
        }
        val pi = PendingIntent.getBroadcast(
            ctx,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { ctx.getSystemService(AlarmManager::class.java).cancel(it) }
    }
}
```

---

## 11. CalendarNotificationListener.kt

```kotlin
package com.calendareventsnooze.service

class CalendarNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg !in AppPrefs.getCalendarPackages(applicationContext)) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title")
            ?: extras.getString("android.subject") ?: ""
        val text = extras.getString("android.text") ?: ""
        val eventId = extras.getLong("eventId", -1L)

        // Try multiple sources for the event's scheduled time
        val eventTimeMs = listOf(
            extras.getLong("when", -1L),
            extras.getLong("android.when", -1L),
            sbn.notification.`when`.takeIf { it > 0L } ?: -1L
        ).firstOrNull { it > 0L } ?: -1L

        val alarmId = "alarm_${System.currentTimeMillis()}"
        val alarmEvent = AlarmEvent(alarmId, title, text, eventId, eventTimeMs)

        // CRITICAL: Do NOT call cancelNotification(sbn.key).
        // The original calendar notification must remain visible in the panel.

        // Start alarm service
        val serviceIntent = Intent(applicationContext, AlarmService::class.java)
            .putAlarmEvent(alarmEvent)
        applicationContext.startForegroundService(serviceIntent)

        // Launch alarm activity
        val activityIntent = Intent(applicationContext, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }.putAlarmEvent(alarmEvent)
        applicationContext.startActivity(activityIntent)
    }
}
```

---

## 12. AlarmService.kt

This is the most critical component. Read every rule before writing this class.

### RULE 1 — onTaskRemoved must be overridden
When the user swipes the app from the recents screen, Android calls
`onTaskRemoved()` on running services. Override it with an empty body to prevent
the service from being killed:
```kotlin
override fun onTaskRemoved(rootIntent: Intent?) {
    // Intentionally empty. Alarm must survive task removal.
}
```

### RULE 2 — Return START_STICKY
`onStartCommand()` must return `START_STICKY` so Android restarts the service
if it is killed by the system.

### RULE 3 — Post the active alarm notification immediately
As soon as the service starts, post a HIGH-importance persistent notification
(notification ID 1002) that allows the user to return to AlarmActivity. This
notification must carry ALL AlarmEvent data in its PendingIntent extras.

### RULE 4 — cancelActiveAlarmNotification() must be a companion method
It must be callable from AlarmActivity and other components without requiring
a reference to the service instance:
```kotlin
companion object {
    const val ACTION_STOP = "com.calendareventsnooze.ACTION_STOP"
    const val FOREGROUND_NOTIF_ID = 1001
    const val ACTIVE_ALARM_NOTIF_ID = 1002

    fun cancelActiveAlarmNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(ACTIVE_ALARM_NOTIF_ID)
    }
}
```

### RULE 5 — handleAutoDismiss logic
```
if autoDismissMaxRetries == 0:
    → auto-dismiss immediately (no snooze)
    → do NOT save to snoozed alarms list
    → call removeSnoozedAlarm, showMissedNotification, stopAlarm
    → return

if autoDismissAction == DISMISS:
    → same as above

if autoDismissAction == SNOOZE:
    increment retry count
    if count > maxRetries:
        → auto-dismiss (as above)
    else:
        → schedule next alarm via AlarmScheduler.scheduleAt()
        → SAVE to snoozed alarms list via AppPrefs.saveSnoozedAlarm()
        → stopSelf() (do NOT show missed notification)
```

### Full class structure:

```kotlin
package com.calendareventsnooze.service

class AlarmService : Service() {

    companion object { /* see Rule 4 */ }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null
    private var currentAlarmEvent: AlarmEvent? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val alarmEvent = intent?.getAlarmEvent() ?: return START_NOT_STICKY
        currentAlarmEvent = alarmEvent

        // 1. Start low-priority foreground notification first (Android requirement)
        startForeground(FOREGROUND_NOTIF_ID,
            buildForegroundNotification(alarmEvent.eventTitle))

        // 2. Post the high-priority return-to-alarm notification
        postActiveAlarmNotification(alarmEvent)

        // 3. Determine ringer mode and load profile
        val audioManager = getSystemService(AudioManager::class.java)
        val ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL  -> RingerMode.SOUND_ON
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else                             -> RingerMode.SILENT
        }
        val profile = AppPrefs.getSoundProfile(applicationContext, ringerMode)

        // 4. Start sound and vibration with correct sequencing
        startAlarmOutput(profile)

        // 5. Schedule auto-dismiss
        if (profile.autoDismissSeconds > 0) {
            val runnable = Runnable { handleAutoDismiss(profile, alarmEvent) }
            autoDismissRunnable = runnable
            handler.postDelayed(runnable, profile.autoDismissSeconds * 1000L)
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Intentionally empty — alarm must survive task removal
    }

    private fun postActiveAlarmNotification(alarmEvent: AlarmEvent) {
        // PendingIntent must carry ALL alarm event fields so AlarmActivity
        // can restore its state even after being destroyed
        val returnIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }.putAlarmEvent(alarmEvent)

        val pi = PendingIntent.getActivity(
            this, ACTIVE_ALARM_NOTIF_ID, returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, "ces_alarm_active")
            .setContentTitle("⚠ Calendar Alarm Active")
            .setContentText(alarmEvent.eventTitle)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(ACTIVE_ALARM_NOTIF_ID, notif)
    }

    private fun buildForegroundNotification(title: String): Notification {
        return NotificationCompat.Builder(this, "ces_foreground")
            .setContentTitle("Calendar Alarm Active")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
        stopSoundAndVibration()
        cancelActiveAlarmNotification(applicationContext)

        // Special case: maxRetries == 0 means dismiss immediately, no snooze
        if (profile.autoDismissMaxRetries == 0 ||
            profile.autoDismissAction == AutoDismissAction.DISMISS) {
            AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
            AppPrefs.removeSnoozedAlarm(applicationContext, alarmEvent.alarmId)
            showMissedNotification(alarmEvent.eventTitle)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val retryCount = AppPrefs.incrementAutoSnoozeCount(
            applicationContext, alarmEvent.alarmId)

        if (retryCount > profile.autoDismissMaxRetries) {
            // Max retries exceeded — dismiss completely
            AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
            AppPrefs.removeSnoozedAlarm(applicationContext, alarmEvent.alarmId)
            showMissedNotification(alarmEvent.eventTitle)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
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
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    fun stopAlarm() {
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        stopSoundAndVibration()
        cancelActiveAlarmNotification(applicationContext)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopSoundAndVibration() {
        runCatching { mediaPlayer?.stop(); mediaPlayer?.release() }
        mediaPlayer = null
        vibrator?.cancel()
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

    override fun onDestroy() { stopSoundAndVibration(); super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}
```

---

## 13. AlarmReceiver.kt

```kotlin
package com.calendareventsnooze.receiver

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.calendareventsnooze.TRIGGER_ALARM" -> {
                val alarmEvent = intent.getAlarmEvent() ?: return

                // Remove from snoozed list — alarm is now firing, not pending
                AppPrefs.removeSnoozedAlarm(context, alarmEvent.alarmId)

                context.startForegroundService(
                    Intent(context, AlarmService::class.java).putAlarmEvent(alarmEvent))

                context.startActivity(
                    Intent(context, AlarmActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }.putAlarmEvent(alarmEvent))
            }
            Intent.ACTION_BOOT_COMPLETED -> { /* AlarmManager handles persistence */ }
        }
    }
}
```

---

## 14. AlarmActivity.kt

### CRITICAL RULES — read before writing a single line of this class

**RULE A — userActionTaken flag prevents double-stopping:**
```kotlin
private var userActionTaken = false
```
Set to `true` ONLY inside the explicit user action handlers (snooze buttons,
dismiss, open calendar). Never set it in any lifecycle method.

**RULE B — onDestroy logic:**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    if (userActionTaken) {
        // User explicitly acted — stop the alarm service
        startService(Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        })
    }
    // If userActionTaken == false: destroyed by Home button, task swipe,
    // or system. Do NOT stop the service. Alarm keeps running.
    // User returns via the persistent notification.
}
```

**RULE C — onNewIntent refreshes state:**
```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    // Update the Compose state holder with new alarm data from intent
}
```

**RULE D — All alarm data comes from the intent:**
AlarmActivity never hardcodes alarm data. It reads everything from the launching
intent via `getAlarmEvent()`. This works for both fresh launches and notification-
driven relaunches because the notification PendingIntent carries the same data.

**RULE E — Back button is disabled:**
```kotlin
BackHandler(enabled = true) { /* intentionally empty */ }
```

### Screen-on / lock-screen setup (in onCreate, before setContent):
```kotlin
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
WindowInsetsControllerCompat(window, window.decorView).apply {
    hide(WindowInsetsCompat.Type.systemBars())
    systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}
```

### Snooze action — execute ALL steps in this exact order:
```kotlin
fun performSnooze(scheduledTimeMs: Long, alarmEvent: AlarmEvent) {
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
```
Call `performSnooze()` from ALL 6 snooze paths: the 4 preset buttons (using
`System.currentTimeMillis() + preset.minutes * 60_000L`), the Specify Time
dialog, and the Time & Date dialog.

### Dismiss action:
```kotlin
fun performDismiss(alarmEvent: AlarmEvent) {
    userActionTaken = true
    AppPrefs.removeSnoozedAlarm(applicationContext, alarmEvent.alarmId)
    AppPrefs.resetAutoSnoozeCount(applicationContext, alarmEvent.alarmId)
    AlarmService.cancelActiveAlarmNotification(applicationContext)
    finish()
}
```

### Open Calendar Event action:
```kotlin
fun performOpenCalendar(alarmEvent: AlarmEvent) {
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
```

---

## 15. AlarmScreen.kt — Compose UI

Full-screen composable. Receives `alarmEvent: AlarmEvent` and callbacks.
Background: `#1A1A2E`. All inside a `Column` with `verticalScroll`.

### Layout:
```
[📅  OPEN CALENDAR EVENT      ]   ← 56dp, blue filled (#2A5298)

         ⚠  Team Standup          ← 24sp bold white centered
     Today at 3:00 PM              ← 14sp #B0B0CC (hidden if null)
   Conference Room B               ← 16sp #B0B0CC centered
   Auto-dismiss in 45s            ← 14sp red, hidden if autoDismiss=0

──────── SNOOZE ──────────────────
[ Preset 1 ]        [ Preset 2 ]   ← 64dp outlined dark buttons
[ Preset 3 ]        [ Preset 4 ]   ← 64dp outlined dark buttons
[⏱ Specify Time]  [📅 Time & Date] ← 64dp gold outlined (#FFD700)
──────────────────────────────────
[          ✕  DISMISS           ]  ← 72dp red filled (#C0392B) bold
```

### Auto-dismiss countdown in Compose:
```kotlin
var secondsLeft by remember { mutableIntStateOf(autoDismissSeconds) }
LaunchedEffect(Unit) {
    if (autoDismissSeconds <= 0) return@LaunchedEffect
    while (secondsLeft > 0) {
        delay(1000L)
        secondsLeft--
    }
    onAutoDismissTimeout()
}
// Display: "Auto-dismiss in ${secondsLeft}s" — hide when autoDismissSeconds <= 0
```

### Specify Time dialog:
Two `OutlinedTextField`s (hours, minutes, both numeric).
Validate total > 0. On confirm: `performSnooze(now + (h*60+m)*60_000L)`.

### Time & Date dialog:
Show `TimePicker` (Material 3) first with default = next top-of-hour.
Then show `DatePicker` with default = today.
Validate selected timestamp > now.
On confirm: `performSnooze(combinedTimestampMs)`.

---
