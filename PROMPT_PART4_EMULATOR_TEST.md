# Calendar Event Snooze — Build Specification (Part 4 of 4: Emulator Smoke Test)

## WHEN TO RUN THIS

Run this AFTER all sections in Parts 1–3 are marked DONE in PROGRESS.md and
`./gradlew assembleDebug` succeeds with zero errors. This is a first-pass smoke
test on the Android emulator to catch obvious failures before the user installs
on a physical device.

Add a section "24. EMULATOR SMOKE TEST" to PROGRESS.md and mark it DONE only
after every check below passes or is documented as emulator-limited.

---

## 24. EMULATOR SMOKE TEST

### Step 1 — Detect or create an emulator

```bash
# List available Android Virtual Devices
emulator -list-avds
```

If no AVD exists, create one via command line (adjust system image if needed):
```bash
# List installed system images
sdkmanager --list | grep system-images

# Install a system image if none present (API 34, Google APIs)
sdkmanager "system-images;android-34;google_apis;x86_64"

# Create an AVD named "ces_test"
echo "no" | avdmanager create avd -n ces_test \
  -k "system-images;android-34;google_apis;x86_64" -d pixel_6
```

### Step 2 — Boot the emulator

```bash
emulator -avd ces_test -no-snapshot-load &
# Wait for full boot
adb wait-for-device
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
```

### Step 3 — Install the debug APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 4 — Grant permissions via ADB (emulator can't do the manual grants)

```bash
# Notification listener access
adb shell cmd notification allow_listener \
  com.calendareventsnooze/com.calendareventsnooze.service.CalendarNotificationListener

# Overlay / display over other apps
adb shell appops set com.calendareventsnooze SYSTEM_ALERT_WINDOW allow

# Exact alarms
adb shell appops set com.calendareventsnooze SCHEDULE_EXACT_ALARM allow

# Calendar + notifications runtime permissions
adb shell pm grant com.calendareventsnooze android.permission.READ_CALENDAR
adb shell pm grant com.calendareventsnooze android.permission.POST_NOTIFICATIONS
```

### Step 5 — Automated checks (each must pass)

| # | Check | Command / method | Pass criteria |
|---|---|---|---|
| 1 | App launches | `adb shell am start -n com.calendareventsnooze/.ui.MainActivity` | No crash in logcat; MainActivity visible |
| 2 | No startup crash | `adb logcat -d \| grep -i "FATAL\|AndroidRuntime"` | Zero fatal exceptions |
| 3 | All 4 tabs render | Launch each tab via UI automation or screenshot | Setup, Snooze Buttons, Sound & Vibration, Snoozed Alarms all visible |
| 4 | Test alarm fires | Tap "FIRE TEST ALARM NOW" (or `am start` the AlarmActivity with test extras) | AlarmActivity appears full-screen |
| 5 | Alarm screen buttons present | Screenshot AlarmActivity | 4 presets + Specify Time + Time & Date + Dismiss + Open Calendar all visible |
| 6 | Snooze persists | Tap a preset, then open Snoozed Alarms tab | The snoozed alarm appears in the list |
| 7 | Snooze survives restart | `adb shell am force-stop`, relaunch, open Snoozed Alarms | Snoozed alarm still listed (SharedPreferences persisted) |
| 8 | Settings persist | Change a snooze preset, force-stop, relaunch | Changed value retained |
| 9 | Auto-dismiss maxRetries=0 | Set a SILENT profile with maxRetries=0, fire test alarm on silent mode | Alarm auto-dismisses immediately, does NOT appear in Snoozed Alarms |
| 10 | Return notification | Fire alarm, press Home (`adb shell input keyevent KEYCODE_HOME`), check shade | Persistent "⚠ Calendar Alarm Active" notification present |
| 11 | Notification returns to alarm | `adb shell am start` the notification's PendingIntent target | AlarmActivity comes back to foreground |
| 12 | Swipe-from-recents survival | Fire alarm, `adb shell input keyevent KEYCODE_APP_SWITCH`, dismiss task | Alarm keeps running (service alive in `adb shell dumpsys activity services`) |

### Step 6 — Capture evidence

```bash
# Screenshot each key screen
adb exec-out screencap -p > /tmp/ces_main.png
adb exec-out screencap -p > /tmp/ces_alarm.png

# Dump running services to confirm AlarmService lifecycle
adb shell dumpsys activity services com.calendareventsnooze
```

### Step 7 — Document emulator limitations

The following CANNOT be validated on an emulator and MUST be flagged for the
user to test on a physical device:

| Feature | Why emulator can't verify |
|---|---|
| Vibration patterns | No physical vibration motor |
| Lock-screen takeover realism | Emulator lock screen differs from real hardware |
| Ringer mode physical switch | Hardware mute switch / real ringer behavior |
| OEM battery optimization | Emulator runs stock AOSP, not Samsung/Xiaomi/OnePlus skins |
| Real calendar notifications | Requires Google Calendar app + real synced events |
| Sound over USAGE_ALARM stream | Emulator audio routing is unreliable |

### Step 8 — Report

After running, write a file `EMULATOR_TEST_RESULTS.md` in the project root with:
- Each of the 12 checks and PASS / FAIL / EMULATOR-LIMITED status
- Any FAIL items with the logcat excerpt showing the error
- A clear list of what still needs physical-device testing

Do NOT attempt to fix physical-device-only features based on emulator behavior —
they are expected to be inconclusive on the emulator.
