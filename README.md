# Baby Sleep Tracker

Android app for tracking baby sleep times and diaper events. Designed with big, easy-to-tap buttons for parents who are half-asleep.

## Features

- **Sleep tracking** — one-tap start/stop with elapsed time display and awake timer
- **Breastfeeding tracking** — left/right side timers with auto-switch, mutual exclusion with sleep
- **Diaper logging** — Pee, Poo, or Both buttons
- **Activity logging** — Stroll, Bath, and free-text Note events
- **Manual entry** — add or edit past events with date/time pickers and validation, prefilled with current time
- **Bottle feeding** — Donor, Pumped, and Formula tracking with configurable preset volumes
- **White noise** — built-in sound generator (white, pink, brown, gray, blue, violet, rain, storm) with volume, duration, and fade controls; tracked in history
- **High contrast viewer** — built-in high contrast images for newborn visual stimulation with multiple patterns, color schemes (including three-color), and auto-advancement; viewing sessions tracked in history
- **Growth tracking** — weight, height, and head circumference charts with WHO percentile curves, zoom/pan (pinch or double-tap-and-drag one-handed zoom), and percentile display; latest measurements shown on home screen
- **Sleep & feeding alarms** — configurable alerts when baby sleeps too long or hasn't been fed, with custom ringtone selection
- **Calendar** — monthly overview with color-coded activity dots and daily detail view
- **Statistics** — sleep, feed, and diaper charts with trend lines (24h/72h/3/7/14/30 day ranges), hourly breakdown charts, day vs night sleep pie chart, scrollable charts for large ranges
- **Home dashboard** — today's stats, time since last sleep/feed/diaper/bath/measurement, and latest growth measurements with percentiles
- **Configurable day/night hours** — adjustable boundaries for day/night stats and auto theme switching
- **History** — scrollable list of all entries with date jump, editable and deletable with undo
- **Dropbox sync** — automatic cloud backup and merge across devices with immediate sync on changes
- **Import/Export** — plain text file format, easy to read and share
- **Theme** — light, dark, or auto (switches based on configurable day/night hours)
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
#a7b8c9d0 DONOR 2026-03-02 12:30 60ml
#b8c9d0e1 PUMPED 2026-03-02 15:00 90ml
#c9d0e1f2 FORMULA 2026-03-02 18:00 120ml
#d0e1f2a3 STROLL 2026-03-02 13:00 - 14:00
#e1f2a3b4 BATH 2026-03-02 18:00
#f2a3b4c5 NOTE 2026-03-02 19:00 First smile!
#a2b3c4d5 NOISE 2026-03-02 20:00 - 20:30 white
#b3c4d5e6 HC 2026-03-02 09:00 - 09:15 BW,RW
#c4d5e6f7 MEASURE 2026-03-02 10:00 3.500kg 51.0cm 35.0hc
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

Copyright (c) 2026 Adrian Kocis. Licensed under the [MIT License](LICENSE).
