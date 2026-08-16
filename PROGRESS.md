# Build Progress — Calendar Event Snooze

Legend: [ ] NOT STARTED | [~] IN PROGRESS | [x] DONE

## Parts 1–3 (Build)
- [x] 1. Project Overview
- [x] 2. Build Configuration (settings/build.gradle.kts, gradle.properties, wrapper)
- [x] 3. AndroidManifest.xml
- [x] 4. File Structure
- [x] 5. Data Models (AlarmEvent, SnoozePreset, SoundProfile, SnoozedAlarmRecord)
- [x] 6. Utility — AlarmEventIntentUtils.kt
- [x] 7. Utility — TimeFormatter.kt
- [x] 8. Persistence — AppPrefs.kt
- [x] 9. Application Class — CalendarEventSnoozeApp.kt
- [x] 10. AlarmScheduler.kt
- [x] 11. CalendarNotificationListener.kt
- [x] 12. AlarmService.kt
- [x] 13. AlarmReceiver.kt
- [x] 14. AlarmActivity.kt
- [x] 15. AlarmScreen.kt
- [x] 16. MainActivity.kt
- [x] 17. SetupScreen.kt (+ TestAlarmHelper)
- [x] 18. SnoozePresetsScreen.kt
- [x] 19. SoundProfileScreen.kt
- [x] 20. SnoozedAlarmsScreen.kt
- [x] 21. Theme (themes.xml, Color.kt, Theme.kt, Type.kt, strings.xml)
- [x] 22. Known Pitfalls verification
- [x] 23. Final Build Checklist — ./gradlew clean assembleDebug: BUILD SUCCESSFUL, zero errors, zero warnings. APK: app/build/outputs/apk/debug/app-debug.apk

## Part 4 (Test)
- [x] 24. Emulator Smoke Test — API 34 AVD (ces_test). 11/12 checks PASS; check 12 (swipe-from-recents survival) INCONCLUSIVE on emulator (code protections present; needs physical device). See EMULATOR_TEST_RESULTS.md.

