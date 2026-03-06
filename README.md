# Baby Sleep Tracker

Android app for tracking baby sleep times and diaper events. Designed with big, easy-to-tap buttons for parents who are half-asleep.

## Features

- **Sleep tracking** — one-tap start/stop with elapsed time display
- **Breastfeeding tracking** — left/right side timers with auto-switch, mutual exclusion with sleep
- **Diaper logging** — Pee, Poo, or Both buttons
- **Activity logging** — Stroll, Bath, and free-text Note events
- **Manual entry** — add or edit past events with date/time pickers and validation
- **Statistics** — sleep, feed, and diaper charts with trend lines (24h/3/7/14/30 day ranges), scrollable charts for large ranges, day vs night sleep breakdown
- **History** — scrollable list of all entries, editable and deletable
- **Dropbox sync** — automatic cloud backup and merge across devices
- **Import/Export** — plain text file format, easy to read and share
- **Dark mode** — toggle in settings
- **Survives app kill** — active sleep/feed session persists via SharedPreferences

## Data Format

Data is stored as a plain text file (chosen by the user via SAF). Each entry has a unique record ID for sync conflict resolution:

```
#a1b2c3d4 SLEEP 2026-03-02 08:15 - 09:30
#b2c3d4e5 FEEDL 2026-03-05 14:30 - 14:45
#c3d4e5f6 FEEDR 2026-03-05 14:46 - 15:10
#d4e5f6a7 PEE 2026-03-02 10:45
#e5f6a7b8 POO 2026-03-02 11:30
#f6a7b8c9 PEEPOO 2026-03-02 12:00
#a7b8c9d0 STROLL 2026-03-02 13:00 - 14:00
#b8c9d0e1 BATH 2026-03-02 18:00
#c9d0e1f2 NOTE 2026-03-02 19:00 First smile!
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
