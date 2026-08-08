# Calendar Event Snooze — working notes

Android app (Kotlin + Jetpack Compose, no XML layouts) that intercepts Google
Calendar notifications and replaces them with a full-screen alarm takeover
offering rich snooze/dismiss controls.

- **Package:** `com.calendareventsnooze` · minSdk 26 · targetSdk 35
- **Target devices:** Pixel 10a / Android 17 (API 37) — primary; Pixel 5a /
  Android 14 — secondary. Timezone America/New_York (UTC-4/-5)
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

**Release builds** are signed from `keystore.properties` + `calendar-snoozer-release.jks`
in the repo root. Both are gitignored, so a fresh clone builds debug fine and
only `assembleRelease` is unavailable. **If the keystore is lost, no future
build can update an installed copy in place** — back it up.

A release APK and a debug APK **cannot replace each other**: different signing
keys, so the install fails with a signature mismatch and the app must be
uninstalled first, which wipes all saved settings.

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
  Snoozed Alarms lives *inside* Home (UI.4), not as its own tab, and its Manage
  view is a `ModalBottomSheet` owned by `HomeScreen` (M3.2).
- A `HorizontalPager` drives both the tab row and the swipe gesture, so the two
  can't disagree. Sound & Vibration nests a second pager for its sound modes:
  swiping there moves sub-tabs, and the primary tab row is how you leave.
- **AlarmActivity** is a separate `singleInstance` activity for the takeover.
- Opening the real calendar event lives in `util/CalendarLauncher.kt`, shared by
  the takeover and the Manage sheet (F.12). Traps 3 and 4 both live in there —
  keep it as the single implementation.
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

`AlarmActivity` re-launches itself to the front from **both**
`onUserLeaveHint()` and `onStop()`. Two hooks, because Android 17 does not
deliver `onUserLeaveHint()` for the overview / recents route — only for home —
so `onStop()` is the one signal that fires whichever way the activity is
backgrounded. Only an explicit action or the service resolving the alarm closes
it. This relies on the overlay permission: the relaunch is admitted as
`BAL_ALLOW_SAW_PERMISSION`, visible in logcat.

The relaunch deliberately skips when the screen is off (`PowerManager
.isInteractive`), so it does not fight a deliberate screen-off; the alarm keeps
ringing and the full-screen-intent notification brings the user back.

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

**5. `SoundProfile` is persisted through a mirror class.**
New fields added to `SoundProfile` must be added to `AppPrefs.SoundProfileJson`
too — make them **nullable** so profiles saved before the field existed migrate
to a sensible default instead of silently becoming 0. (A `LongArray` also does
not round-trip through Gson at all, which is one reason the vibration pattern is
no longer stored as one.)

**6. The vibration waveform strictly alternates off/on.**
For a **custom buzz**, `SoundProfile.buildVibrationWaveform()` expands the five
F.7 sliders (buzz-on, buzz-off, buzzes per pattern, delay between patterns,
repetitions) into `[0, ON, off, ON, ...]`. Two consecutive *off* values would
invert every subsequent element, so the gap after a pattern's last buzz **is**
the between-patterns delay — never emit both. Its repetitions are concatenated
into the array, not expressed with the vibrator's `repeat` index.

The UI.25 **presets** are the one exception: they must buzz for as long as the
alarm lasts, so they are `[0, on, off]` played with `repeat = 0` — which does
loop forever, deliberately. `vibrationRepeatIndex()` decides which, so callers
stay a single `play(waveform, repeat)`. Anything that leaves an alarm must keep
cancelling the vibrator, or a preset never stops.

**7. No `LazyColumn` inside a `Column` that scrolls the same direction.**
The Home tab scrolls, so the snoozed-alarm list renders as plain rows.

**8. Pager APIs still need `@OptIn` on this Compose version.**
`HorizontalPager` / `rememberPagerState` are `@ExperimentalFoundationApi` in
Compose 1.6 (BOM 2024.02.00) — without the opt-in the build fails with errors,
not warnings.

**9. The status bar does not follow the Compose theme on its own.**
`Theme.kt` sets `window.statusBarColor` / `navigationBarColor` explicitly. Drop
that and the bar reverts to the platform theme's indigo, which belongs to no
palette here. It tracks `LocalAppBarColors.container`, so the bar and the top
app bar can't drift apart.

