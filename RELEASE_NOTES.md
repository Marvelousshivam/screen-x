# ScreenX Release Notes — v1.6.0

## 🌟 What's New in v1.6.0

### 🌍 Universal Multilingual Localization & Translation Parity
* **5 Fully Supported Languages**:
  * Native translation support for **English (`en`)**, **Spanish (`es`)**, **Ukrainian (`uk`)**, **Russian (`ru`)**, and **Hindi (`hi`)**.
  * **100% Dictionary Parity**: All string keys thoroughly translated across all 5 localization dictionaries with 0 missing strings.
* **Modern Language Selection Bottom Sheet**:
  * Accessible directly from **Settings > Appearance & Theme > App Language**.
  * Built using a custom `ModalBottomSheet` tailored to ScreenX's Orion dark aesthetic (28dp rounded top corners, custom subtle drag handle, and glowing emerald accents).
  * Features native script language titles, English subtitle translations, language code badges (`EN`, `ES`, `UK`, `RU`, `HI`), and smooth radio-selection animations.
* **Persistent Across Reboots & Restarts**:
  * Language preference is permanently saved via `SettingsManager` and synchronized with Android 13+ per-app language settings via `locales_config.xml`.
  * Preserves selected language across device restarts and task terminations without resetting to system defaults.

---

### ⏱️ Dynamic Countdown Timer & 20s Duration Option
* **Accurate Variable Countdown Duration**:
  * Resolved an issue where countdown wait times always defaulted to 3 seconds regardless of user selection.
  * Added a new **20s** countdown option (`Off`, `3s`, `5s`, `10s`, `20s`) for comprehensive recording preparations.
* **Non-Blocking Overlay & Tick Callbacks**:
  * Decoupled countdown timer loop from window overlay attachment so countdowns complete accurately even if overlay permissions are unavailable.
  * Per-second tick callbacks sync real-time remaining countdown seconds with foreground notifications and status bar chips.

---

### 🔆 Keep Screen Awake Setting
* **Prevent Screen Sleep While Recording**:
  * Added a dedicated **Keep Screen Awake** toggle in **Settings > Recording Controls**.
  * Acquires a managed `PowerManager.WakeLock` (`SCREEN_BRIGHT_WAKE_LOCK or ON_AFTER_RELEASE`) throughout countdown and recording sessions to stop displays from dimming or sleeping.
  * Cleanly releases wake locks upon recording finalization, manual stops, cancellations, or service destruction, with a 2-hour fail-safe safety timeout.

---

### 🟢 Live Status Bar Pill & OEM Live Notifications
* **Cross-OEM Status Bar Countdown & Recording Chip**:
  * Added a **Live Status Bar Pill** toggle in **Settings > Recording Controls**.
  * **Samsung One UI 7/8 Now Bar**: Fully integrated via ongoing notification styling (`android.ongoingActivityNoti.style`) and chronometers.
  * **Android 16+ Promoted Live Updates**: Native support for `setRequestPromotedOngoing(true)` and `setShortCriticalText`.
  * **Xiaomi HyperOS & OPPO/OnePlus ColorOS/OxygenOS**: Native support for `miui.focus.notification` and `coloros.notification.capsule`.

---

### 🛡️ Robust Notification Permission Fallback
* **Safety Controls When Notifications are Disabled**:
  * If `POST_NOTIFICATIONS` is denied on Android 13+ or disabled in system settings, ScreenX automatically displays the `FloatingControlOverlay` ball as a fallback so users never lose the ability to pause or stop recording.
  * Displays user-friendly toasts explaining why notifications aren't visible and how to stop stealth capture.
  * The **Live Status Bar Pill** setting warns users when notifications are disabled and directly opens system notification settings when tapped.

---

## 📦 Artifact Details
* **File**: `app-release.apk`
* **Version**: `v1.6.0` (Build `9`)
* **Package**: `com.gxdevs.screenx`
* **Output Path**: `app/build/outputs/apk/release/app-release.apk`
* **Size**: `29.8 MB (29,831,488 bytes)`
* **Target SDK**: `37` (Android 15 / 16 ready)
* **Min SDK**: `26` (Android 8.0+)
