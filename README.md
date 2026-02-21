# Thumper

A minimal jump rope workout app for Android with BLE heart rate monitoring and mic-based jump counting.

## Features

- **Heart rate monitoring** via any Bluetooth Low Energy HR strap (auto-reconnects to last device)
- **Jump counting** using the phone's microphone to detect rope impacts
- **Live jumps per minute** displayed during workouts
- **Workout timer** with 5-second countdown, pause/resume
- **Workout summary** dialog after stopping (duration, avg BPM, jumps, JPM)
- **Workout history** stored locally with Room
- **CSV export** of all workouts to Downloads
- **Configurable beep** every N jumps (100, 200, or 400)
- **Adjustable sensitivity** for jump detection threshold

## Screenshots

Dark UI with large BPM and jump count displays. Settings, scan, and summary are modal dialogs.

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
  MainActivity.kt           Navigation, permissions, keep-screen-on
  viewmodel/
    WorkoutViewModel.kt      All state: timer, HR collection, beep, export
  ui/
    WorkoutScreen.kt         Main screen + settings/scan/summary dialogs
    HistoryScreen.kt         Past workouts list + CSV export button
  ble/
    HrmBleManager.kt         BLE scan, connect, HR notifications
  audio/
    JumpDetector.kt          Mic audio processing, amplitude threshold
  data/
    WorkoutDatabase.kt       Room DB (v2, migrated to add jumpCount)
    WorkoutDao.kt            Queries
    WorkoutEntity.kt         Schema
```

## Tech

Kotlin, Jetpack Compose, Material3, Room, BLE GATT, AudioRecord, ToneGenerator, MediaStore for CSV export.
