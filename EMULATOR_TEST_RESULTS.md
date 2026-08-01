# Emulator Smoke Test Results — Calendar Event Snooze

**Date:** 2026-07-03
**Build:** `app/build/outputs/apk/debug/app-debug.apk` (package `com.calendareventsnooze`, versionName 1.0)
**Gradle:** `./gradlew clean assembleDebug` → **BUILD SUCCESSFUL, zero errors, zero warnings**

## Test environment
| Item | Value |
|---|---|
| Emulator AVD | `ces_test` (Pixel 6 profile) |
| System image | `system-images;android-34;google_apis;x86_64` (API 34) |
| Emulator | Android Emulator 36.4.10, headless (`-no-window -gpu swiftshader_indirect`) |
| JDK | OpenJDK 21 (Android Studio JBR) |
| SDK platform used to build | android-35 (auto-installed by AGP) |

> Note: The spec targets API 34/35; the emulator runs **API 34** (Google APIs). This is
> the closest image installed and is fully valid for the smoke test (min SDK 26, target 35).

Permissions were granted via ADB per Part 4 Step 4 (notification listener, overlay,
exact-alarm, READ_CALENDAR, POST_NOTIFICATIONS) — all succeeded.

---

## The 12 automated checks

| # | Check | Status | Evidence |
|---|---|---|---|
| 1 | App launches | **PASS** | `am start .ui.MainActivity` → `topResumedActivity=MainActivity`, no crash |
| 2 | No startup crash | **PASS** | `logcat` grep `FATAL EXCEPTION` = **0** across all runs |
| 3 | All 4 tabs render | **PASS** | UI dump + screenshot show `Setup`, `Snooze Buttons`, `Sound & Vibration`, `Snoozed Alarms`; TabRow sits below the status bar (statusBars inset padding works — pitfall #8) |
| 4 | Test alarm fires | **PASS** | Tapping **FIRE TEST ALARM NOW** → `AlarmActivity` becomes top; `AlarmService isForeground=true, foregroundId=1001, type=mediaPlayback` |
| 5 | Alarm screen buttons present | **PASS** | Screenshot shows OPEN CALENDAR EVENT (blue), 10 min / 30 min / 1 hour / 1 day presets, ⏱ Specify Time, 📅 Time & Date (gold), ✕ DISMISS (red), plus red "Auto-dismiss in Ns" countdown ticking |
| 6 | Snooze persists | **PASS** | Manual "10 min" tap → `snoozed_alarms` record written with `scheduledTimeMs ≈ now+600000ms` and **no** `auto_snooze_count` entry (confirms manual `performSnooze` path). Auto-snooze path also observed writing records with `auto_snooze_count=1`. |
| 7 | Snooze survives restart | **PASS** | After `am force-stop` + relaunch, Snoozed Alarms tab lists all 3 records (SharedPreferences persisted) |
| 8 | Settings persist | **PASS** | Changed Button 1 minutes 10→25, saved, force-stopped, relaunched → field reloads **25**; `snooze_presets` JSON shows `"minutes":25` |
| 9 | Auto-dismiss maxRetries=0 | **PASS** | Set SOUND_ON profile `autoDismissSeconds=3, autoDismissMaxRetries=0` (action still SNOOZE). Fired alarm → auto-dismissed: **Missed Calendar Alarm** notification (channel `ces_missed`) posted, **NO** new record added to `snoozed_alarms`, service stopped, AlarmActivity `CLOSE` transition in logcat. Confirms the `maxRetries==0` short-circuit before snooze logic (pitfall #9). |
| 10 | Return notification | **PASS** | Fired alarm, pressed HOME (launcher on top, no user action) → `AlarmService` still `isForeground=true`; persistent notification `⚠ Calendar Alarm Active` (channel `ces_alarm_active`, id 1002, importance HIGH) present in shade |
| 11 | Notification returns to alarm | **PASS** | Launching the notification's target (`AlarmActivity`, REORDER_TO_FRONT) brought `AlarmActivity` back to top ("current task has been brought to the front") |
| 12 | Swipe-from-recents survival | **INCONCLUSIVE (emulator)** — see below | Single controlled recents interaction kept `AlarmService isForeground=true`. A double-swipe that fully removed the app task left the process alive but the FGS + its notifications stopped. Code protections are present and verified (empty `onTaskRemoved`, `START_STICKY`). |

**Result: 11 / 12 PASS, 1 inconclusive on emulator (check 12).**

### Detail on Check 12
`AlarmService` correctly implements both required protections (verified in source and at runtime):
- `onTaskRemoved(rootIntent)` overridden with an **empty body** (does not stop the service).
- `onStartCommand` returns **`START_STICKY`**.

Runtime behaviour on the emulator was mixed: a single recents swipe left the foreground
service running, but a swipe that *fully removed the task* resulted in the foreground
service and its notifications being torn down (the OS process stayed alive). This is
consistent with Android 14's foreground-service-on-task-removal handling and with the
fact that this build does not persist/re-arm the in-flight alarm on a `START_STICKY`
restart (a sticky restart delivers a null intent, which the service ignores by design).
**This behaviour must be confirmed on a physical device** — emulator recents gesture
handling is not representative of real hardware or OEM skins.

---

## Extra behaviours validated in passing
- **LongArray ↔ Gson (pitfall #7):** `vibrationPattern` round-tripped as `[0,500,200,500]` in the saved `sound_profile_SOUND_ON` JSON.
- **TabRow status-bar inset (pitfall #8):** tabs render fully below the status bar.
- **`userActionTaken` flag:** service is stopped after a manual snooze/dismiss (`ACTION_STOP` on `onDestroy`) but **survives** the Home button (no user action) — exactly as specified.
- **Auto-snooze retry counter:** increments (`auto_snooze_count_<id>=1`) only on the auto-snooze path, not on manual snooze.
- **Three independent Sound sub-tabs:** Sound On / Vibrate Mode / Silent each load and save their own profile.
- **Original calendar notification not cancelled:** `CalendarNotificationListener` never calls `cancelNotification()` (code-verified; not exercised at runtime since it needs a real synced calendar notification).

## Evidence
Screenshots saved under `emulator_test_evidence/`:
- `alarm_screen.png` — full alarm screen with all buttons
- `snoozed_alarms_after_restart.png` — persisted snoozed list after restart
- `snooze_buttons.png` — snooze preset editor
- `sound_profile_autodismiss.png` — Sound On profile / AUTO-SNOOZE-DISMISS section

---

## Items requiring PHYSICAL-DEVICE testing
Per Part 4 Step 7 (cannot be validated on an emulator) **plus** check 12:

| Feature | Why the emulator can't confirm it |
|---|---|
| **Swipe-from-recents survival (check 12)** | Emulator recents gestures & FGS task-removal behaviour differ from real hardware/OEM skins |
| Vibration patterns | No physical vibration motor |
| Lock-screen takeover realism | Emulator lock screen differs from real hardware (setShowWhenLocked / turnScreenOn) |
| Ringer-mode physical switch | No hardware mute switch; real ringer routing |
| OEM battery optimization | Emulator is stock AOSP, not Samsung/Xiaomi/OnePlus |
| Real calendar notification interception | Requires Google Calendar app + real synced events triggering `CalendarNotificationListener` |
| Sound over USAGE_ALARM stream | Emulator audio routing is unreliable (`MediaPlayerService: OMX service is not available` seen in logs) |

## Emulator-only test-state note
The SOUND_ON profile on the emulator was left with `autoDismissSeconds=3, maxRetries=0`
from check 9. This only affects the throwaway emulator; the shipped APK is unaffected.
Re-open Sound & Vibration → Sound On to restore desired values before any device demo.
