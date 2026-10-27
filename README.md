English | [简体中文](README.zh-CN.md)

# Bitter Melon Timetable (苦瓜课表)

A fully offline Android course-schedule app. Import your timetable, then view and edit courses and class times without any account or network connection, with class reminders beforehand. Only two optional features use the network: the academic affairs import and the holiday schedule sync (see "Network behavior" below); your timetable data never leaves the device.

## Features

- **Weekly timetable grid**: shows courses by week, auto-calculates the current week number, supports swiping between weeks, and highlights today
- **Multiple import methods** (everything goes through a preview step before anything is written; entries with problems are flagged in red with the reason and unchecked by default — fix them in the preview and re-check):
  - Excel (.xlsx) / CSV files (both UTF-8 and GBK encodings)
  - JSON backup files (this app's own export format)
  - Offline OCR of timetable screenshots (ML Kit Chinese text recognition, model bundled in the APK — no network)
  - Manual entry
  - Academic affairs import (needs network): opens your school's academic affairs website in an in-app WebView (defaults to Southwest Jiaotong University, changeable to any school), log in manually, open the "My Course Selection Records" or "This Semester's Timetable" page, and import with one tap. Plaintext http addresses and certificate errors both trigger an explicit risk warning first (credentials could be intercepted) and only proceed after you confirm; navigating to a different site asks too. Compatible with http sub-sites and self-signed certificates of campus systems; supports pinch-to-zoom; white screens caused by broken CSS height chains on some admin portals are auto-repaired
- **Course management**: one course can have multiple time slots; **arbitrary week sets** (e.g. weeks "1-8, 11-16" — odd/even weeks are just quick presets); custom colors, teacher, room, and notes; time-conflict detection on save
- **Timetable sharing**: render the current week to an image, save it to the gallery or share it with friends
- **Display settings**: three text-size levels (stacks with the system font scale), a compact mode that squeezes row height without shrinking text, and four preset app themes (violet / indigo / pine green / rose pink) applied instantly — course colors and share images stay fixed
- **Course search**: quick filtering by name / teacher / room on the course list page
- **Class period times**: start/end time of every period is freely editable, with add/remove — adapts to any school's schedule
- **Semester management**: multiple semesters, start date (drives week numbers), total weeks, set current / archive / delete; new semesters can copy the period table from an old one
- **Class reminders**:
  - Global: remind N minutes in advance (0–120) via notification; reminders can be disabled per course
  - Exact alarms via the USE_EXACT_ALARM permission (granted on install, no dialog); automatically rescheduled after reboot / app update; when the exact-alarm permission is revoked or notifications are turned off system-wide, the Settings page shows a red warning with a one-tap fix
  - Alarms go through the `setAlarmClock` channel: immune to Doze batching and vendor battery saving, so the 8 a.m. reminder fires on time even overnight (a small alarm-clock icon appears in the status bar)
  - Notifications come with a heads-up banner, sound, and vibration (not silent)
  - Holidays & makeup workdays: calendar-based management (Settings → Holidays → calendar). Statutory holidays are marked 休 (red, no reminders that day); makeup workdays are marked 班 and remind according to the weekday you assign (default: the day's own weekday). Pre-seeded with the State Council's 2026 schedule (including 6 makeup workdays) and auto-synced online (holiday-cn source, fills in next year's schedule once published); your manual edits are never overwritten by the sync
- **Home-screen widgets**: "Today" widget — today's courses, the current one highlighted, tap to open the app; shows "今天放假" on statutory holidays and follows the assigned weekday on makeup workdays. "This week" widget (about 4×3) — one column per day with today highlighted, follows the "show weekend" setting
- **Daily auto-backup**: the current semester is exported as JSON to Documents/苦瓜课表 once a day, keeping the latest 7 copies; can be turned off or triggered manually in Settings, which also shows the backup status (success time / red failure warning), entirely offline
- **Dark mode**: Material 3 theme that follows the system dark mode
- **JSON export**: backup / device migration

## Usage

1. First launch: just tap "Import Timetable" — if no semester exists yet, a confirmation dialog appears first (name / start date / total weeks, all editable) before anything is written; you can also create a semester manually under "Semester Management"
2. Import templates: see [模板/课程表导入模板.csv](模板/课程表导入模板.csv) (CSV template) and [模板/课程表示例.json](模板/课程表示例.json) (sample JSON)
3. Adjust period start/end times in "Period Times" (10-period template by default)
4. Reminder settings live in "Settings"; reminders can be turned off per course in the course editor

## Project Docs (Chinese)

- [Devlog & TODO](devlog/) — changelog, backlog, and per-day dev logs (Chinese)
- [docs/](docs/) — requirements, architecture, design specs, and workflow guides

## Build

- Requirements: Android Studio (use its bundled JBR as the Gradle JDK), Android SDK
- `./gradlew :app:assembleDebug` produces the debug build (all ABIs, for development); `./gradlew :app:assembleRelease` produces the size-optimized release build (R8 shrinking + resource shrinking + ABI splits under `abi/arm64-v8a/` etc.)
- Release signing: prefers the formal keystore referenced by `keystore.properties` in the repo root (the file and keystore are not committed — generate your own and back it up); falls back to the debug key when that file is missing (local testing only, not for releases)
- Note: v1.3 and earlier were debug-signed; from v1.4 on the formal key is used, so covering-install requires uninstalling the old version first
- `gradle.properties` in this repo enables `android.overridePathCheck=true` (the workspace path contains non-ASCII characters)

## Network behavior

| Feature | Network behavior |
|---|---|
| Timetable viewing / editing / search / sharing | Fully local |
| Screenshot OCR | Chinese recognition model bundled in the APK — no network |
| Backup & export (JSON / timetable image / daily auto-backup) | Local only |
| Academic affairs import | User-initiated: in-app WebView opens the school site. Plaintext http URLs trigger a risk warning first (credentials could be intercepted), certificate errors are confirmed per site, and navigating off-site asks for confirmation. The fetched page is parsed locally; timetable data is never uploaded |
| Holiday sync | Fetches public holiday-cn data over https (3 CDN mirrors); on failure it silently falls back to the built-in State Council presets; your manual edits are never overwritten |

The only requested permission, `INTERNET`, is used exclusively by the last two features; there is no account system and nothing is uploaded.

## Tech Stack

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · WorkManager (auto-backup) · Glance (widgets) · ML Kit text recognition (offline model bundled) · Jsoup (academic affairs page parsing) · Gradle version catalog + lint gate

No account system; all data lives in the on-device database (`timetable.db`). The `INTERNET` permission is used only by the "Academic Affairs Import" page and the holiday sync (see Network behavior above) — timetable data never leaves the device.

## Known Limitations

- Legacy `.xls` (non-xlsx) is not supported — re-save as `.xlsx` in Office/WPS first
- Screenshot OCR quality depends on image clarity and layout; always review results before importing
- On Chinese ROMs (Xiaomi/Huawei etc.) you may need to manually allow "auto-start" and "background pop-up", otherwise notifications can be delayed
