# Baby Sleep Tracker

Android app for tracking baby sleep times and diaper events. Designed with big, easy-to-tap buttons for parents who are half-asleep.

## Features

- **Sleep tracking** — one-tap start/stop with elapsed time display
- **Diaper logging** — Pee, Poo, or Both buttons
- **Manual entry** — add past events with date/time pickers
- **Statistics** — sleep duration chart and daily summaries (3/7/14/30 day ranges)
- **History** — scrollable list of all entries with swipe-to-delete
- **Import/Export** — plain text file format, easy to read and share
- **Dark mode** — toggle in settings
- **Survives app kill** — active sleep session persists via SharedPreferences

## Data Format

Data is stored as a plain text file (chosen by the user via SAF):

```
SLEEP 2026-03-02 08:15 - 09:30
PEE 2026-03-02 10:45
POO 2026-03-02 11:30
PEEPOO 2026-03-02 12:00
```

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- MVVM (ViewModel + StateFlow)
- Navigation Compose
- Canvas-based charts
- Storage Access Framework (SAF)

## Requirements

- Android 8.0+ (API 26)
- Android SDK 35

## Build

```sh
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## License

Copyright 2026 akocis. All rights reserved.
