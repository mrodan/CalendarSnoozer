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
