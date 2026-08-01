# Calendar Event Snooze — working notes

Android app (Kotlin + Jetpack Compose, no XML layouts) that intercepts Google
Calendar notifications and replaces them with a full-screen alarm takeover
offering rich snooze/dismiss controls.

- **Package:** `com.calendareventsnooze` · minSdk 26 · targetSdk 35
- **Target device:** Pixel 5a, Android 14, timezone America/New_York (UTC-4/-5)
- `PROGRESS.md` is the changelog (what was built, each bug's root cause).
  This file is the durable "how it works / what will bite you" doc.

---

## Build & install

Java is **not** on PATH. Use Android Studio's bundled JDK:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Set-Location "C:\Users\droda\Documents\CalendarEventSnoozeV3"
.\gradlew.bat assembleDebug --no-daemon
```

Install to the connected phone:

```powershell
& "C:\Users\droda\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "C:\Users\droda\Documents\CalendarEventSnoozeV3\app\build\outputs\apk\debug\app-debug.apk"
```

> ⚠️ **The leading `&` is required.** The user's "Run in Terminal" button uses
> PowerShell, where a command starting with a quoted path silently fails to run
> without the call operator. Always give shell commands in PowerShell form.

`local.properties` is gitignored and machine-specific; it is regenerated per
machine and must not be committed.

---

## Architecture

```
CalendarNotificationListener ─┐
AlarmReceiver (TRIGGER_ALARM) ─┼─→ AlarmService (owns ALL ringing alarms)
TestAlarmHelper ──────────────┘        │
                                       ├─ audio + vibration
                                       ├─ sticky full-screen-intent notification (id 1002)
                                       └─ launches/updates AlarmActivity (the takeover)
AlarmScheduler → AlarmManager → AlarmReceiver (fires a snoozed alarm)
AppPrefs (SharedPreferences + Gson) — all persistence
```

- **MainActivity** hosts three tabs: Home / Snooze Buttons / Sound & Vibration.
  Snoozed Alarms lives *inside* Home (UI.4), not as its own tab.
- **AlarmActivity** is a separate `singleInstance` activity for the takeover.
- Sound settings are per **ringer mode** (SOUND_ON / VIBRATE / SILENT); the
  service picks the profile matching the phone's current ringer state.

### The alarm stack (B.6) — the most important invariant

`AlarmService` holds a **stack** of ringing alarms. A newly triggered alarm
interrupts the current one (silencing its audio) but does **not** discard it;
resolving the top alarm brings the one beneath it back to the screen. No alarm
is ever dropped without the user acting on it.

Protocol between activity and service:

- The activity never decides its own fate. On snooze/dismiss/open-calendar it
  calls `AlarmService.resolveAlarm(ctx, alarmId)` and waits.
- The service either pushes the next queued alarm (arrives via `onNewIntent`)
  or broadcasts `ACTION_ALARM_RESOLVED`, which closes the screen.
- `ACTION_STOP` (no id) stops *everything* — used only by Force Stop.

Two activity flags, easy to get wrong:

- `userActionTaken` — the user explicitly acted. Set **only** in the action
  handlers, never in a lifecycle method.
- `resolveSent` — we already told the service. Prevents `onDestroy` from
  sending a second stop that would kill a just-resumed alarm.

If neither is set, `onDestroy` must **not** stop the service — that's the
system destroying us (Home button, task swipe) and the alarm must survive.

### Takeover can't be escaped (B.1)

`AlarmActivity.onUserLeaveHint()` re-launches itself to the front, so home /
recents / quick-switch gestures bounce back. Only an explicit action or the
service resolving the alarm closes it. This relies on the overlay permission.

---

## Traps that have already cost time

**1. Material3 `DatePicker` returns UTC midnight.**
Reading `selectedDateMillis` with a local `Calendar` shifts the day back in any
timezone behind UTC. Always use `combineDateAndTime()` in `TimeFormatter.kt`.
Both date pickers (takeover + Manage) share `TimeAndDateDialog` so this is
fixed in one place — keep it that way.

**2. AlarmManager alarms do NOT survive a reboot.**
`AlarmScheduler.rescheduleAllSnoozed()` re-arms everything on `BOOT_COMPLETED`
and on app open. Without it, saved records linger with past times that can
never fire. Do not "simplify" the boot receiver back into a no-op.

**3. Calendar notifications carry no `eventId`.**
`extras.getLong("eventId")` is always `-1` from Google Calendar. The real event
is resolved by querying `CalendarContract.Instances` (±12h around the alarm
time, matched on title, closest instance wins) and opened with
`EXTRA_EVENT_BEGIN_TIME`.

**4. Never launch a package that isn't a known calendar app.**
`content://com.android.calendar/...` is claimed by unrelated apps (Messages
claims it on stock images). Falling back to "whatever handles the URI" opens
the wrong app. Only ever target `CALENDAR_PACKAGES`; skip the candidate
otherwise. Android 11+ package visibility also requires the `<queries>` block
in the manifest, or `getLaunchIntentForPackage` silently returns null.

**5. `LongArray` does not round-trip through Gson.**
`SoundProfile.vibrationPattern` is stored as `List<Long>` in a JSON mirror
class. New fields added to `SoundProfile` must be added to `SoundProfileJson`
too — make them **nullable** so profiles saved before the field existed migrate
to a sensible default instead of silently becoming 0.

**6. A vibration waveform plays once.**
"Pattern Repetitions" is implemented by concatenating the pattern N times
(leading delay dropped on repeats), not by the `repeat` index, which loops
forever.

**7. No `LazyColumn` inside a `Column` that scrolls the same direction.**
The Home tab scrolls, so the snoozed-alarm list renders as plain rows.

---

## Conventions

- **Auto-save everywhere.** Settings screens have no Save buttons. The pattern
  is a `LaunchedEffect` keyed on *every* editable state value, so no control can
  bypass persistence. Follow this for any new setting.
- Colours live in `ui/theme/Color.kt`. App palette is `App*`, alarm takeover is
  `Alarm*`. Don't hardcode hex in screens.
- Numeric fields use `KeyboardType.Number`; the **vibration pattern** field uses
  `KeyboardType.Phone` because it needs commas and the number keypad has no
  comma key.
- Commit one logical change per commit so any single fix can be reverted alone.

---

## Testing

**Emulator:** AVD `ces_test` (API 34, google_apis, x86_64).

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd ces_test -no-snapshot-load -no-window -no-audio -gpu swiftshader_indirect
```

Non-obvious testing facts:

- **Set the emulator timezone to America/New_York**, then reboot it. The default
  is UTC, which *cannot reproduce date bugs at all* — that is how the
  off-by-one-day bug reached the user.
- **`BOOT_COMPLETED` is only delivered after the first unlock.** Testing reboot
  behaviour on a locked emulator shows zero re-armed alarms and looks like a
  failure that isn't one.
- **Set `autoDismissSeconds` to 0** before UI-testing the takeover, or the alarm
  closes itself mid-test. The value persists in the AVD between sessions.
- The emulator has Google Calendar but **no account or events**, so the
  open-specific-event path cannot be verified there — it needs the real phone.
- `uiautomator dump` + regex on the XML is more reliable than screenshots for
  assertions; use screenshots for visual checks only (they cost a lot of context).
- The user's phone is often **locked**; screenshots come back black and the PIN
  must never be attempted. Verify on the emulator instead.

Physical-device-only behaviour: vibration, real lock-screen takeover, OEM
battery optimisation, real calendar notification interception, ringer switch.
