# TallyVox — Android Counter App Specification

## Overview
TallyVox is a native Android tally counter app built with Kotlin + Jetpack Compose. It features a large-display primary counter, interval-based secondary counter, voice phrase recognition mode, and volume button control.

## Tech Stack
- **Language:** Kotlin 1.9.22
- **UI:** Jetpack Compose with Material3
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34
- **Build:** Gradle 8.5 with Kotlin DSL

## Color Palette

### Dark Theme
- Background: `#111110`
- Surface: `#171614`
- Surface2: `#1d1c1a`
- Primary: `#4f98a3` (teal)
- Primary Hover: `#227f8b`
- Success: `#6daa45` (green)
- Error: `#d163a7` (pink/red)
- Warning: `#bb653b`
- Gold: `#e8af34` (interval accent)
- Text: `#e8e7e3`
- Text Muted: `#888785`

### Light Theme
- Background: `#f7f6f2`
- Surface: `#f9f8f5`
- Surface2: `#fbfbf9`
- Primary: `#01696f` (dark teal)
- Text: `#1a1815`
- Text Muted: `#6b6a66`

## Features Implemented

1. **Large Primary Counter** — 96sp bold tabular numerals, ≥40% screen height, animated digit transitions
2. **Plus/Minus Buttons** — Dominant green Plus (70%), narrower red Minus (30%), 72dp+ tall, haptic feedback
3. **Long-press Rapid Increment** — 600ms initial delay, then 5/sec
4. **300ms Debounce** — Prevents double-tap double-counting
5. **Interval Sub-Counter** — Gold accent, auto-increments every N primary counts (N configurable 1–99999)
6. **Interval Trigger Feedback** — 200ms haptic + visual pulse on sub-counter increment
7. **Decrement Recalculation** — Secondary decrements when crossing interval boundary
8. **Reset Controls** — Reset Primary, Reset Secondary, Reset Both with confirmation dialogs
9. **Voice Mode Toggle** — Manual ↔ Voice pill toggle at top
10. **Voice Recognition** — On-device SpeechRecognizer, continuous listening loop
11. **Volume Button Mapping** — VolUp=+, VolDown=- via dispatchKeyEvent override
12. **Dark/Light Theme** — Follows system setting
13. **Foreground Service** — Persistent notification with current count
14. **SharedPreferences Persistence** — Counter values, interval, mode saved across restarts
15. **Keep Screen On** — FLAG_KEEP_SCREEN_ON in foreground
16. **Haptic Feedback** — Different durations for different events
17. **Interval Setting Dialog** — Number input with validation
18. **Confirmation Dialogs** — For all destructive reset actions

## Permissions
- `RECORD_AUDIO` — Voice mode
- `FOREGROUND_SERVICE` — Background counting
- `FOREGROUND_SERVICE_MICROPHONE` — Voice mode foreground service
- `POST_NOTIFICATIONS` — Android 13+ notification permission
- `WAKE_LOCK` — Keep CPU awake for screen-off counting
- `VIBRATE` — Haptic feedback
