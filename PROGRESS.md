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
