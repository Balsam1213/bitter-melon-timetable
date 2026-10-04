[简体中文](README.md) | English

# Bitter Melon Timetable (苦瓜课表)

A fully offline Android course-schedule app. Import your timetable, then view and edit courses and class times without any account or network connection, with class reminders beforehand. The optional "academic affairs import" is the only feature that uses the network.

## Features

- **Weekly timetable grid**: shows courses by week, auto-calculates the current week number, supports swiping between weeks, and highlights today
- **Multiple import methods** (everything goes through a preview step before anything is written):
  - Excel (.xlsx) / CSV files (both UTF-8 and GBK encodings)
  - JSON backup files (this app's own export format)
  - Offline OCR of timetable screenshots (ML Kit Chinese text recognition, model bundled in the APK — no network)
  - Manual entry
  - Academic affairs import (needs network): opens your school's academic affairs website in an in-app WebView (defaults to Southwest Jiaotong University, changeable to any school), log in manually, open the "My Course Selection Records" or "This Semester's Timetable" page, and import with one tap. Compatible with http sub-sites and self-signed certificates of campus systems (a confirmation dialog appears on certificate errors); supports pinch-to-zoom; white screens caused by broken CSS height chains on some admin portals are auto-repaired
- **Course management**: one course can have multiple time slots; custom colors, teacher, room, and notes; time-conflict detection on save
- **Class period times**: start/end time of every period is freely editable, with add/remove — adapts to any school's schedule
- **Semester management**: multiple semesters, start date (drives week numbers), total weeks, set current / archive / delete; new semesters can copy the period table from an old one
- **Class reminders**:
  - Global: remind N minutes in advance (0–120) via notification; reminders can be disabled per course
  - Exact alarms via the USE_EXACT_ALARM permission (granted on install, no dialog); automatically rescheduled after reboot / app update
  - Alarms go through the `setAlarmClock` channel: immune to Doze batching and vendor battery saving, so the 8 a.m. reminder fires on time even overnight (a small alarm-clock icon appears in the status bar)
  - Notifications come with a heads-up banner, sound, and vibration (not silent)
  - Holiday skip: no reminders on statutory holidays. The list is pre-seeded with the State Council's 2026 schedule and auto-synced when online (holiday-cn source, follows the official notices day by day, and fills in next year's schedule once published); dates you added or removed yourself are never overwritten by the sync; the list is collapsed by default — view/edit it under Settings → Holidays
- **Home-screen widget**: today's courses, the current one highlighted, tap to open the app
- **Dark mode**: Material 3 follows the system, dynamic color on Android 12+
- **JSON export**: backup / device migration

## Usage

1. First launch: create a semester in "Semester Management" (name, date of Monday of week 1, total weeks), or just go to "Import Timetable" (a semester is created automatically if none exists)
2. Import templates: see [模板/课程表导入模板.csv](模板/课程表导入模板.csv) (CSV template) and [模板/课程表示例.json](模板/课程表示例.json) (sample JSON)
3. Adjust period start/end times in "Period Times" (10-period template by default)
4. Reminder settings live in "Settings"; reminders can be turned off per course in the course editor

## Build

- Requirements: Android Studio (use its bundled JBR as the Gradle JDK), Android SDK
- `./gradlew :app:assembleDebug` produces the debug build (all ABIs, for development); `./gradlew :app:assembleRelease` produces the size-optimized release build (R8 shrinking + resource shrinking + ABI splits under `abi/arm64-v8a/` etc., signed with the debug key so it installs directly)
- `gradle.properties` in this repo enables `android.overridePathCheck=true` (the workspace path contains non-ASCII characters)

## Tech Stack

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · Glance (widget) · ML Kit text recognition (offline model bundled) · Jsoup (academic affairs page parsing)

No account system; all data lives in the on-device database (`timetable.db`). The only network permission, `INTERNET`, is used exclusively by the "Academic Affairs Import" page (in-app WebView) — timetable data itself never leaves the device.

## Known Limitations

- Legacy `.xls` (non-xlsx) is not supported — re-save as `.xlsx` in Office/WPS first
- Screenshot OCR quality depends on image clarity and layout; always review results before importing
- On Chinese ROMs (Xiaomi/Huawei etc.) you may need to manually allow "auto-start" and "background pop-up", otherwise notifications can be delayed