**10. `setOngoing(true)` does NOT make a foreground-service notification stick.**
Since Android 13 the user can swipe an FGS notification away whatever that flag
says. B.6 needs three layers: `setOngoing`, `FLAG_NO_CLEAR` (set on the built
`Notification`, NotificationCompat has no builder for it), and a
`setDeleteIntent` that re-posts via `ACTION_REASSERT_NOTIFICATION` while the
alarm stack is non-empty. A *programmatic* `cancel()` does not fire the delete
intent, so resolving an alarm still clears it — don't "simplify" the reassert
handler into an unconditional re-post or dismissing will resurrect it.

**11. The adaptive-icon safe zone is a CIRCLE, not a square.**
"66 of 108dp" is a 66dp-*diameter circle*, so a glyph clears it only if its
**diagonal** is ≤ 66dp — a 53 × 65.5dp glyph fits the square and still gets
clipped by a Pixel's round mask. Measure the diagonal, not the sides. The
foreground in `mipmap-*/ic_launcher_foreground.png` is the supplied art scaled
to 78.3% for exactly this reason; re-exporting it at "full size" will clip it
again.

**12. `USE_FULL_SCREEN_INTENT` is revocable and silently degrades the takeover.**
Since Android 14 the user can turn it off per app. Without it the alarm never
takes over the screen — it becomes an ordinary heads-up notification with no
error anywhere. It is checked in the Permissions card for exactly that reason;
don't drop it from `allGranted`.

**13. Don't set `statusBarColor` / `navigationBarColor`.**
Both are deprecated and **ignored from Android 15 onward** for targetSdk 35.
The app calls `enableEdgeToEdge()` and lets the M3 top app bar paint the
status-bar area, which behaves identically on every version. Re-adding those
setters "to fix" the bar only works on Android 14 and misleads on newer ones.

**14. Never round-trip a source file through PowerShell `Get-Content`/`Set-Content`.**
PowerShell 5.1 reads as ANSI unless told otherwise, so every em dash in the
comments comes back as `â€"` and gets written out corrupted. Use the Edit tool
for source edits. If a bulk rewrite is unavoidable, use
`[System.IO.File]::ReadAllLines` / `WriteAllLines` with an explicit
`UTF8Encoding($false)`, then grep for `â€` to confirm.

**15. The alarm volume slider changes a system setting.**
F.10 overrides the phone's alarm volume with
`AudioManager.setStreamVolume(STREAM_ALARM, …)` — `MediaPlayer.setVolume` only
scales *within* the current stream volume, so it cannot make the alarm louder
than the phone is set to. The previous level is captured once per alarm and
restored in `stopSoundAndVibration()`, which every stop path runs. Any new exit
path must keep going through there or the user's volume stays overridden.

**16. The app is named "Calendar Snoozer" but the package is not.**
`applicationId`, the `com.calendareventsnooze` package and every class name keep
the old spelling on purpose — renaming them would orphan every saved
SharedPreference and scheduled alarm. Only `app_name` changed.

**17. `finish()` does not remove the takeover's task from recents.**
`AlarmActivity` is `singleInstance`, so it is always the root of a task of its
own. Finishing it leaves an empty task record whose **base intent is the alarm**,
and returning to the app from the overview replays that intent — the takeover
reopens for an alarm the user already resolved (B.7). Every close path must go
through `closeTakeover()` (`finishAndRemoveTask()`), and the manifest entry keeps
`excludeFromRecents` + `autoRemoveFromRecents`.

To check it: `dumpsys activity recents` **still lists** the takeover's task while
the alarm rings — `excludeFromRecents` only hides it from the overview UI, it
does not remove the record. Read the task's `intent=` flags instead:
`0x00800000` is `FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS`, and `autoRemoveRecents=true`
appears on the line below. What must be true *after* the alarm is resolved is
that no task with `baseIntent … AlarmActivity` remains at all.

---

## Design system (M3.1)

The UI is Material Design 3 throughout. `ui/theme/` owns it:

