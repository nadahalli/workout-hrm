# Thumper

A minimal jump rope workout app for Android with BLE heart rate monitoring and mic-based jump counting.

## Features

- **Heart rate monitoring** via any Bluetooth Low Energy HR strap (auto-reconnects to last device)
- **Jump counting** using the phone's microphone to detect rope impacts
- **Workout timer** with 5-second countdown, pause/resume
- **Workout summary** dialog after stopping (duration, avg HR, jumps, jumps per minute)
- **Workout history** stored locally with Room
- **TCX export** of individual or all workouts to Downloads (compatible with Garmin, Strava, etc.)
- **Adjustable sensitivity** for jump detection threshold

## Requirements

- Android 12+ (API 31)
- BLE-capable device (for heart rate)
- Microphone permission (for jump detection)

## Build

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17. Android Studio's bundled JBR works:

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## Architecture

Single-activity Compose app, MVVM pattern.

```
com.tejaswin.thumper/
  MainActivity.kt             Navigation, permissions, keep-screen-on
  viewmodel/
    WorkoutViewModel.kt        State: timer, HR collection, TCX export, summary
  ui/
    WorkoutScreen.kt           Main screen + settings/scan/summary dialogs
    HistoryScreen.kt           Past workouts list + TCX export buttons
  ble/
    HrmBleManager.kt           BLE scan, connect, HR notifications
  audio/
    JumpAnalyzer.kt            Pure amplitude/cooldown logic (no Android deps)
    JumpDetector.kt            Mic recording, delegates to JumpAnalyzer
  data/
    WorkoutDatabase.kt         Room DB (v3, with workout samples)
    WorkoutDao.kt              Queries
    WorkoutEntity.kt           Workout schema
    WorkoutSampleEntity.kt     Per-workout trackpoint samples (HR, jump count)
```

## Testing

```
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest
```

Pure business logic is extracted into Android-free functions/classes for JUnit testing:

- `JumpAnalyzerTest` - amplitude threshold, cooldown, reset
- `HeartRateParserTest` - BLE heart rate byte parsing (8-bit, 16-bit)
- `WorkoutSummaryTest` - avg HR, jump count, jumps-per-minute math
- `TcxExportTest` - TCX XML structure, timestamps, trackpoints

## Tech

Kotlin, Jetpack Compose, Material3, Room, BLE GATT, AudioRecord, MediaStore for TCX export.
