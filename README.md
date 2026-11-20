# Tracker

A native Android money tracker built with Kotlin and Jetpack Compose. Track expenses across multiple funds, manage debts and receivables, review spending analytics, and keep everything private on your device, in English or Bangla.

![Platform](https://img.shields.io/badge/platform-Android%205.1%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Build](https://img.shields.io/badge/build-GitHub%20Actions-2088FF)

---

## Features

### Funds and transactions
- Multiple funds (accounts) with their own type, icon and colour. Reorder them by long-press and drag.
- Add, edit and delete expenses; add money to a fund; transfer between funds.
- Custom categories with a large icon set, plus a category slider with drag-to-reorder.
- Daily spending chart (week / month view) with a per-day tooltip.
- Balance checks and a one-day amount lock on edits to protect your history.

### Logs
- Full transaction history with search, sort, time and price filters.
- Calendar and wheel date pickers; days with entries are marked.
- Search highlighting and per-section notes.

### Debts and receivables
- Record money you owe and money owed to you, with due date and reminder time.
- Partial payments, payment history, progress bars, priority and "key person" flags.
- Optionally link an entry to a fund so balances update automatically.
- Pending / Settled views with filters and sorting.

### Stats
- **Spending:** overview (total, daily average, count), top categories, and insights such as biggest expense and most active day.
- **Debts and receivables:** net position, settlement progress and top partners.
- Month filter and an "All Categories" summary sheet.

### Security and privacy
- Screen lock: fingerprint / face (Android BiometricPrompt), 4-digit PIN, or pattern.
- PIN and pattern are stored only as SHA-256 hashes.
- All data stays on the device. The only network use is the optional update check.

### Reminders
- Four daily expense reminders (morning, afternoon, evening, night) with editable times.
- A due-date reminder for every pending debt or receivable.
- Scheduled with `AlarmManager` (exact alarms where the device allows) and restored after reboot or app update.

### Backup and restore
- Export all data to a JSON file (saved to Documents or shared through the share sheet).
- Import with full validation and an all-or-nothing restore.
- Reads backups made by the earlier web version of the app.

### Personalisation
- Dark and light mode, English and Bangla (with Bangla digits and dates), custom avatar and display name.
- Time-based greeting on the Profile screen.

---

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.0.20 |
| UI | Jetpack Compose (BOM 2024.09.00), custom components, no Material dependency |
| Min / target SDK | 22 / 34 |
| Build | Gradle 8.9, Android Gradle Plugin 8.5.2, JDK 17 |
| Async | Kotlin Coroutines |
| Auth | AndroidX Biometric |
| Storage | Local JSON state with atomic file writes |
| CI / CD | GitHub Actions, GitHub Releases |

---

## Project structure

```
app/src/main/
├── assets/i18n_bn.json         Bangla translation table
├── java/com/myapp/app/
│   ├── MainActivity.kt
│   ├── data/                   Models, AppStore, backup/import, session, reminder settings
│   ├── reminder/               Notification channel, alarms, boot receiver
│   ├── util/                   Money and date formatting, i18n, digits, categories, update checker
│   └── ui/
│       ├── TrackerRoot.kt      App shell and tab host
│       ├── AppUi.kt            Tab and panel state
│       ├── theme/              Colours, fonts, system bars
│       ├── components/         Bottom nav, charts, wheel picker, icons, shared widgets
│       ├── screens/            Home, Logs, Debts, Stats, Profile, Fund Details
│       └── popup/              Add/edit dialogs, pickers, lock screens, backup, reminders
└── res/                        Icons, splash, themes, file provider paths
```

---

## Getting started

### Install
Download the latest APK from the [Releases page](https://github.com/darkrider13/Tracker-V2/releases/tag/latest) and install it. Android may ask you to allow installs from your browser or file manager.

### Build locally
Requirements: JDK 17 and Android Studio (or the Android SDK command-line tools).

```bash
git clone https://github.com/darkrider13/Tracker-V2.git
cd Tracker-V2
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

> The debug build is signed with the project keystore when `app/tracker.keystore` and the `KEYSTORE_PASSWORD` environment variable are present. Without them the build is signed with Android's default debug key.

### Build with GitHub Actions
Every push to `main` (or a manual run) builds the app and publishes it.

1. In the repository, open **Settings → Secrets and variables → Actions** and add:
   - `KEYSTORE_BASE64`: your keystore file, base64-encoded
   - `KEYSTORE_PASSWORD`: the keystore password
2. Push to `main`, or run the **Build Kotlin Android APK** workflow manually.
3. The workflow publishes `Tracker.apk` and `version.json` to the `latest` release.

Version numbers are generated automatically: `versionCode = 1000 + run number` and `versionName = 2.0.<run number>`, so each build installs as an update over the previous one.

---

## Permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Show reminders |
| `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` | Deliver reminders at the chosen time |
| `RECEIVE_BOOT_COMPLETED` | Re-register reminders after a reboot |
| `USE_BIOMETRIC` | Fingerprint / face lock |
| `INTERNET` | Check for and download app updates |
| `REQUEST_INSTALL_PACKAGES` | Install a downloaded update |
| `READ/WRITE_EXTERNAL_STORAGE` (Android 9 and below) | Save and read backup files |

---

## Data and backups

- Data is stored in the app's private storage. Transactions older than 6 years and settled debts older than 15 years are purged automatically.
- Backup files use the type `smart_saver_elite_backup` (version 2). Unknown fields are preserved on import.
- To move data from an older version, export from the old app and import in the new one from the Profile screen.

---

## Contributing

This is a personal project, but issues and suggestions are welcome. For code changes, open an issue first to discuss what you would like to change.

---

## License

© Md. Insanul Islam Abir. All rights reserved.
