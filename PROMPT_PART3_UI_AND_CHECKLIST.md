## 16. MainActivity.kt

Single `ComponentActivity`. Uses `Scaffold` with a `TabRow`.

**Status bar padding — CRITICAL:**
Apply `Modifier.windowInsetsPadding(WindowInsets.statusBars)` to the outermost
container so the tab row is never hidden behind the status bar:
```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars)
) {
    TabRow(selectedTabIndex = selectedTab) { /* tabs */ }
    /* screen content */
}
```

### Four tabs:
1. **Setup** → `SetupScreen`
2. **Snooze Buttons** → `SnoozePresetsScreen`
3. **Sound & Vibration** → `SoundProfileScreen`
4. **Snoozed Alarms** → `SnoozedAlarmsScreen`

---

## 17. SetupScreen.kt

### Permissions checklist:

| Permission | Check | Grant intent |
|---|---|---|
| Notification Access | `Settings.Secure.getString(cr, "enabled_notification_listeners").contains(packageName)` | `ACTION_NOTIFICATION_LISTENER_SETTINGS` |
| Display over other apps | `Settings.canDrawOverlays(ctx)` | `ACTION_MANAGE_OVERLAY_PERMISSION` |
| Schedule Exact Alarms | `AlarmManager.canScheduleExactAlarms()` (API 31+) | `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |
| Read Calendar | `ContextCompat.checkSelfPermission(READ_CALENDAR)` | `requestPermissions()` |

Each row: permission name (bold), description (muted), status badge
(green "✓ Granted" / red "✗ Required"). Row is tappable if not granted.

### Test Alarm card:
```
[🔔  FIRE TEST ALARM NOW           ]   ← gold filled button
[🔒  TEST ON LOCK SCREEN (+5 sec)  ]   ← gold outlined button
```
Both check `Settings.canDrawOverlays()` before firing; if false, show dialog.

`TestAlarmHelper` cycles through 8 fake events:
1. "Team Standup" / "Conference Room B · 15 min"
2. "Doctor Appointment" / "Dr. Martinez · Bring insurance card"
3. "Call with Client" / "Zoom link in calendar description"
4. "Lunch with Sarah" / "The Corner Bistro · 12:30 PM"
5. "Project Deadline" / "Submit final report by end of day"
6. "Gym Session" / "Don't forget water bottle!"
7. "Flight to New York" / "Terminal 3 · Check-in closes in 2h"
8. "Birthday Dinner" / "Reservation confirmed for 7:30 PM"

---

## 18. SnoozePresetsScreen.kt

Four `Card`s. Each has:
- "Button N" label in blue
- `OutlinedTextField`: button label text
- `OutlinedTextField`: minutes (numeric, 1–10080)
- "Save" button

Helper: `"60 = 1 hour  |  1440 = 1 day  |  10080 = 1 week"`

---

## 19. SoundProfileScreen.kt

Three inner `TabRow` sub-tabs: **Sound On**, **Vibrate Mode**, **Silent**.

Each sub-tab is completely independent — its own `remember` state loaded from
`AppPrefs.getSoundProfile(mode)`, saved separately with `saveSoundProfile()`.
Changing one sub-tab must never affect another.

### Controls per sub-tab:

**🔊 SOUND**
- `Switch`: "Enable sound alarm"
- Current sound name + "Choose" button (opens `RingtoneManager` picker via
  `ActivityResultLauncher`; saves URI to profile)

**📳 VIBRATION**
- `Switch`: "Enable vibration"
- `OutlinedTextField`: pattern (comma-separated ms)
- Helper: `"Format: delay,ON,off,ON — e.g. 0,500,200,500"`

**⏱ SEQUENCING**
- Radio: "Sound starts first" / "Vibration starts first"
- `OutlinedTextField`: delay seconds (numeric)

**⏰ AUTO-SNOOZE / AUTO-DISMISS**
Section header must read exactly: **"AUTO-SNOOZE / AUTO-DISMISS"**

Fields in this order:
1. "Trigger after (seconds) — 0 to disable" → `autoDismissSeconds`
2. "Auto-snooze for (minutes)" → `autoDismissSnoozeMinutes`
3. "Max auto-snooze attempts before auto-dismiss — 0 to skip snoozing" → `autoDismissMaxRetries`

Helper: `"0 attempts = dismiss immediately. 3 = snooze 3 times, then dismiss."`

Radio: "Auto-snooze and retry" / "Dismiss immediately (no retry)"

**Save button**: full-width, confirms with Snackbar: "Settings saved"

---

## 20. SnoozedAlarmsScreen.kt

### List view:
- Load on entry via `LaunchedEffect` + refresh on back-navigation
- Empty state: `"No snoozed alarms"` centered in muted text
- Each `Card`: event title (bold) + formatted scheduled time + **"Manage"** button

### Manage Snooze view (bottom sheet or nav screen):

Shows: title, description, `"Currently snoozed until: [time]"`

**"Reschedule"** button:
1. Time picker → date picker (default = next top-of-hour / today)
2. Validate future timestamp
3. `AlarmScheduler.cancelAlarm(ctx, alarmId)`
4. `AlarmScheduler.scheduleAt(ctx, alarmEvent, newTimeMs)`
5. `AppPrefs.saveSnoozedAlarm(...)` with new `scheduledTimeMs`
6. Navigate back, refresh list

**"Cancel Snooze"** button (red):
1. Confirm dialog: "Cancel this alarm? The event will not remind you again."
2. `AlarmScheduler.cancelAlarm(ctx, alarmId)`
3. `AppPrefs.removeSnoozedAlarm(ctx, alarmId)`
4. Navigate back, refresh list

---

## 21. THEME

### res/values/themes.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.CalendarEventSnooze"
           parent="Theme.MaterialComponents.DayNight.NoActionBar" />
    <style name="Theme.CalendarEventSnooze.Alarm"
           parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="android:windowBackground">#1A1A2E</item>
        <item name="android:windowShowWhenLocked">true</item>
        <item name="android:windowTurnScreenOn">true</item>
    </style>
</resources>
```

