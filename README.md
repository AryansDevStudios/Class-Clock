# ⏰ Class Clock

> **Android floating timetable overlay, class schedule tracker, and real-time period countdown timer.**

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-24-blue)
![targetSdk](https://img.shields.io/badge/targetSdk%20%2F%20compileSdk-36-blue)
![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)
![Status](https://img.shields.io/badge/Status-Active-brightgreen)

---

## 📖 Overview

**Class Clock** is a specialized Android utility designed for students and educators to seamlessly monitor their class timetable without interrupting active tasks. Running as a persistent foreground overlay service, it renders a draggable heads-up display (HUD) over any open application, presenting the active subject, current elapsed time, dynamic progress bar, and precise countdown to the next period.

Built in Kotlin targeting Android 16 (API Level 36) with Java 17 desugaring, Class Clock ensures high performance, minimal battery consumption, and rock-solid reliability across Android versions from Android 7.0 (Nougat) upwards.

---

## ✨ Key Features

- **Floating Overlay HUD**: Displays active period information over all Android applications using the `SYSTEM_ALERT_WINDOW` permission (`TYPE_APPLICATION_OVERLAY`).
- **Interactive Drag & Repositioning**: Touch drag listener (`setupDragListener`) smoothly tracks finger gestures (`ACTION_DOWN`, `ACTION_MOVE`), allowing users to dock or reposition the clock anywhere on screen.
- **Precision Countdown & Progress Bar**: Employs a 1-second update loop via `Handler(Looper.getMainLooper())` that computes elapsed seconds against total duration and updates a live `ProgressBar` (0–100%).
- **Structured JSON Timetable Engine**: Powered by `assets/timetable.json`, mapping a full 6-day weekly timetable (Monday through Saturday, 08:00 to 14:30) across core subjects:
  - English, Hindi, Mathematics, Physics, Chemistry, Biology, Social Science, Information Technology, and Physical Training.
- **Smart Period State Detection**: Gracefully categorizes timetable events into:
  - **Active Period**: Displays subject title, countdown, and progress.
  - **FREE TIME**: Automatically identified during lunch breaks (11:00 – 11:30) and free periods.
  - **NO SCHEDULE**: Displayed outside school hours and on Sundays.
- **Auto-Start on Device Reboot**: Features a broadcast receiver (`BootReceiver`) registered for `BOOT_COMPLETED`, automatically reinstating the timetable overlay upon system reboot.
- **Zero-Footprint Launcher Activity**: `MainActivity` utilizes an invisible theme (`Theme.Transparent`) that validates permissions, opens system settings if permission is missing, launches `OverlayService`, and terminates immediately.

---

## 🛠️ Tech Stack

| Component | Technology | Version / Spec |
|-----------|------------|----------------|
| **Language** | Kotlin | Modern Kotlin with Java 17 compatibility |
| **Platform** | Android OS | `minSdk: 24` (Android 7.0), `targetSdk: 36`, `compileSdk: 36` |
| **Concurrency & Handlers** | Android Looper & Handler | Main thread non-blocking dispatch loop |
| **Window Management** | WindowManager | `TYPE_APPLICATION_OVERLAY`, `PixelFormat.TRANSLUCENT` |
| **Serialization** | Google Gson | `2.10.1` |
| **Compatibility** | Core Library Desugaring | `desugar_jdk_libs: 2.0.4` (`java.time.*` support on API < 26) |
| **UI Components** | AndroidX & Google Material | Material 3 Components, AppCompat |

---

## 📁 Project Structure

```plaintext
Class-Clock/
├── app/
│   ├── build.gradle.kts           # SDK configurations, desugaring, and dependencies
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml # Permissions (SYSTEM_ALERT_WINDOW, BOOT_COMPLETED)
│   │       ├── assets/
│   │       │   └── timetable.json  # 6-day weekly period schedule definitions
│   │       ├── java/com/aryansdevstudios/classclock/
│   │       │   ├── MainActivity.kt # Permission verification & service trigger
│   │       │   ├── OverlayService.kt # Foreground service, HUD lifecycle, drag handler
│   │       │   ├── BootReceiver.kt # BOOT_COMPLETED broadcast receiver
│   │       │   └── TimetableModels.kt # Data models for Gson serialization
│   │       └── res/
│   │           ├── layout/        # Floating overlay layout with ProgressBar
│   │           └── values/        # App themes and transparent styles
├── gradle/                        # Gradle wrapper files
├── build.gradle.kts               # Root project build script
└── settings.gradle.kts            # Project repositories & module settings
```

---

## 🚀 Getting Started

### Prerequisites

1. **Android Studio**: Ladybug (2024.2+) or newer.
2. **JDK**: Java Development Kit 17.
3. **Android SDK**: Android API 36 Platform and Build Tools.

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/AryansDevStudios/Class-Clock.git
   cd Class-Clock
   ```

2. Assemble the debug APK using the Gradle wrapper:
   ```bash
   # On Linux / macOS:
   ./gradlew assembleDebug

   # On Windows:
   .\gradlew.bat assembleDebug
   ```

3. The generated APK will be available at:
   `app/build/outputs/apk/debug/app-debug.apk`

### Installation & Permissions

1. Install the APK to your connected Android device or emulator:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. Open **Class Clock** from your app drawer.
3. When prompted, grant the **Display over other apps** (`SYSTEM_ALERT_WINDOW`) permission in Android System Settings.
4. The floating timetable widget will appear on your screen immediately.

---

## ⚙️ Customizing the Timetable

To modify the subject timetable or period timings, edit `app/src/main/assets/timetable.json`:

```json
{
  "dayOfWeek": "MONDAY",
  "periods": [
    { "periodName": "Mathematics", "startTime": "08:00", "endTime": "08:45" },
    { "periodName": "Lunch", "startTime": "11:00", "endTime": "11:30" }
  ]
}
```

Rebuild the application to apply changes.

---

## 🤝 Contributing

Contributions, feature suggestions, and pull requests are warmly welcomed!
1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/NewTimetableFeature`)
3. Commit your Changes (`git commit -m 'Add custom period alarms'`)
4. Push to the Branch (`git push origin feature/NewTimetableFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