## Feedback round 1 (device testing on Pixel 5a / Android 14) — DONE, build clean
- [x] B.1 Takeover can't be dismissed by navigation gestures — AlarmActivity.onUserLeaveHint() re-asserts the takeover (home/quick-switch/overview all bounce back); only snooze/dismiss/open-calendar or service resolution closes it. Backed by full-screen-intent notification + immersive mode.
- [x] B.2 Ghost sound after dismiss/snooze — AlarmService now cancels ALL pending handler callbacks on stop (not just auto-dismiss), stops any existing MediaPlayer before starting a new one, ignores null-intent sticky restarts, and broadcasts ACTION_ALARM_RESOLVED so the screen closes in lockstep with the service.
- [x] F.1 Tab order/rename: Home, Snoozed Alarms, Snooze Buttons, Sound & Vibration.
- [x] F.2 Home tab: large lime FORCE STOP button (silences + clears notifications + kills process), Test Alarm section, collapsible Permissions with green ✓ (#34A853) / yellow warning status icon.
- [x] F.3 Sticky full-screen-intent notification (channel ces_alarm_active) returns the user to the takeover.
- [x] UI.1 Mellow palette: bg #F1F3F4, tab bar #5F6368, regular buttons #E8F5E9, Force Stop #C6FF00, accent blue #4285F4. Alarm takeover kept high-contrast (accent blue applied to Open Calendar).
- Verified on emulator: clean build, no crash, Home layout + tab order + colors + collapsible permissions all render. B.1/B.2 gesture behaviour must be confirmed on the physical device.

## Feedback round 2 (Pixel 5a / Android 14) — DONE, build clean, verified on emulator @ America/New_York
- [x] B.3 Time & Date snoozed one day early — Material3 DatePicker returns **UTC midnight**; the code read it with a local Calendar, shifting the day back in every timezone behind UTC. New shared `combineDateAndTime()` extracts Y/M/D in UTC. Verified: picking Aug 5 → "Wed Aug 5 at 1:00 PM".
- [x] B.4 Snoozed Alarms → Reschedule same bug — now reuses the takeover screen's `TimeAndDateDialog`, so one implementation serves both. Verified: reschedule to Aug 12 → "Wed Aug 12 at 1:00 PM".
- [x] B.5 Open Calendar Event showed an app chooser then "Couldn't load object" — two causes: (1) no `<queries>` element, so Android 11+ package visibility made `getLaunchIntentForPackage` return null; (2) the fallback used a bare `ACTION_VIEW` on `CalendarContract.CONTENT_URI`, which unrelated apps claim (Messages claims it on stock images). Now every candidate intent is aimed at a **known calendar package only**, else skipped. Verified: opens Google Calendar directly, no chooser.
- [x] B.6 Second alarm silently discarded the first — AlarmService now keeps an **alarm stack**: a new alarm interrupts (silencing the old audio) but the previous one resumes when the top is snoozed/dismissed. Verified 2-deep and 3-deep: each alarm required its own dismiss; nothing lost.
- [x] B.7 Snoozed alarms stuck with past date/time — root cause: **AlarmManager alarms do not survive a reboot**; the BOOT_COMPLETED branch was an empty no-op. Now re-arms all saved alarms on boot and on app open, reviving past-due ones (staggered) instead of leaving dead rows. Verified: after `adb reboot` with the app never opened, both alarms were re-registered with AlarmManager.
- [x] F.4 Manage Snoozed Alarms — added the 4 snooze presets (2×2, #F1F3F4/black) + Specify Time (#5F6368); Reschedule renamed "Date & time" (#E8F5E9).
- [x] F.5 Sound & Vibration auto-saves; Save button removed from all three sub-tabs. Verified: edited a field, value persisted with no save action.
- [x] UI.2 Takeover restyle — background #202124, Open Calendar button lowered (all content shifted down), snooze/Specify Time/Date & Time buttons 1.5× taller with 2× borders in #F1F3F4 / #5F6368 / #E8F5E9.
- Emulator note: tested at **America/New_York**, not UTC. The default UTC emulator cannot reproduce B.3 at all — that is why the first round missed it.

## Feedback round 3 — DONE, build clean (0 errors, 0 warnings), UI verified on emulator
- [x] B.5 (round 2 follow-up) opened Calendar but not the event — cause: Google Calendar does **not** put `eventId` in its notification extras (always -1), so the event-URI branch never ran. Now the event is looked up in the calendar provider (`Instances`, ±12h around the event time, matched on title, closest instance wins) and opened with `EXTRA_EVENT_BEGIN_TIME`. **Needs a real synced calendar to confirm** — the emulator has no events.
- [x] F.6 Numeric keypad on Specify Time (Hours/Minutes) and the vibration pattern field. Pattern uses the *phone* keypad rather than the pure-number one, because the number keypad has no comma key and the pattern needs commas.
- [x] F.7 "Pattern Repetitions" (default 4). A waveform plays once, so N repetitions concatenates the pattern N times (leading delay dropped on repeats). Old saved profiles migrate to 4 via a nullable JSON field rather than silently becoming 0.
- [x] F.8 Snooze Buttons auto-save; Save buttons removed.
- [x] UI.3 Takeover: Time & Date now #5F6368, Specify Time and Time & Date at the old snooze font size, snooze presets 1.5x that, and Open Calendar Event moved below Dismiss in #FF8C8A with "(DISMISSED ALARM)".
- [x] UI.4 Snoozed Alarms moved into Home (heading, list, Manage; "No Snoozed Alarms" when empty), above Force Stop → Test Alarm → Permissions. Tabs are now Home / Snooze Buttons / Sound & Vibration. Added a Gmail-style scrollbar that appears only when the content overflows.
- [x] UI.5 Fire Test Alarm Now matches the lock-screen test button.
- [x] UI.6 Each Sound & Vibration sub-tab keeps its scroll position (scroll state hoisted per mode).
- [x] UI.7 First field renamed "Trigger Auto-Snooze after (seconds) — 0 to disable"; the two radios replaced by a single "Auto-Snooze ON" switch directly under the section heading.
- [x] UI.8 Snooze Buttons laid out 1 | 2 over 3 | 4, matching the takeover screen.
- [x] UI.9 App title bar "CALENDAR EVENT SNOOZE" in the Force Stop colour with black text, plus grey half-size "(IFYKYK)".

## Feedback round 4 — build clean (0 errors, 0 warnings)
- [x] F.7 (round 3 rework) Vibration is now five sliders instead of a comma-separated pattern field: Buzz-On Length / Buzz-Off Length (S 250, M 500, L 1000, XL 2000, XXL 3000 ms; preset M), Number of Buzzes Pattern (1–10, preset 5), Delay Between Patterns (S 500, M 1000, L 2000, XL 3000, XXL 5000 ms; preset L), Number of Pattern Repetitions (1–10, preset 5). `SoundProfile.vibrationPattern`/`vibrationRepeat` are gone; `buildVibrationWaveform()` expands the five values into the waveform. **The gap after the last buzz of a pattern IS the between-patterns delay** — a waveform strictly alternates off/on, so two off values in a row would invert the whole pattern from that point. Saved profiles migrate via nullable JSON fields.
- [x] UI.3 Open Calendar Event: "(DISMISSED ALARM)" → "(DISMISSES ALARM)", moved onto its own centred line below the label, 10sp → 11sp.
- [x] UI.10 Home: Force Stop button is #FF8C8A and reads "FORCE STOP APP"; the ⏹ emoji (which cannot be recoloured) is replaced by a drawn square with a red border. Permissions status ✓/⚠ moved to the right of the heading. "Test Alarm" heading left unchanged (confirmed with the user).
- [x] UI.11 Sound & Vibration: the Home tab's overflow-only scrollbar now also draws on each sub-tab (extracted to `ui/screens/Scrollbar.kt`), and the selected sub-tab is hoisted into MainActivity so leaving the tab and returning lands on the same one.
- [x] UI.9 "(IFYKYK)" raised from 9sp to 18sp, matching the title text.
- Note: the title bar keeps the old lime #C6FF00; only the Force Stop *button* changed colour, as specified.

## Feedback round 5 — Material Design 3 redesign
- [x] M3.1 Whole UI rebuilt on Material Design 3. New `Color.kt` holds full light **and** dark colour schemes derived from the five-swatch palette (Lavender #C5D1EB, Powder Blue #92AFD7, Blue Slate #5A7684, Granite #395B50, Evergreen #1F2F16); the app follows the system setting. Light: Blue Slate primary, Granite secondary, near-white blue-cast neutrals. Dark: Powder Blue primary, Evergreen as the ground and card surface. Dynamic colour is deliberately off so the palette stays the brand. New `Shape.kt` adds the M3 shape scale plus a 4dp-grid `Spacing` object; screens no longer hardcode dp or hex.
- [x] M3.1 Typography — DM Sans (bundled variable font, OFL, `third_party/DM_Sans-OFL.txt`) across the full 15-style M3 type scale. Google Sans itself is proprietary and cannot be bundled; DM Sans is the closest freely licensed match.
- [x] M3.1 Components — `CenterAlignedTopAppBar` + `PrimaryTabRow` replace the hand-rolled title bar; `SecondaryTabRow` for the sound sub-tabs; cards, `FilledTonalButton`, `OutlinedButton`, `AssistChip` (permission status), `SingleChoiceSegmentedButtonRow` (sound-vs-vibration ordering), `ModalBottomSheet`, M3 dialogs.
- [x] M3.1 Alarm takeover restyled to M3 shape/type/spacing but kept **always dark and high-contrast** (darkest Evergreen ground, Lavender text) rather than following the system scheme — it fires on a lock screen at night. `themes.xml` windowBackground updated to match, or the takeover flashes the old navy before Compose draws.
- [x] M3.2 Manage snoozed alarm is now a `ModalBottomSheet` over Home instead of replacing the whole tab. `ManageSnoozeView` became `ManageSnoozeSheet` (content only; the sheet and its dismissal belong to HomeScreen).
- [x] M3.3 Home tab's three sections (Snoozed Alarms / Test Alarm / Permissions) each sit in their own card with 24dp between them.
- [x] M3.4 Snooze Buttons — each of the four presets is an `OutlinedCard`, so its heading and two fields read as one bounded group.
- [x] M3.5 Swiping moves between tabs (`HorizontalPager` drives the tab row, so the two cannot disagree). On Sound & Vibration the swipe moves between the three sound modes; the primary tab row is how you leave that tab.
- Pager APIs are still `@ExperimentalFoundationApi` on Compose 1.6 (BOM 2024.02.00) — both pagers need `@OptIn`.

## Feedback round 6
(Labels B.6 / F.8 repeat earlier, unrelated items — this round's entries are the ones below.)
- [x] B.6 "Calendar Alarm Active" notification now persists until the alarm is resolved. It already had `setOngoing(true)`; the cause is that **Android 13+ lets the user dismiss a foreground-service notification regardless of that flag**, so the flag alone can't hold it. Three layers now: `setOngoing` (hides the swipe affordance), `FLAG_NO_CLEAR` (survives "Clear all"), and a `setDeleteIntent` that fires `ACTION_REASSERT_NOTIFICATION` back into AlarmService, which re-posts it whenever the alarm stack is still non-empty. Snooze / dismiss / open-calendar cancel it programmatically, which does **not** fire the delete intent, so resolving still clears it.
- [x] F.8 App renamed "Calendar Snoozer" (`strings.xml`); the manifest now points `android:label` at `@string/app_name` instead of repeating the literal.
- [x] F.9 "Test Vibration" button under the Delay Between Patterns slider, in the same tonal style as the Test Alarm buttons. It plays exactly **one** pattern (a copy of the live slider values with `vibrationRepetitions = 1`), so it previews the buzz shape without the full repeat count.
- [x] UI.12 Top bar — "Calendar Snoozer (IYSnoozeYK)", filled with Granite #395B50 in light and Blue Slate #5A7684 in dark, white title with a lighter tint on the suffix. New `LocalAppBarColors` carries the trio so the status bar matches without re-deriving it; status-bar icons are now always light because both grounds are dark.
- [x] UI.12 Test-on-lock-screen button takes the same alarm glyph as the button above it, followed by "+5".
- [x] UI.12 Snoozed Alarms — rule under the heading as well as between entries.
- [x] UI.12 Force Stop — moved below the Test Alarm section, half width, 52dp tall (80% of 64), left aligned, and pinned to the **light** error colours in both schemes so the panic button never changes appearance. Its glyph is drawn at 16dp (80% of 20) with an X running corner to corner at the border's 2dp stroke.
- [x] UI.12 App icon added at all five densities from the supplied PNGs. The project had **no launcher icon at all** before this — no mipmaps and no `android:icon`, so it was showing the system default.

## Feedback round 7
- [x] UI.13 Force Stop is now an `OutlinedButton`: transparent container so the page background shows through, 1dp error-coloured border, centred with `Modifier.align(Alignment.CenterHorizontally)` (the Home column aligns Start by default). Icon corners rounded to 4dp and the stroke cut from 2dp to 1.5dp (25% thinner).
- **Reversed part of UI.12 on purpose:** that round pinned the button to the *light* error colours in both schemes so it looked identical everywhere. That only worked because the near-black content colour (#410002) sat on a pale pink container. With the fill removed it lands on the dark scheme's #12180E background at ~1.06:1 — invisible. Border and label now take `colorScheme.error`, which adapts (#BA1A1A light at 6.4:1, #FFB4AB dark at 10.6:1). No single fixed colour can clear 4.5:1 against both backgrounds — the maths rules it out.
- The X arms stop where the corner arc begins rather than at the square's geometric corner: on a rounded rect that corner sits outside the outline, so full-length diagonals would poke past it. Offset is `r · (1 − 1/√2)` per axis.
- Verified on the Pixel 5a in **dark** mode (the case the old pinned colour would have broken). Light mode not re-checked this round.

## Feedback round 8
- [x] UI.14 "Test Vibration" centred. `SettingsSection`'s content lambda became `@Composable ColumnScope.() -> Unit` so section children can reach `Modifier.align`.
- [x] UI.14 Launcher icon replaced with the supplied **adaptive** icon: `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`, per-density `ic_launcher_foreground.png` / `ic_launcher_round.png`, and `values/ic_launcher_background.xml` (#FFFFFF). The manifest's `roundIcon` now points at `ic_launcher_round` rather than repeating `ic_launcher`. Legacy PNGs stay as the pre-API-26 fallback.
- **The supplied foreground had to be rescaled to 78.3%.** As delivered the glyph measured 53 × 65.5dp — inside a 66dp *square*, but the launcher masks to a **circle**, and content only survives that if its **diagonal** fits 66dp. The delivered diagonal was 84.3dp, and on the Pixel the calendar outline visibly overflowed the white circle on all four sides. Scaling the artwork (unchanged, just smaller) about the canvas centre brings the diagonal to 66.1dp. Verified on the phone: clean margin all round.
- Verified on the Pixel 5a in light mode: Test Vibration centred, icon correct on the launcher.

## Feedback round 9 — Pixel 10a / Android 17 (API 37)
Verified directly on the device, not an emulator.

**Works unchanged:** app launches and renders, takeover opens on a fired alarm, HOME and BACK both bounce back to the takeover, notification posts with `ONGOING_EVENT|NO_CLEAR|FOREGROUND_SERVICE`, dismiss and snooze both stop the service and clear the notification, snooze registers an `RTC_WAKEUP` exact alarm and persists its record, `USE_FULL_SCREEN_INTENT` is still granted at install for a sideloaded app.

- [x] **B.1 fix — recents no longer had a re-assert hook.** Android 17 does not deliver `onUserLeaveHint()` for the overview/recents route (home still does), so that gesture left the takeover backgrounded. `AlarmActivity` now re-asserts from `onStop()` as well, which fires whichever way it is backgrounded. Guarded by the existing `userActionTaken`/`resolveSent`/`isFinishing` flags plus an `isInteractive` check so it does not fight a deliberate screen-off. **The relaunch itself was never the problem** — logcat shows it succeeding as `BAL_ALLOW_SAW_PERMISSION result code=2` (task moved to front); what could not be settled by adb is whether the launcher's overview surface legitimately sits on top afterwards. Needs a real-gesture check.
- [x] Full-screen intent is now a visible permission row. It is what lets the alarm take over a locked screen and Android 14 made it revocable, but the Permissions card checked four things and not this one — if it were ever off the takeover would silently degrade to a heads-up notification. Links to the settings screen, with a fallback to app notification settings.
- [x] Foreground service declares `mediaPlayback|specialUse` with the subtype property. `mediaPlayback` alone was dishonest: the SILENT and vibrate-only profiles play no media. Android 17 accepted the pair — `dumpsys` shows `types=0x40000002`.
- [x] Edge-to-edge is opted into explicitly and `Theme.kt` no longer sets `statusBarColor`/`navigationBarColor`. Both setters are deprecated and ignored from Android 15 onward for targetSdk 35, so leaning on them would look right on Android 14 and wrong on anything newer. The M3 top app bar paints the status-bar area itself — confirmed correct on Android 17.
- [x] `tearDown()` now leaves the foreground state before cancelling the notification, and the `ACTION_REASSERT_NOTIFICATION` branch stops the service instead of leaving it started without a foreground notification (itself a violation on Android 14+).

## Feedback round 10 — verified on the Pixel 10a / Android 17
- [x] F.10 Sound settings, all three sound modes. "Choose sound" → **"Change Sound"**. Above the URI line, the ringtone's own name (e.g. "Castle") resolved via `RingtoneManager.getRingtone(...).getTitle(...)`, at `bodyLarge` to match the "Enable sound alarm" label. Below the button: **Alarm Volume** slider (1–100%, with a live readout), **Alarm Fade In** (seconds) and **Alarm Stops After** (seconds).
- Volume genuinely **overrides** the phone: `AudioManager.setStreamVolume(STREAM_ALARM, …)` rather than just attenuating below whatever the phone is set to, because `MediaPlayer.setVolume` only scales *within* the current stream volume. The previous level is captured once and restored from `stopSoundAndVibration()`, which every stop path runs, so the override can never outlive the alarm.
- Fade-in ramps `MediaPlayer.setVolume` in 10 steps/second from silence. "Stops after" silences only the audio — the takeover stays up and vibration continues.
- [x] F.11 "Test Vibration" moved above **Delay Between Patterns**, so it sits directly under the three sliders that shape a single pattern and above the two that only control repetition. Verified order on device: Buzz-On → Buzz-Off → Number of Buzzes → Test Vibration → Delay Between Patterns → Number of Pattern Repetitions.
- [x] F.12 "OPEN CALENDAR EVENT / (DISMISSES ALARM)" added to the Manage sheet below Cancel snooze, same height and Lavender colours as the takeover's. It cancels the snooze as the label says. The event lookup moved out of `AlarmActivity` into a new `util/CalendarLauncher.kt` so both callers share one implementation — traps 3 and 4 are exactly the kind that get reintroduced by a second copy.
- [x] UI.15 Takeover content shifted down one row (spacer 80dp → 124dp = one `headlineMedium` line of 36sp plus the 8dp gap). Measured on device at 420dpi: the title top is now 389px, and the date/time row previously sat at 388.5px — so the title starts precisely where the time used to.
- versionCode 3 / versionName 1.2. `build.gradle.kts` had drifted back to versionCode 1 while the phone carried 2, which blocked the install with `INSTALL_FAILED_VERSION_DOWNGRADE`.

## Feedback round 11
- [x] UI.16 Sound & Vibration collapses what does not apply yet, in all three sound modes. `AnimatedVisibility` hides the sound controls until "Enable sound alarm" is on, the five vibration sliders until "Enable vibration" is on, and the auto-snooze timings until "Auto-Snooze ON" is on. Sequencing collapses unless **both** sound and vibration are enabled — ordering only means something when there are two things to order. (The request said Sequencing's condition was "both", and the auto-snooze bullet read "expand if the Sound is enabled", which is a slip for auto-snooze; implemented on the auto-snooze switch.)
- [x] F.13 The Manage sheet's "Open Calendar Event" no longer cancels the snooze — it is purely a shortcut to the event, and the alarm stays in the list and still fires. The "(DISMISSES ALARM)" second line went with it, since it would now be false. This reverses the F.12 behaviour from round 10; the takeover's own button is a separate path and was not affected.
- [x] F.14 The takeover's "Open Calendar Event" button is replaced by an unchecked-by-default checkbox, **"Open Calendar Event After"**. Ticking it makes the *next* action — snooze or dismiss — also open the event; unticked, nothing extra happens. It no longer resolves the alarm on its own, so opening the event and snoozing are no longer mutually exclusive. `onSnooze`/`onDismiss` carry the flag so every exit honours it (four presets, Specify Time, Time & Date, Dismiss). The checkbox resets when a different alarm takes over the screen (B.6 stack). Auto-dismiss is unaffected — no user action, no follow-up.
- versionCode 4 / versionName 1.3.
- The checkbox sits where the old button did, below Dismiss. It has to be set *before* the action it modifies, which is above it — worth watching in use.

## Feedback round 12 — verified on the Pixel 10a / Android 17
- [x] UI.17 The takeover's checkbox row now carries the "Fire test alarm now" colours, reads **"Also Open Calendar Event"**, and ends with the calendar-and-Zs mark tinted to the label colour; checkbox, text and icon are centred as a group. The supplied SVG became `res/drawable/ic_calendar_snooze.xml` — every stroke and fill stays black on purpose because it is always drawn through `Icon(tint = …)`. The colours are taken from the **light** scheme constants rather than the live scheme: the takeover keeps a fixed palette whatever the system theme is doing, so it must not flip with it.
- [x] UI.18.1 First-install presets. Only `secondStartDelaySeconds` actually changed (0 → 5, now `DEFAULT_SECOND_START_DELAY_SECONDS`); the rest already matched. SOUND_ON = sound + vibration + auto-snooze, sound first, 5s. VIBRATE = vibration + auto-snooze. SILENT = auto-snooze only. Every mode carries "sound first, 5s" so Sequencing already reads that whenever it becomes relevant. **Defaults apply to fresh installs only** — existing profiles keep their saved values, so this was verified by code inspection rather than by wiping the phone.
- [x] UI.18.2 Collapsed Sequencing shows "opens when Sound + Vibration are ON" beside its heading, in the same `bodySmall` / `onSurfaceVariant` as the delay field's label. `SettingsSection` gained an optional `hint`.
- [x] UI.18.3 Section headings are 25% larger (titleMedium scaled ×1.25, i.e. 16sp → 20sp) and the last one is now "Auto-Snooze / Auto-Dismiss".
- [x] UI.19 "CANCEL SNOOZE" in the Manage sheet.
- versionCode 5 / versionName 1.4.

## Feedback round 13
- [x] UI.20 Auto-Snooze / Auto-Dismiss is a segmented switch rather than an on/off toggle, matching Sequencing. Auto-Snooze shows the three existing fields; Auto-Dismiss shows one, "Trigger Auto-Dismiss after (seconds)". Both branches write the same `autoDismissSeconds`, so the value carries across when the user flips between them. Presets are now 60 / 10 / 2 in **all three** modes (SILENT was 30 / 5 / 2).
- [x] UI.21 "Sound Delay (seconds)" added above the fade field; the other two labels now read "Sound Fade In" and "Sound Stops After" rather than "Alarm …".
- [x] UI.22 "Vibration Delay" and "Vibration Stops After" close the Vibration section, mirroring the sound timings.
- [x] UI.23 Section headings sit on their own tonal band — `surfaceContainerHigh` against the card's `surfaceContainerLow`, which is the M3 way to lift a header off its container rather than a divider or an arbitrary tint.
- [x] F.15 A **Missed Alarms** section under Snoozed Alarms, same shape, listing the *event's* day and time. Alarms are filed there on auto-dismiss, on running out of auto-snooze retries, and on Force Stop (every alarm still on the stack). New `MissedAlarmRecord` — deliberately a separate type from `SnoozedAlarmRecord`, because a missed alarm has no future firing time and carrying a `scheduledTimeMs` would invite code to treat it as still armed. `saveMissedAlarm` clears any snooze record, so an alarm appears in exactly one list. Manage reuses the same sheet via an `isMissed` flag: re-scheduling promotes it back to Snoozed, and the red button becomes "REMOVE FROM LIST".
- [x] F.16 "Shhhh" on the takeover — a third-width outlined button in the screen's own background colour that silences sound and vibration without resolving anything. It deliberately does **not** clear the handler queue, or the auto-snooze countdown would die with the sound; a `silenced` flag makes any pending delayed start a no-op instead. Reset whenever a new alarm is presented.
- **Sequencing and the new per-output delays stack.** Sequencing sets which output leads and how long the other waits; UI.21/UI.22 add a further per-output offset. Both default to 0, so untouched profiles behave exactly as before. Worth watching — two controls now influence the same timing.
- versionCode 6 / versionName 1.5.

## Feedback round 14
- [x] B.7 The takeover is no longer what you return to. `AlarmActivity` is `singleInstance`, so it owns a task of its own — and that task outlived the alarm: `finish()` left an empty task record in recents whose base intent was the alarm, so re-entering the app from the overview replayed it and the takeover reopened for an alarm that had already been snoozed, dismissed or opened in Calendar. Fixed at both ends: `excludeFromRecents` + `autoRemoveFromRecents` in the manifest keep the takeover's task out of the overview and drop it when it finishes, and every close path now calls `finishAndRemoveTask()` instead of `finish()`. The only CalendarSnoozer entry left in recents is MainActivity, so coming back lands on Home. **Nothing about B.1 changes** — while an alarm is still ringing the re-assert from `onUserLeaveHint`/`onStop` is unaffected; this only governs what survives *after* the alarm is resolved.
- [x] UI.23 The heading band now runs to the card's own edges. It was inset inside the card's padding with all four corners rounded; the band is now the first child of the card with no padding above or beside it, so the card's own clip rounds its top two corners and its bottom edge is a straight line from side to side. The section body carries the padding the card used to.
- [x] F.16 "Shhhh" centred horizontally.
- [x] UI.24 Force Stop moved inside the Test Alarm card, and Test Alarm collapses like Permissions. Both are deliberate, occasional actions, so they now share one collapsed block. The collapsing header/divider/chevron was duplicated at that point, so it became one `CollapsibleCard`; Permissions passes its ✓ / ⚠ symbol in through a `headerExtra` slot.
- versionCode 7 / versionName 1.6.

## Feedback round 15 — verified on the Pixel 10a / Android 17
- [x] UI.25.1 The "Overrides the phone's alarm volume…" caption moved above the slider, between it and the "Alarm Volume" label — it explains the control, so it reads before it.
- [x] UI.25.2 Sound Delay, Sound Fade In, Sound Stops After, Vibration Delay and Vibration Stops After each sit behind their own switch; the box only exists while the switch is on. The switch state is held **separately** rather than derived from "value > 0", or clearing the box to type a new number would make it vanish mid-edit. Off persists as 0, which every consumer already reads as "not set", and the typed value stays in the box so flicking the switch back restores it.
- [x] UI.25.3 A **Buzz style** menu — Little / Medium / Long / XXL / Custom. The four named ones are a single buzz length alternating on/off *for as long as the alarm lasts*: S/S, M/M, XL/L, XXL/XL. Only "Custom Buzz" shows "Customize your Buzz", which opens the five pattern sliders in a dialog ("Number of Buzzes Pattern" → "Buzzes per Pattern"). Test Vibration moved above the Vibration Delay switch and now tests whichever style is selected — one pattern for custom, three cycles for a preset, since a preset would otherwise never stop.
- A preset is the one case where the vibrator's own `repeat` index is correct: the waveform is `[0, on, off]` looped from the start, and every stop path (Vibration Stops After, auto-dismiss, auto-snooze, any user action, Force Stop) already cancels the vibrator. A custom buzz still unrolls its repetitions into the array — see trap 6.
- **Migration:** a profile saved before UI.25 becomes `CUSTOM`, the only value that preserves what its five sliders were doing. Fresh installs start at `MEDIUM`. Confirmed on the phone: every existing profile came back as Custom Buzz with its slider values intact.
- [x] UI.27.1 A rule between the two alarm lists and the tools below them.
- [x] UI.27.2 Snoozed Alarms and Missed Alarms take the same tonal heading band as the Sound card, and the divider that used to sit under each heading is gone. The band is now one `ui/components/SectionCard.kt` shared by Home and Sound & Vibration rather than two copies.
- [x] UI.27.3 The "all granted" mark is drawn rather than a Material icon: a hollow circle and a check at the same stroke weight, the check's long arm ending exactly **on** the circle at 45° up-right. `CheckCircle` is a solid disc and its outlined variant leaves the check floating inside the ring.
- [x] UI.27.4 Primary tab labels +20%, Sound & Vibration sub-tab labels +15%.
- [x] F.17 The missed-alarm notification had no content intent at all, so tapping it did nothing. It now opens MainActivity on the Home tab. Verified end to end on the phone: with the app backgrounded on the Snooze Buttons tab, tapping the notification brought it forward on Home with the missed alarm listed. The tab switch is driven by a counter rather than a flag, so a second tap works too.
- versionCode 8 / versionName 1.7.

## Feedback round 16 — verified on the Pixel 10a / Android 17, and on the emulator for the fresh-install presets
- [x] UI.25.1 A gap above the "Overrides the phone's alarm volume…" caption, and it now takes the M3 **error** role — the same colour Force Stop uses — because it warns about changing a phone-wide setting rather than describing the control. "Alarm Volume" dropped from `titleSmall` to `bodyLarge`, matching the switch labels below it (both measure 55px on the emulator).
- [x] UI.25.3 A **Test Vibration** button at the bottom of the Customize your Buzz dialog, sharing the section button's state. The dialog's defaults are M / L / 5 / XL / 5. A **Test Alarm Sound** button under the volume slider, same shape as Test Vibration with an alarm glyph; it plays the chosen ringtone once at the chosen volume.
- The sound test overrides the ALARM stream exactly as the real alarm does, because `MediaPlayer.setVolume` only scales within the current stream volume and would preview the wrong thing. That makes restoring mandatory (trap 15): `SoundTest.stop()` is the single exit and every path reaches it — Stop Test, the completion callback, and a `DisposableEffect` for leaving the screen or swiping to another sound mode.
- [x] UI.25.4 Fresh-install presets: volume **50%**, **Vibration first** with a 5-second delay, auto-snooze/auto-dismiss trigger **30s**. Vibrate (sound off / vibration on / auto-snooze) and Silent (sound off / vibration off / auto-snooze) already matched. Verified by uninstalling and reinstalling on the emulator and reading all three profiles back out of `ces_prefs.xml`.
- **These are defaults, so an existing install keeps its saved values** — the same caveat as UI.18.1. The phone will not show them without wiping the sound profiles.
- [x] UI.25.5 The volume slider stops every 10%. The floor is **10%, not 0%**: "Enable sound alarm" is what turns sound off, and a 0% alarm would be inaudible with nothing on screen explaining why — the F.10 constant already said the slider never reaches silence. Values stored before this snap to the nearest stop on load.
- [x] UI.27.4 Both tab rows now scale from one `TAB_LABEL_SCALE` constant, so they are the same size and cannot drift apart.
- [x] UI.28 Back goes to Home from the other two tabs, and closes the app from Home. The `BackHandler` is disabled on Home so the system default applies rather than the app swallowing the gesture. Verified: back from Sound & Vibration landed on Home; back again put the launcher on top.
- [x] F.18 While a test runs its button becomes **Stop Test** — outlined in its own label colour over a transparent background, so it reads as one control in two states. It reverts by itself: the sound from `MediaPlayer`'s completion callback, the vibration from the waveform's measured duration, since the vibrator gives no callback. Verified on the phone for both buttons and in both the section and the dialog, reverting by itself and on Stop.
- **The volume override was measured on the phone, not inferred.** `dumpsys audio` showed `STREAM_ALARM streamVolume` at 4 before the test, **3** during it (the 50% override of a max of 7), and 4 again after Stop — and again after leaving the screen mid-test, which is the `DisposableEffect` path. `media.audio_flinger` reported `Standby: no` while it played and `yes` afterwards, so the sound really started and really stopped.
- versionCode 9 / versionName 1.8.

## Feedback round 17 — verified on the Pixel 10a / Android 17
- [x] UI.25.3 Leaving the Customize your Buzz dialog now ends a running vibration test with it. Verified on the phone via `dumpsys vibrator_manager`: `CurrentVibration: status = running` while the dialog was open, `null` immediately after Done, with `Recent vibrations` showing the 5.5s waveform cut short at 2.58s. Both exits go through the one handler, so tapping outside the dialog behaves the same as Done.
- [x] UI.29.1 The middle tab is now **Alarm Screen**. The four snooze sub-cards live in a "Snooze Buttons X4" card with the usual heading band, and the "60 = 1 hour · 1440 = 1 day · 10080 = 1 week" key moved inside it, under the fields it explains. Sub-cards are shorter: fields −15% (56 → 47.6dp) and the gaps either side of the rule −40% (8 → 4.8dp, 16 → 9.6dp).
- **The 15% needed a different text field.** `Modifier.height()` on an `OutlinedTextField` does not shrink it, it clips the value — caught on the phone, where every preset showed a sliced-off "1 min". The field is now a `BasicTextField` driving `OutlinedTextFieldDefaults.DecorationBox`, with the height taken out of the content padding. See trap 18.
- [x] UI.29.2 An **Alarm Screen Styles** card with Dark Mode and Light Mode side by side, each showing its name, a rule, and a miniature of the takeover below it. Dark is the default; the chosen one takes a 3dp primary border against the other's 1dp outline, and its name turns primary too.
- The miniature is **drawn from the palette rather than bundled as a screenshot**: a captured image would go stale the next time the takeover changes and would have to be re-shot for every style added later.
- **The styles are real, not just a picker.** A control that stores a preference and changes nothing would be worse than no control, so the takeover's 13 fixed `Alarm*` colours became an `AlarmPalette` provided through `LocalAlarmPalette`, and `AlarmActivity` supplies the chosen one. Verified both ways on the phone by firing a test alarm: light renders light, dark is pixel-for-pixel what it was.
- **Two defects the phone caught, both fixed.** The auto-dismiss countdown was painted with the `danger` *container* colour, which is pale pink — legible on the dark ground, all but invisible on the light one; it now has its own `dangerText` role. And routing the "Also Open Calendar Event" row through the palette had quietly restyled it away from the colours UI.17 asked for, so `calendar` is pinned to the same value in both palettes.
- versionCode 10 / versionName 1.9.

## Feedback round 18 — verified on the Pixel 10a / Android 17
- [x] **Bottom navigation.** The top tab row became a `NavigationBar` with four icon-and-label destinations: Home, Alarm Screen, Sound & Vibration, Settings. The pager still backs it, so swiping between destinations works exactly as before, and UI.28's back behaviour is unchanged (verified: back from Sound & Vibration → Home, back again closes the app).
- [x] **Settings.** A new fourth destination. Permissions moved there at the top, followed by "Silent Hours/Days [PENDING]" and "Ignore These Calendars [PENDING]" as headed but empty cards.
- [x] The badge is a **count of what is outstanding**, in the error colour, on the Settings icon *and* beside the Permissions header; once everything is granted both become the check circle and the nav badge disappears. Verified both states on the emulator by granting the missing three and reopening.
- Both read from one `readPermissions()` / `PermissionsStatus`, so the badge and the card cannot disagree about the count.
- [x] **The test tools became a floating cluster.** A "Test" bubble sits bottom-right above the navigation bar; tapping it opens Test Alarm, Test Alarm +5 and Force Stop upwards in that order, right-aligned, each with its icon on the right. Force Stop keeps the M3 error roles and sits furthest from the thumb.
- **Home lost its Test Alarm card and its Permissions card**, which is what those two changes imply — leaving either behind would have meant two ways to do the same thing. Home is now purely the two alarm lists.
- The cluster lives in the `Scaffold`, so it floats over **every** destination rather than only Home. Force Stop in particular is worth reaching without navigating first; say the word if it should be Home-only.
- Firing an alarm from the cluster was verified end to end on the emulator, including the overlay-permission guard that moved with the buttons.
- [x] **UI.28 fix found on the phone.** Pressing back twice quickly from a far destination ate the second press: the scroll to Home had not settled, so `currentPage` was still non-zero and the handler was still armed. It now gates on `pagerState.targetPage`, which is 0 the moment that scroll starts. Two rapid backs from Settings now reach Home and then close the app; a single back still lands on Home rather than closing.
- The phone dropped off USB mid-round, so this was verified on the emulator first and re-verified on the Pixel afterwards — which is how the back-press defect surfaced, since it needs the four-destination distance to be reproducible.
- versionCode 11 / versionName 2.0.

## Feedback round 19 — verified on the Pixel 10a / Android 17
- [x] UI.30 Permissions now has the same shape as the two cards under it: a heading band reading "Permissions" with the status icon beside it, and a body whose first row says either "All permissions are granted" or "N permissions pending" — the pending wording in the error colour — with the expand/collapse chevron at the right. The five rows appear under a rule when expanded. `CollapsibleCard` had no callers left afterwards and was deleted rather than kept as dead code.
- [x] UI.31 Expanding the Test cluster now dims the whole screen behind it, bars included, and a tap anywhere on the dimmed area closes it — the same behaviour as the Customize your Buzz dialog. The scrim is `colorScheme.scrim` at 32%, M3's modal value, and sits above the entire Scaffold but below the cluster, so only the four buttons stay lit. `indication = null` stops a tap flashing a ripple across the whole screen.
- The cluster had to leave the Scaffold's `floatingActionButton` slot to sit above the scrim, so it now positions itself — but against the bottom padding the Scaffold reports rather than an assumed bar height, because the bar grows when "Sound & Vibration" wraps to two lines.
- [x] UI.32 "[PENDING]" became "[Coming soon]" at 75% of the heading size, carried by a new `titleSuffix` on `SectionCard` rather than baked into the title string.
- Verified on the phone: both card shapes, the scrim dimming everything but the buttons, a tap on the scrim closing it, and a cluster action still firing through it. The "N permissions pending" wording was checked on the emulator, since seeing it on the phone would have meant revoking one of its permissions.
- versionCode 12 / versionName 2.1.

## Round 20 — portability beyond Pixels
Prompted by wanting to share the APK with other people, on phones that are not Pixels.
- [x] **Release signing restored.** `assembleRelease` had been producing `app-release-UNSIGNED.apk` — uninstallable — while still reporting BUILD SUCCESSFUL. The `signingConfigs`/`buildTypes` block had gone missing from `build.gradle.kts`, the same drift that once reset versionCode to 1. Verified with `apksigner`: signs under `CN=Calendar Snoozer`.
- [x] **The watched-calendar list is no longer three hardcoded packages.** `CalendarNotificationListener` ignored anything that wasn't Google, AOSP or Samsung Calendar, so on a Xiaomi, Huawei, Oppo or vivo the app installed, reported every permission granted, and then did nothing at all. New `util/CalendarApps.kt` is the single registry, covering the OEM calendars, and `AppPrefs` now defaults to whichever of them is actually installed.
- [x] **A Calendar Apps picker in Settings.** `saveCalendarPackages` had existed since the beginning with no UI calling it, so a user whose calendar wasn't recognised had no way to fix it. The card lists every known calendar app that is installed plus anything the system reports as one, warns when the selection is empty, and keeps a selected-but-uninstalled package visible rather than silently dropping it.
- **A bug of my own, caught on the phone.** The first cut defaulted to "every known calendar app that is installed", which included **Gmail** — every arriving email would have fired a full-screen alarm. The registry now separates `DEDICATED` calendars (default on) from `MIXED_USE` apps (offered, never default, flagged in red as "Also posts non-calendar notifications"). See trap 19.
- [x] **The notification listener asks to be rebound.** `onListenerDisconnected` → `requestRebind`. Android unbinds listeners under memory pressure and aggressive OEMs do it routinely; without this the service stays dead until reboot or a manual permission toggle, with no symptom other than nothing happening.
- [x] **"Open Calendar Event" covers the OEM calendars too**, from the same registry — but only the `DEDICATED` ones, since handing an event URI to Gmail achieves nothing. Trap 4 still holds: only listed packages are ever targeted.
- [x] **Optional permission row: "Unrestricted background battery usage (OPTIONAL)".** Excluded from `pendingCount`, so leaving it off never shows a red badge or contradicts "All permissions are granted" — verified in both directions on the emulator by whitelisting via `dumpsys deviceidle`.
- **OEM skins cannot be emulated.** The Android SDK ships AOSP/Google API images only; there are no Samsung, Xiaomi, Oppo, vivo, OnePlus or Huawei system images. What was tested is Android 14 (emulator, fresh data) and Android 17 (the Pixel), plus the *behaviours* those OEMs provoke — empty/partial calendar selection, an unrecognised calendar app, and battery-optimisation state. The Android 16 AVD still will not leave `offline` on this machine, exactly as recorded in round 9.
- versionCode 13 / versionName 2.2.

## Round 21 — the app explains itself
- [x] **Master switch on Home.** A segmented control matching Sequencing's — "Snoozer is ON" / "Turn OFF Snoozer". Off stops notifications being intercepted; it deliberately does **not** cancel alarms already snoozed, which were scheduled on purpose. The OFF half takes the error roles (container, content, border) rather than the usual selected green, with the red explanation underneath.
- [x] **"Last reminder seen: <app> · <time>"** on Home, recorded by the listener before anything else can fail. Until now nothing on screen answered "is this working?" until an event happened to be due.
- [x] **A warning on Home when no calendar app is selected**, because the fix for that is on a different tab.
- [x] **Settings leads with Calendar Apps**, the setting most likely to be wrong on a non-Pixel and the one whose being wrong makes everything else pointless.
- [x] **Enabling a mixed-use app is confirmed**, spelling out that every notification it posts — mail included — will trigger the alarm at any hour.
- [x] **The Permissions summary tells the truth about the optional one**: "All required permissions are granted. / Optional permission is not." when the battery exemption is off, and "All permissions are granted." only when everything including it is on.
- [x] **Gmail and K-9 Mail removed from the picker.** Gmail posts mail; reminders for "events from Gmail" are posted by Google Calendar. Listing it invited a dangerous toggle for no benefit — see the round 20 note on how it nearly shipped as a default.
- versionCode 14 / versionName 2.3.

## Round 22 — verified on the Pixel 10a / Android 17
- [x] **Silent Hours/Days is real.** Day chips (Mon-first) plus a from/to window, stored through a nullable JSON mirror for the same reason `SoundProfile` is — a field added later must migrate to a default rather than becoming an empty set, which here would mean "silent all day". The listener checks it after the master switch.
- **Two limits stated in the card rather than left to be discovered.** It suppresses *interception* only: alarms already snoozed still fire, because those were scheduled deliberately. And a window whose end precedes its start wraps past midnight, matched against the day it **started** — a Friday 22:00–07:00 window covers Saturday 01:00, which is what "Friday night" means.
- Selecting no days is called out in red, because "never silent" would otherwise look identical to the feature being broken.
- [x] **The Home master switch left its card**, heading and all — it is the page's own state, not one section among several — and the text beneath it is centred.
- [x] **"Ignore These Calendars" deleted.** `PendingCard` went with it, having no callers left.
- Verified on the phone: the ON/OFF styling, the Outlook confirmation (cancel leaves it off), the new Permissions wording in **both** branches, the day chips, the clock dialog in the phone's own 12-hour format, and the summary line. Silent Hours was switched back off and all seven days restored afterwards, so nothing was left suppressing real reminders.
- Also confirmed live: "Last reminder seen: Calendar · Today at 6:29 PM" — a genuine calendar reminder, not a test alarm.
- versionCode 15 / versionName 2.4.

**Correction (round 9):** an "orphaned notification after dismiss" was reported mid-session and was a **measurement error** — `dumpsys notification` includes an archive of past notifications and the grep matched that. `cmd notification list` showed no live notification. `FLAG_NO_CLEAR` was briefly removed chasing this and has been restored. See the CLAUDE.md testing notes.

## Round 9 — forward compatibility + distribution
Prompted by moving from a Pixel 5a (Android 14) to a Pixel 10a (Android 17).
- [x] **Full-screen intent is now a checked permission.** Android 14 made `USE_FULL_SCREEN_INTENT` revocable per app, and it is what lets the alarm take over a locked screen. The Permissions card checked four things and not this one, so if it were ever off the takeover would silently degrade to a heads-up notification. Now surfaced with a link to `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, falling back to app notification settings. Verified on API 34: revoking it via appops flips the summary from ✓ to ⚠.
- [x] **Foreground service type made honest** — `mediaPlayback|specialUse` plus the subtype property Android 14+ requires. `mediaPlayback` alone was false for the silent and vibrate-only profiles, where no media plays.
- [x] **Edge-to-edge opted into explicitly**, and `statusBarColor` / `navigationBarColor` removed from `Theme.kt`. Both are deprecated and **ignored from Android 15 onward** for targetSdk 35, so relying on them looked right on Android 14 and would drift on anything newer. The M3 top app bar paints the status-bar area itself.
- [x] **Release signing added.** There was no `buildTypes`/`signingConfigs` block at all, so `assembleRelease` produced an unsigned, uninstallable APK — the only shareable artifact was the debug build. Credentials load from gitignored `keystore.properties`; shrinking stays off because Gson resolves model fields reflectively. versionCode 1 → 2.
- **Not verified: Android 16/17 behaviour.** The API 36.1 emulator image would not boot on this machine under either swiftshader or swangle (two attempts, ~40 min, never left `offline`). The takeover's escape-prevention on a newer OS remains untested — it needs a real device or a hardware-GPU emulator run from Android Studio.
- **A release-signed APK cannot install over the debug build** (different signing key). Upgrading the Pixel 5a means uninstalling first, which wipes saved presets and sound profiles.