### Alarm color constants (ui/theme/Color.kt):
```kotlin
val AlarmBackground    = Color(0xFF1A1A2E)
val AlarmSurface       = Color(0xFF16213E)
val AlarmAccentGold    = Color(0xFFFFD700)
val AlarmDanger        = Color(0xFFC0392B)
val AlarmCalendarBlue  = Color(0xFF2A5298)
val AlarmTextPrimary   = Color(0xFFFFFFFF)
val AlarmTextSecondary = Color(0xFFB0B0CC)
val AlarmTextMuted     = Color(0xFF7070A0)
```

---

## 22. KNOWN PITFALLS SUMMARY

1. **Never call `cancelNotification()` in CalendarNotificationListener** —
   original notification must stay visible.

2. **Never stop AlarmService unconditionally in AlarmActivity.onDestroy()** —
   only stop it when `userActionTaken == true`.

3. **Always override AlarmService.onTaskRemoved() with an empty body** —
   prevents service death on swipe-to-close from recents.

4. **Always return START_STICKY from AlarmService.onStartCommand().**

5. **Active alarm notification (ID 1002) PendingIntent must carry ALL
   AlarmEvent fields as extras** — required for AlarmActivity recreation.

6. **Cancel notification ID 1002 in ALL resolution paths** — snooze, dismiss,
   open calendar, auto-dismiss, auto-snooze limit reached.

7. **LongArray must be stored as List<Long> for Gson** — convert on read/write.

8. **TabRow in MainActivity needs WindowInsets.statusBars padding** —
   without it, tabs are clipped behind the status bar.

9. **autoDismissMaxRetries == 0 → dismiss immediately, no snooze** — handle
   this at the top of handleAutoDismiss() before any retry logic.

10. **All 6 snooze paths must call AppPrefs.saveSnoozedAlarm()** — presets,
    Specify Time, Time & Date dialogs, and auto-snooze in AlarmService.
    Auto-dismiss must NOT save to the list.

11. **Use FLAG_ACTIVITY_SINGLE_TOP when launching AlarmActivity** — prevents
    duplicate instances and routes to onNewIntent() if already running.

12. **AlarmManager.setAlarmClock() on API 31+ requires canScheduleExactAlarms()
    check** — fall back to set() if permission not granted.

---

## 23. FINAL BUILD CHECKLIST

- [ ] `./gradlew assembleDebug` succeeds with zero errors
- [ ] Manifest has all permissions + all 5 components (2 activities, 2 services, 1 receiver)
- [ ] AlarmActivity shows over lock screen and turns screen on
- [ ] `userActionTaken` flag in AlarmActivity controls service shutdown in onDestroy
- [ ] AlarmService.onTaskRemoved() overridden with empty body
- [ ] AlarmService.onStartCommand() returns START_STICKY
- [ ] Active alarm notification (1002) posted on service start with all AlarmEvent extras
- [ ] Active alarm notification cancelled in all 5 resolution paths
- [ ] CalendarNotificationListener never calls cancelNotification()
- [ ] All 6 snooze paths call AppPrefs.saveSnoozedAlarm() with correct scheduledTimeMs
- [ ] Auto-dismiss path calls removeSnoozedAlarm(), never saveSnoozedAlarm()
- [ ] autoDismissMaxRetries == 0 handled as immediate dismiss before retry logic
- [ ] Sound & Vibration tab has 3 independent inner sub-tabs with separate save
- [ ] Snoozed Alarms tab shows list with Manage view (Reschedule + Cancel Snooze)
- [ ] AlarmReceiver calls removeSnoozedAlarm() when alarm fires
- [ ] onNewIntent() implemented in AlarmActivity
- [ ] LongArray stored as List<Long> in Gson serialization
- [ ] TabRow has WindowInsets.statusBars top padding
- [ ] Both test alarm buttons work in SetupScreen
- [ ] TestAlarmHelper cycles through 8 fake event names
