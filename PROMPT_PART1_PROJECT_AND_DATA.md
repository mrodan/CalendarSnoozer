# Calendar Event Snooze — Build Specification (Part 1 of 3)

## CONTINUITY INSTRUCTIONS — READ FIRST

**If starting fresh:**
1. Read Part 1 (this file), Part 2, and Part 3 fully before writing any code.
2. Create PROGRESS.md in the project root listing every numbered section
   (1 through 23) marked as [ ] NOT STARTED.
3. Build the app working through sections in order, marking each [x] DONE.
4. Run `./gradlew assembleDebug` only after all sections are [x] DONE.
5. Fix all build errors before declaring the task complete.

**If resuming after interruption:**
1. Read PROGRESS.md to find the first [ ] NOT STARTED section.
2. Re-read that section and all remaining sections from the spec files.
3. Continue from there. Do not redo [x] DONE sections.

**If context is getting full:**
Update PROGRESS.md to mark everything completed so far, then type /compact.
After compacting, re-read PROGRESS.md and continue from the first NOT STARTED.

**Build rule:**
Do not run `./gradlew assembleDebug` until ALL sections are marked DONE.
Run the build exactly once at the end. Fix errors. Confirm zero errors.

---

# Calendar Event Snooze — Complete Build Specification for Claude Code

## INSTRUCTIONS FOR CLAUDE CODE

Build the complete Android application described in this document from scratch.
This is a greenfield project — do not assume any existing code.

This prompt is written for Claude Opus. Take time to reason through each
component's interactions before writing code. Pay special attention to the
CRITICAL IMPLEMENTATION RULES sections — they document real bugs observed in
previous builds of this app that must be proactively prevented.

**Completion requirement:** Run `./gradlew assembleDebug` after all files are
written. Fix every compiler error and warning. Do not mark the task complete
until the build succeeds with zero errors.

---

## 1. PROJECT OVERVIEW

**App name:** Calendar Event Snooze
**Package:** `com.calendareventsnooze`
**Purpose:** Intercepts Android calendar event notifications and replaces them
with a custom full-screen alarm screen that gives the user rich snooze and
dismiss controls, custom sounds, vibration patterns, and automatic retry logic.

**Min SDK:** 26 (Android 8.0)
**Target SDK:** 35
**Language:** Kotlin
**UI:** Jetpack Compose with Material 3 throughout — no XML layouts anywhere
**Architecture:** Single-activity host (MainActivity) for settings screens,
separate standalone activity (AlarmActivity) for the alarm screen, MVVM with
StateFlow where state management is needed, SharedPreferences + Gson for all
persistence.

---

## 2. BUILD CONFIGURATION

