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
                                       ├─ sticky low-importance notification (id 1002)
                                       └─ launches/updates AlarmActivity (the takeover)
AlarmScheduler → AlarmManager → AlarmReceiver (fires a snoozed alarm)
AppPrefs (SharedPreferences + Gson) — all persistence
```

- **MainActivity** hosts four destinations in a bottom `NavigationBar`:
  Home / Alarm Screen / Sound & Vibration / Settings. Home is the two alarm
  lists and nothing else; the Manage view is a `ModalBottomSheet` owned by
  `HomeScreen` (M3.2). Permissions lives on Settings, and the test tools are a
  floating cluster in the Scaffold rather than a card on any one screen.
- A `HorizontalPager` drives both the navigation bar and the swipe gesture, so
  the two can't disagree. Sound & Vibration nests a second pager for its sound
  modes: swiping there moves sub-tabs, and the navigation bar is how you leave.
- **Permissions are read in one place.** `readPermissions()` in
  `SettingsScreen.kt` returns the whole `PermissionsStatus`, and both the nav
  bar's badge and the Permissions card render from it. Adding a permission means
  adding it there, or the badge count and the card silently disagree.
- **AlarmActivity** is a separate `singleInstance` activity for the takeover.
- Opening the real calendar event lives in `util/CalendarLauncher.kt`, shared by
  the takeover and the Manage sheet (F.12). Traps 3 and 4 both live in there —
  keep it as the single implementation.
- Sound settings are per **ringer mode** (SOUND_ON / VIBRATE / SILENT); the
  service picks the profile matching the phone's current ringer state.

### What the listener accepts (round 27)

`CalendarNotificationListener` is not "every notification from a watched
package" any more. In order: the package must be watched, the master switch on,
and the time outside Silent Hours; then outside Shabbat & Yom Tov Mode, and the
notification must not be a group summary or an ongoing one, must have a title or
text, and must not repeat one already alarmed. Only then is the account filter
consulted. Every decision, with how long after posting it arrived, is written to a
rolling log in preferences — no UI, read it with `adb shell run-as
com.calendareventsnooze cat .../ces_prefs.xml` when a ghost alarm is reported.

Account filtering **fails open at every step**. A notification carries no event
id (trap 3), so the event is matched by title and time through
`CalendarEvents.resolve` and its calendar gives the account; no permission, no
match, or an unreadable calendar all mean "let the alarm through". Suppressing
an alarm on a guess is the one outcome worth avoiding, and the Settings card
tells the user this rather than implying a guarantee.

### Shabbat & Yom Tov Mode (round 28)

Nothing may ring during Shabbat or Yom Tov in the selected city. Unlike Silent
Hours, that includes alarms already in motion, so **any new way to ring an alarm
must check `ObservanceCalendar.isSilenced`**. Today there are three:

- A new reminder is left as the calendar app posted it (the listener).
- A snooze coming due goes to Missed Alarms with no sound, takeover or
  notification (`AlarmReceiver`). So does one found overdue on reboot or app open
  (`rescheduleAllSnoozed`). The due time counts as well as the clock, so a late
  delivery cannot ring after the period ends.
- An alarm ringing when the period begins is ended the same way, by a cutoff
  armed in `presentTopAlarm`.

The Test buttons still ring: Test Alarm directly, and Test Alarm +5 because it
reaches the receiver with no snooze record.

`assets/shabbat_yomtov/*.tsv` are the user's rows, unedited, through 27 Oct 2046.
Times are wall-clock in the **city's** zone, not the phone's. `mergeRows` joins
overlapping rows, and a row starting on the date the last period ended: on eight
Saturday nights the Miami file starts Yom Tov 8 minutes after Shabbat ends, and
the user confirmed those are one continuous period. All three cities merge to
1109 periods; `ObservanceCalendarTest` runs against the real assets.

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

### The auto-dismiss countdown is managed, not fire-and-forget

It is a `Runnable` the service holds, with its deadline — **not** an anonymous
lambda on the shared `handler`, because the only way to cancel one of those is
`removeCallbacksAndMessages(null)`, and that queue also carries the sound
sequencing and per-output delays. Three things drive it:

- **"Shhhh"** restarts it from full. It means "not yet", so letting the
  remaining seconds expire and file the alarm as missed is the wrong answer.
- **Opening a snooze picker** pauses it; closing one resumes from what was
  left. Choosing a time takes longer than a 30-second countdown allows, and the
  alarm used to resolve itself out from under the open dialog.
- A countdown resumed with ~nothing left gets a second's grace, or Cancel would
  look like it dismissed the alarm.

`AlarmScreen`'s on-screen counter mirrors this: `secondsLeft` lives **outside**
the `LaunchedEffect` that ticks it, so re-keying the effect pauses and continues
the count. "Shhhh" has to reset the value *and* re-key, or a counter that had
already reached zero would never tick again.

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

**Sideloaded builds keep it; Play builds may not.** Android 14+ grants this
permission to *every* app at install — it is the **Google Play Store** that then
revokes it for apps whose core function isn't calling or alarms. So a sideloaded
APK keeps it (which is why it reads granted on the Pixel), and there is no
manifest attribute that changes this. Publishing on Play would require the
full-screen-intent declaration in the Play Console; `android:appCategory` is
about data-usage grouping and does nothing here. Don't "fix" this in code.

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

**15a. Changing a preset default changes nothing on an existing install.**
`AppPrefs.defaultSoundProfile()` is only consulted when no profile has been
saved for that ringer mode. Every "the preset values should be X" request
(UI.18.1, UI.25.3, UI.25.4) therefore only shows up on a **fresh install** —
verify it by `adb uninstall` + install on the emulator, never by looking at a
phone that has been used. Say so when reporting, or it reads as not done.

**15. The alarm volume slider changes a system setting.**
F.10 overrides the phone's alarm volume with
`AudioManager.setStreamVolume(STREAM_ALARM, …)` — `MediaPlayer.setVolume` only
scales *within* the current stream volume, so it cannot make the alarm louder
than the phone is set to. The previous level is captured once per alarm and
restored in `stopSoundAndVibration()`, which every stop path runs. Any new exit
path must keep going through there or the user's volume stays overridden.

`util/SoundTest.kt` (UI.25.3's "Test Alarm Sound") carries the same hazard for
the same reason, and `SoundTest.stop()` is its single exit — Stop Test, the
completion callback and the screen's `DisposableEffect` all call it. Add a
fourth way to end a test and it must call `stop()` too.

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

**19. Watching a mail app turns every email into a 3am alarm.**
`CalendarApps` splits its registry in two on purpose. `DEDICATED` is calendar
apps whose only notifications are reminders — safe to watch by default.
`MIXED_USE` (Gmail, Outlook, K-9, Any.do) is offered in the Settings picker but
**never** on by default, because the listener intercepts *every* notification
from a watched package. This was caught on the phone: the first cut defaulted to
"all known calendar apps installed", which silently included Gmail.

`CalendarApps.KNOWN` and the manifest's `<queries>` block must stay in step — a
package missing from `<queries>` is invisible to `getLaunchIntentForPackage` on
Android 11+, so it can be neither offered in the picker nor opened for an event.

**20. Optional permissions must stay out of `pendingCount`.**
`PermissionsStatus.batteryUnrestricted` is recommended, not required, so it is
deliberately excluded from `pendingCount` and therefore from both the nav-bar
badge and "All permissions are granted". Adding a genuinely required permission
means adding it to the `pendingCount` list too; adding an optional one means
*not* doing that.

**24. `AudioManager.getRingerMode()` lies while Do Not Disturb is on.**
Android keeps two ringer modes: the *internal* one the user set, and the
*external* one apps are told. DND forces the external one to SILENT and leaves
the internal one alone — so `getRingerMode()` reports SILENT on a phone set to
Sound, and the app picked the Silent profile for every alarm, permanently, for
anyone on a DND schedule. `getRingerModeInternal()` is the right API and is not
public, so `util/RingerModes.kt` reads the setting `AudioService` persists the
internal mode to and falls back to `getRingerMode()`. Confirm with
`adb shell dumpsys audio | grep -A3 "^Ringer mode:"`, which prints both:

    - mode (internal) = NORMAL
    - mode (external) = SILENT

Alarms are deliberately **not** silenced by DND; quiet hours are the app's own
Silent Hours & Days.

**25. Notification extras are CharSequences; `getString` returns null for them.**
`Bundle.getString("android.title")` returns null whenever the value is a
CharSequence that is not a String, and notification titles are often
SpannableStrings. Always `getCharSequence(key)?.toString()`. This is a suspected
cause of the blank "ghost alarm", **not a confirmed one**: no real Google Calendar
notification has been inspected. The interception log will show what they hold.

**26. Binding does NOT replay the shade, despite what round 27 said.**
Round 27 blamed ghost alarms on `onNotificationPosted` being re-delivered for
notifications already on screen, without measuring it. Round 28 measured it on
API 34: with 14 live notifications from a watched package, neither a rebind
(`disallow_listener` / `allow_listener`) nor `adb install -r` delivered anything.
Android 17 is untested, and **the cause of the ghost alarms is still unknown**.
Round 29 removed the two-minute staleness rule built on that theory, at the
user's request, because it could silently drop a reminder that arrived late. The
listener still ignores group summaries, ongoing notifications, contentless ones
and in-place updates of a notification already alarmed; none of those has been
shown to stop a ghost. `interception_log` records each notification's age on
arrival instead. Read it after the next report, before theorising.

**28. `adb root` breaks `cmd notification post`, and seeding prefs races the app.**
Simulating a reminder means watching `com.android.shell`, so testing listener
behaviour needs no calendar account. Two things make it look broken. Under
`adb root` the post throws inside the system server yet still prints "posting:",
so run it as `su 2000 cmd notification post -S bigtext -t TITLE TAG TEXT`. And the
bound listener keeps the process alive, so a live process overwrites any prefs
file written beneath it: `disallow_listener`, `force-stop`, copy the file,
`allow_listener`. Always run a control that must ring; silence proves nothing
until the same setup has been seen to alarm.

To test anything date-bound, use the emulator: `adb root`, turn `auto_time` off,
then `adb shell date MMDDhhmmYYYY.ss`. Clock jumps often raise "System UI isn't
responding", so check for that dialog before tapping.

**27. Channel importance draws the heads-up banner, not the full-screen intent.**
A notification announcing the alarm appeared *over* the takeover. Dropping its
full-screen intent was measured and **did not** help: IMPORTANCE_HIGH alone is
enough. A foreground service must post something, so the ringing-alarm
notification lives on an IMPORTANCE_LOW channel (`ces_alarm_ongoing`) and the
full-screen intent moved to a second notification on the HIGH channel, posted
only when the takeover has failed to reach the screen within 1.5s. B.6 is
unaffected on the low channel — importance governs the banner, not
dismissibility, so ongoing, `FLAG_NO_CLEAR` and the re-assert delete intent all
still work. A channel's importance cannot be changed after it is created, which
is why this is a new channel id rather than an edit to the old one.

**21. A Compose wheel picker needs blank end rows, not `contentPadding`.**
The usual recipe — a 3-row-tall `LazyColumn` with `contentPadding = vertical
itemHeight` and `firstVisibleItemIndex` as the selection — looks right and is
ambiguous: the padding counts as content in some of LazyColumn's measurements
and not others, so which index is *centred* shifts by one depending on where
the scroll happens to be. `NumberWheel` instead puts a blank row at each end
and reads the centred item out of `layoutInfo` by distance from the viewport's
midpoint. Then `scrollToItem(i)` puts list item `i` flush at the top, the
middle row is list item `i + 1` — value `i` — and snapping lands on exactly
those positions. `rememberSnapFlingBehavior` is still `@ExperimentalFoundationApi`
on this Compose version, same as the pager (trap 8).

**22. `POST_NOTIFICATIONS` is denied by default and fails silently.**
Android 13+ drops any `notify()` from an app without it, with no exception and
nothing in logcat. It was **off on the user's own Pixel**, which is how a
missed-alarm notification could be "working" in code and invisible in life.
It is in the Permissions card and in `pendingCount` because the feature it
gates — telling the user an alarm was missed — is the one this app exists for.
Note the foreground-service notification is unaffected, so the alarm itself
still behaves normally; only the missed-alarm notification vanishes. Also note
the request dialog stops appearing after two declines and the launcher then
returns "denied" instantly, so the row falls through to app notification
settings.

**23. The takeover's portrait lock depends on targetSdk staying ≤ 35.**
`android:screenOrientation="portrait"` on `AlarmActivity` is what stops
auto-rotate turning the alarm on its side. From **targetSdk 36** Android
ignores activity orientation locks on displays 600dp and wider — so raising
targetSdk silently un-does this. Check `requestedOrientation` in
`dumpsys activity activities` together with `ignoreOrientationRequest`; the
manifest attribute alone no longer proves the behaviour.

**18. `OutlinedTextField` cannot simply be made shorter.**
Its decoration box reserves a fixed slice for the floating label and centres the
input in what is left, so `Modifier.height(48.dp)` renders the *value* clipped
in half rather than a shorter field — it looks fine in a layout preview and
wrong the moment there is text in it. Shrinking one means driving
`OutlinedTextFieldDefaults.DecorationBox` from a `BasicTextField` and taking the
height out of `contentPadding`, which is what `CompactTextField` in
`SnoozePresetsScreen.kt` does.

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

The **alarm takeover is the one exception**: it never follows the system scheme,
because it fires on a lock screen at night. Since UI.29 it has two of its own
colour sets — `DarkAlarmPalette` and `LightAlarmPalette` in
`ui/theme/AlarmPalette.kt` — chosen explicitly on the Alarm Screen tab and
provided through `LocalAlarmPalette`; the default is dark. `AlarmScreen` must
read every colour from that local, never from an `Alarm*` constant directly, or
one style silently keeps the other's colours. It still uses M3 shape, type and
spacing.

Two roles in there are easy to get wrong:

- **`danger` is a container colour, `dangerText` is not.** The auto-dismiss
  countdown is loose text on the background. Painting it with `danger` is fine
  on the dark ground and near-invisible on the light one — pale pink on
  near-white, caught on the phone.
- **`calendar` is deliberately the same in both palettes.** UI.17 asked the
  "Also Open Calendar Event" row to carry the "Fire test alarm now" colours;
  routing it through the palette must not restyle it as a side effect.

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
- **To prove a vibration is live, read `CurrentVibration` — not `currentState`.**
  In `dumpsys vibrator_manager`, `currentState = IDLE` is the amplitude between
  the on-segments of a waveform and reads IDLE while the vibration is very much
  running. The authoritative line is `CurrentVibration: … status = running`,
  which becomes `null` once it is cancelled. `Recent vibrations` also shows each
  one's actual duration, which is how a cut-short test can be told from one that
  ran to completion.
- **Don't regex the whole UI dump for a screen's identity.** The tab labels are
  in every dump, so matching "Snooze Buttons" proves nothing about which tab is
  showing. Match on content unique to that screen ("No snoozed alarms",
  "Changes are saved automatically.") — a tab-label match once looked exactly
  like a broken back button that worked fine.
- **`uiautomator dump` does not capture Compose popups.** A dropdown menu,
  `AlertDialog` content or `ModalBottomSheet` rendered in its own window can be
  fully open on screen and completely absent from the XML. An "it didn't open"
  conclusion drawn from the dump alone is worthless — and tapping again to
  "retry" just closes it. Take a screenshot for anything in a popup.
- **Never pipe `adb exec-out screencap -p` to a file in PowerShell** — the shell
  re-encodes the stream and prepends a BOM, producing an unreadable PNG. Use
  `adb shell screencap -p /sdcard/x.png` then `adb pull`.
- **Force-stop before reading `ces_prefs.xml`, or the value is a lie.**
  `SharedPreferences.apply()` writes asynchronously, so a read taken seconds
  after a UI change can return the *previous* contents — three consecutive reads
  returned three different values while chasing a phantom migration bug across
  rounds 23 and 24. `adb shell am force-stop com.calendareventsnooze`, wait a
  couple of seconds, then read.
- **Never tap a control from coordinates in an older dump.** Expanding a card
  moves everything below it, so a stale y-coordinate lands on whatever slid into
  that spot — in round 24 that silently rewrote the user's day selection twice.
  Dump, then tap, in that order; and for a switch, match the `checkable="true"`
  node rather than guessing an x near the right edge.
- Settings are readable straight from the device on a debug build:
  `adb shell run-as com.calendareventsnooze cat
  /data/data/com.calendareventsnooze/shared_prefs/ces_prefs.xml`. This is the
  fastest way to prove auto-save and JSON migration actually worked.
- **The user can be touching the phone while you drive it.** A Shhhh test once
  ended with the takeover closed a second early and an unrelated missed alarm
  gone from the list; an identical second run behaved correctly and nothing was
  in the crash buffer. Before writing up an anomaly seen on *their* device, run
  it again — and say plainly which of the two you are reporting.
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
