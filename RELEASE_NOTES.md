# ScreenX Release Notes — v1.7.2

## 🌟 What's New in v1.7.2

### 📱 Restored Exact v1.4.2 Auto-Rotation & Window Metrics (POCO & Xiaomi Fix)
* **Root Cause Resolved**:
  * In versions after v1.4.2, dimension calculation had been refactored to rely on background `Display.rotation` queries and forced `minDim`/`maxDim` constraints. On devices like the POCO X3 Pro, POCO X4 GT, Redmi, and Xiaomi devices running MIUI/HyperOS, `Display.rotation` inside a background service always returned `0`, incorrectly forcing recordings into portrait mode (`1080x2400`) and causing letterboxed/squished output.
* **Exact v1.4.2 Proven Mechanics Restored**:
  * Restored the direct `windowManager.currentWindowMetrics.bounds` rotation-aware query on Android 11+ (API 30+) and real display metrics on earlier APIs.
  * Reinstated the clean `getTargetDimensions` aspect-ratio scaling from v1.4.2 (`isLandscape = screenWidth > screenHeight`).
  * Reverted the `VirtualDisplay` flags to `DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC`, which reliably adapts to device rotation across all OEM compositors without forcing portrait letterboxing.
  * Restored matching window metrics calculation for ADB stealth recording in `AdbRecordService`.

---

### 🔄 Wireless Debugging Liveness & Auto-Reconnect Recovery
* **Active TLS Socket Liveness Verification**:
  * Fixed an issue where Wireless Debugging appeared connected even after Android OS dropped the socket due to inactivity or sleep.
  * Added active connection health checks (`isConnectionAlive`) on background threads.
* **Automatic mDNS Discovery Fallback**:
  * When Wireless Debugging is restarted or toggled in Developer Options, ScreenX automatically falls back to mDNS auto-discovery when the saved port fails, eliminating the need to un-pair and re-pair.

---

### 🎨 Update Dialog Bottom Sheet UI Polish
* **Clean Action Hierarchy**:
  * Resolved an issue where the "View on GitHub" button text was cramped in a 50/50 horizontal row, causing it to break into two lines and get vertically chopped.
  * Restructured update dialog actions into a clean, un-cramped vertical stack:
    * Primary **"Download & Install"** button (full width).
    * Outlined **"View on GitHub"** button (full width) with single-line constraint and ellipsis overflow protection across all 5 localization languages (English, Spanish, Ukrainian, Russian, Hindi).
    * Subtle **"Maybe Later"** dismiss button.

---

### ⏱️ Notification Chronometer Continuity
* **Preserve Elapsed Recording Time on Resume**:
  * Resuming from pause now maintains the continuous elapsed session time instead of resetting the notification chronometer and status bar pill to 00:00.

---

### 🛡️ MediaStore Error Cleanup & System Safety
* **No Orphaned Pending Videos**:
  * Pending MediaStore URIs are immediately removed if writing fails, preventing 0-byte entries from cluttering galleries.
* **Smart WakeLock Timeout**:
  * Dynamically scaled the wake lock timeout to session duration + buffer (`TIME_LIMIT_SECS * 1000L + 30_000L`).
* **Receiver Lifecycle Protection**:
  * Guarded broadcast receivers against duplicate registrations across service restarts.

---

## 📦 Artifact Details
* **File**: `app-release.apk`
* **Version**: `v1.7.2` (Build `12`)
* **Package**: `com.gxdevs.screenx`
* **Output Path**: `app/build/outputs/apk/release/app-release.apk`
* **Target SDK**: `37` (Android 15 / 16 ready)
* **Min SDK**: `26` (Android 8.0+)