### settings.gradle.kts
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "CalendarEventSnooze"
include(":app")
```

### build.gradle.kts (project level)
```kotlin
plugins {
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

### app/build.gradle.kts
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.calendareventsnooze"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.calendareventsnooze"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("com.google.code.gson:gson:2.10.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

### gradle.properties
```
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

---

## 3. ANDROIDMANIFEST.XML

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.READ_CALENDAR" />
    <uses-permission android:name="android.permission.WRITE_CALENDAR" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".CalendarEventSnoozeApp"
        android:allowBackup="true"
        android:label="Calendar Event Snooze"
        android:theme="@style/Theme.CalendarEventSnooze">

        <!-- Main settings activity -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Full-screen alarm activity — completely separate from MainActivity -->
        <activity
            android:name=".ui.AlarmActivity"
            android:exported="true"
            android:launchMode="singleInstance"
            android:showOnLockScreen="true"
            android:turnScreenOn="true"
            android:theme="@style/Theme.CalendarEventSnooze.Alarm"
            android:windowSoftInputMode="stateAlwaysHidden" />

        <!-- Notification listener -->
        <service
            android:name=".service.CalendarNotificationListener"
            android:exported="true"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

        <!-- Alarm foreground service -->
        <service
            android:name=".service.AlarmService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

        <!-- Snooze alarm receiver -->
        <receiver
            android:name=".receiver.AlarmReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="com.calendareventsnooze.TRIGGER_ALARM" />
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

---

## 4. FILE STRUCTURE

```
app/src/main/
├── java/com/calendareventsnooze/
│   ├── CalendarEventSnoozeApp.kt
│   ├── data/
│   │   └── AppPrefs.kt
│   ├── model/
│   │   ├── AlarmEvent.kt
│   │   ├── SnoozePreset.kt
│   │   ├── SoundProfile.kt
│   │   └── SnoozedAlarmRecord.kt
│   ├── service/
│   │   ├── CalendarNotificationListener.kt
│   │   └── AlarmService.kt
│   ├── receiver/
│   │   └── AlarmReceiver.kt
│   ├── scheduler/
│   │   └── AlarmScheduler.kt
│   ├── util/
│   │   ├── AlarmEventIntentUtils.kt
│   │   ├── TimeFormatter.kt
│   │   └── TestAlarmHelper.kt
│   └── ui/
│       ├── MainActivity.kt
│       ├── AlarmActivity.kt
│       ├── theme/
│       │   ├── Theme.kt
│       │   ├── Color.kt
│       │   └── Type.kt
│       └── screens/
│           ├── AlarmScreen.kt
│           ├── SetupScreen.kt
│           ├── SnoozePresetsScreen.kt
│           ├── SoundProfileScreen.kt
│           └── SnoozedAlarmsScreen.kt
└── res/
    └── values/
        ├── strings.xml
        └── themes.xml
```

---

## 5. DATA MODELS

### AlarmEvent.kt
```kotlin
package com.calendareventsnooze.model

data class AlarmEvent(
    val alarmId: String,
    val eventTitle: String,
    val eventText: String,
    val eventId: Long,       // calendar event ID; -1 if unavailable
    val eventTimeMs: Long    // scheduled start time of the event; -1 if unavailable
)
```

### SnoozePreset.kt
```kotlin
package com.calendareventsnooze.model

data class SnoozePreset(
    val label: String,   // button text shown in UI, e.g. "10 min"
    val minutes: Int     // valid range: 1 to 10080 (1 week)
)
```

### SoundProfile.kt
```kotlin
package com.calendareventsnooze.model

enum class RingerMode { SOUND_ON, VIBRATE, SILENT }
enum class AutoDismissAction { DISMISS, SNOOZE }

data class SoundProfile(
    val ringerMode: RingerMode,
    val soundEnabled: Boolean,
    val soundUri: String?,            // null = use system default alarm sound
    val vibrationEnabled: Boolean,
    val vibrationPattern: LongArray,  // ms: [delay, on, off, on, ...] e.g. [0,500,200,500]
    val vibrationRepeat: Int,         // -1 = no repeat; 0 = repeat from index 0
    val soundStartsFirst: Boolean,
    val secondStartDelaySeconds: Int, // 0 = start both simultaneously
    val autoDismissSeconds: Int,      // 0 = never auto-dismiss
    val autoDismissAction: AutoDismissAction,
    val autoDismissSnoozeMinutes: Int,
    val autoDismissMaxRetries: Int    // 0 = auto-dismiss immediately, no snooze attempt
)
```

### SnoozedAlarmRecord.kt
```kotlin
package com.calendareventsnooze.model

data class SnoozedAlarmRecord(
    val alarmId: String,
    val eventTitle: String,
    val eventText: String,
    val eventId: Long,
    val eventTimeMs: Long,
    val scheduledTimeMs: Long  // Unix timestamp when alarm will next fire
)
```

---

## 6. UTILITY — AlarmEventIntentUtils.kt

Create extension functions for consistent intent packing/unpacking across all
components. Every component that passes AlarmEvent data via an Intent must use
these functions — never write raw putExtra/getLong calls inline.

```kotlin
package com.calendareventsnooze.util

private const val EXTRA_ALARM_ID     = "ces_alarm_id"
private const val EXTRA_EVENT_TITLE  = "ces_event_title"
private const val EXTRA_EVENT_TEXT   = "ces_event_text"
private const val EXTRA_EVENT_ID     = "ces_event_id"
private const val EXTRA_EVENT_TIME   = "ces_event_time"

fun Intent.putAlarmEvent(event: AlarmEvent): Intent {
    putExtra(EXTRA_ALARM_ID, event.alarmId)
    putExtra(EXTRA_EVENT_TITLE, event.eventTitle)
    putExtra(EXTRA_EVENT_TEXT, event.eventText)
    putExtra(EXTRA_EVENT_ID, event.eventId)
    putExtra(EXTRA_EVENT_TIME, event.eventTimeMs)
    return this
}

fun Intent.getAlarmEvent(): AlarmEvent? {
    val alarmId = getStringExtra(EXTRA_ALARM_ID) ?: return null
    return AlarmEvent(
        alarmId = alarmId,
        eventTitle = getStringExtra(EXTRA_EVENT_TITLE) ?: "",
        eventText = getStringExtra(EXTRA_EVENT_TEXT) ?: "",
        eventId = getLongExtra(EXTRA_EVENT_ID, -1L),
        eventTimeMs = getLongExtra(EXTRA_EVENT_TIME, -1L)
    )
}
```

---

## 7. UTILITY — TimeFormatter.kt

```kotlin
package com.calendareventsnooze.util

fun formatEventTime(eventTimeMs: Long): String? {
    if (eventTimeMs <= 0L) return null
    val eventCal = Calendar.getInstance().apply { timeInMillis = eventTimeMs }
    val today    = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

    fun isSameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(eventTimeMs))
    return when {
        isSameDay(eventCal, today)    -> "Today at $timeStr"
        isSameDay(eventCal, tomorrow) -> "Tomorrow at $timeStr"
        else -> SimpleDateFormat("EEE MMM d 'at' h:mm a", Locale.getDefault())
                    .format(Date(eventTimeMs))
    }
}

fun formatScheduledTime(scheduledTimeMs: Long): String =
    formatEventTime(scheduledTimeMs) ?: "Unknown time"

fun nextHourDefaultMs(): Long {
    return Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
```

---

## 8. PERSISTENCE — AppPrefs.kt

Singleton `object`. All reads/writes go through this class.

**CRITICAL: LongArray and Gson**
`LongArray` does not serialize cleanly with Gson. For `SoundProfile`, use
an internal JSON-safe data class that stores `vibrationPattern` as `List<Long>`,
then convert on read/write:
```kotlin
private data class SoundProfileJson(
    // all SoundProfile fields except vibrationPattern is List<Long> here
    val vibrationPattern: List<Long>,
    ...
)
```

**Snooze Presets:**
- SharedPreferences key: `"snooze_presets"`
- Gson serialize as `List<SnoozePreset>`
- Defaults: `[SnoozePreset("10 min", 10), SnoozePreset("30 min", 30),
  SnoozePreset("1 hour", 60), SnoozePreset("1 day", 1440)]`

**Sound Profiles:**
- Key per mode: `"sound_profile_SOUND_ON"`, `"sound_profile_VIBRATE"`,
  `"sound_profile_SILENT"`
- Defaults:

```
SOUND_ON:  soundEnabled=true, vibrationEnabled=true, soundStartsFirst=true,
           secondStartDelaySeconds=0, autoDismissSeconds=60,
           autoDismissAction=SNOOZE, autoDismissSnoozeMinutes=10,
           autoDismissMaxRetries=3, vibrationPattern=[0,500,200,500],
           vibrationRepeat=-1, soundUri=null

VIBRATE:   soundEnabled=false, vibrationEnabled=true,
           vibrationPattern=[0,700,300,700], vibrationRepeat=-1,
           autoDismissSeconds=60, autoDismissAction=SNOOZE,
           autoDismissSnoozeMinutes=10, autoDismissMaxRetries=3,
           soundStartsFirst=true, secondStartDelaySeconds=0, soundUri=null

SILENT:    soundEnabled=false, vibrationEnabled=false,
           autoDismissSeconds=30, autoDismissAction=SNOOZE,
           autoDismissSnoozeMinutes=5, autoDismissMaxRetries=2,
           soundStartsFirst=true, secondStartDelaySeconds=0,
           vibrationPattern=[0,500,200,500], vibrationRepeat=-1, soundUri=null
```

**Snoozed Alarms:**
- Key: `"snoozed_alarms"`
- Store as a JSON map: `Map<String, SnoozedAlarmRecord>` where key = alarmId
- `getAllSnoozedAlarms()` returns values sorted by `scheduledTimeMs` ascending

**Auto-snooze retry counter:**
- Key per alarm: `"auto_snooze_count_<alarmId>"`
- Methods: `getAutoSnoozeCount`, `incrementAutoSnoozeCount`, `resetAutoSnoozeCount`

**Calendar packages:**
- Key: `"calendar_packages"`
- Default: `setOf("com.google.android.calendar", "com.android.calendar",
  "com.samsung.android.calendar")`

---