- **`Color.kt`** — the five source swatches (Lavender, PowderBlue, BlueSlate,
  Granite, Evergreen) and the full light **and** dark M3 role sets derived from
  them. Screens must read `MaterialTheme.colorScheme.*`, never the swatches, or
  they break in one of the two schemes. Light: Blue Slate primary. Dark:
  Evergreen as the ground, Powder Blue primary.
- **`Type.kt`** — the 15-style M3 type scale in DM Sans (bundled variable font,
  `res/font/dm_sans.ttf`, OFL in `third_party/`). Google Sans is proprietary and
  cannot be bundled; DM Sans is the stand-in. Use `MaterialTheme.typography.*`,
  not `fontSize =`.
- **`Shape.kt`** — the M3 shape scale plus a 4dp-grid `Spacing` object. Use
  `MaterialTheme.shapes.*` and `Spacing.*`; don't write raw `.dp` or
  `RoundedCornerShape`.
- Dynamic colour is deliberately **off** — the palette is the brand.
- Destructive actions (Force Stop, Cancel Snooze, Dismiss) take the M3 **error**
  roles, not an arbitrary red.

The **alarm takeover is the one exception**: it keeps its own always-dark
`Alarm*` colours instead of following the system scheme, because it fires on a
lock screen at night and needs maximum contrast. It still uses M3 shape, type
and spacing.

## Conventions

- **Auto-save everywhere.** Settings screens have no Save buttons. The pattern
  is a `LaunchedEffect` keyed on *every* editable state value, so no control can
  bypass persistence. Follow this for any new setting.
- Numeric fields use `KeyboardType.Number`.
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
- **`uiautomator dump` does not capture Compose popups.** A dropdown menu,
  `AlertDialog` content or `ModalBottomSheet` rendered in its own window can be
  fully open on screen and completely absent from the XML. An "it didn't open"
  conclusion drawn from the dump alone is worthless — and tapping again to
  "retry" just closes it. Take a screenshot for anything in a popup.
- **Never pipe `adb exec-out screencap -p` to a file in PowerShell** — the shell
  re-encodes the stream and prepends a BOM, producing an unreadable PNG. Use
  `adb shell screencap -p /sdcard/x.png` then `adb pull`.
- Settings are readable straight from the device on a debug build:
  `adb shell run-as com.calendareventsnooze cat
  /data/data/com.calendareventsnooze/shared_prefs/ces_prefs.xml`. This is the
  fastest way to prove auto-save and JSON migration actually worked.
- The user's phone is often **locked** (screenshots come back black, and the PIN
  must never be attempted) — but they will unlock it on request for a debugging
  session, so ask rather than defaulting to the emulator. The emulator is a poor
  substitute anyway: it has no calendar account, and a cold boot often throws a
  "Pixel Launcher isn't responding" ANR over the app.

Physical-device-only behaviour: vibration, real lock-screen takeover, OEM
battery optimisation, real calendar notification interception, ringer switch.

### Measuring notifications — do NOT grep `dumpsys notification`

`dumpsys notification` includes `mArchive`, a history of *past* notifications,
so grepping it for the package reports a notification long after it was
cancelled. That produced a convincing phantom "orphaned notification after
dismiss" bug that did not exist. Use `adb shell cmd notification list`, which
lists only live notifications.

### The 60-second auto-snooze silently invalidates takeover tests

The default profiles auto-snooze after 60s (30s in SILENT). Any takeover test
that takes longer than that is measuring an alarm that already resolved itself
— which looks exactly like "the takeover escaped". Set `autoDismissSeconds` to
0 first, or keep the whole test under ~40s.

### `pm clear` does NOT cancel scheduled alarms

Clearing app data wipes the saved records but leaves the `AlarmManager`
registrations, so test alarms still fire later with no matching record.
`adb uninstall` removes them, and cancelling each snooze through the Manage
sheet removes them without wiping the user's settings. Always check after a
testing session or the user gets alarms at 3am.

Read that check correctly: most `TRIGGER_ALARM` lines in `dumpsys alarm` sit
under **`Snapshot:`**, which is historical statistics, not a queue — they
persist long after the alarm is cancelled. The authoritative list is
**`Pending alarms per uid`**; if the app's uid is absent from it, nothing is
scheduled. Same shape of trap as the notification archive above.
