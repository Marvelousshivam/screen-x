# ScreenX Release Notes — v1.7.1

## 🌟 What's New in v1.7.1

### 🔄 Wireless Debugging Liveness & Auto-Reconnect Recovery
* **Active TLS Socket Liveness Verification**:
  * Fixed an issue where Wireless Debugging appeared active in the app even after Android OS dropped the socket due to inactivity or sleep.
  * Added active connection probes (`isConnectionAlive`) on background threads before attempting commands.
* **Automatic mDNS Discovery Fallback**:
  * When Wireless Debugging is restarted or toggled in Developer Options, Android assigns a new connection port. ScreenX now automatically falls back to mDNS auto-discovery when the saved port fails, eliminating the need to un-pair and re-pair.

---

### 📱 Robust Orientation Handling (POCO X3 Pro, POCO X4 GT & MIUI/HyperOS)
* **True Hardware-Level Display Rotation**:
  * Fixed auto-rotation recording in portrait when the phone was in landscape on devices like POCO X3 Pro, POCO X4 GT, Redmi, and Xiaomi devices running MIUI/HyperOS.
  * Uses hardware display rotation (`Surface.ROTATION_90` / `ROTATION_270`) as the definitive orientation signal, completely restoring the rock-solid v1.6.0 auto-rotate behavior.

---

### 🎨 Update Dialog Bottom Sheet UI Polish
* **Clean Action Hierarchy**:
  * Resolved an issue where the "View on GitHub" button text was cramped in a 50/50 horizontal row, causing it to break into two lines and get vertically chopped in the button frame.
  * Redesigned the update dialog actions into a clean, un-cramped vertical stack:
    * Primary **"Download & Install"** button (full width).
    * Outlined **"View on GitHub"** button (full width) with single-line constraint, ellipsis overflow protection, and ample padding across all 5 localization languages (English, Spanish, Ukrainian, Russian, Hindi).
    * Subtle **"Maybe Later"** dismiss button.
* **Optional Update Checks**:
  * Clarified and confirmed that update dialogs are completely optional and only run when enabled by the user in Settings.

---

### ⏱️ Notification Chronometer Continuity
* **Preserve Elapsed Recording Time on Resume**:
  * Fixed an issue where resuming from pause reset `recordingStartTimeMs` to the current system time, which caused the notification chronometer (and status bar pill) to start counting from 00:00 again.
  * Resumed recordings now retain the true accumulated session time.

---

### 🛡️ MediaStore Error Cleanup & System Safety
* **No Orphaned Pending Videos**:
  * In `saveVideoToMediaStore`, if file writing fails or is interrupted, the pending entry is immediately removed via `resolver.delete(videoUri, null, null)`, preventing 0-byte or corrupt entries from cluttering galleries.
* **Smart WakeLock Timeout**:
  * Replaced the hardcoded 2-hour wake lock with a dynamic session-based timeout (`TIME_LIMIT_SECS * 1000L + 30_000L`) to prevent unnecessary battery drain if the service is abruptly killed.
* **Receiver Lifecycle Protection**:
  * Guarded screen-off broadcast receiver registration and unregistration against lifecycle duplicates.
* **Screenshot VirtualDisplay Safeguards**:
  * Added fallback dimensions to `takeScreenshot` if OEM custom display metrics return null or 0.

---

## 📦 Artifact Details
* **File**: `app-release.apk`
* **Version**: `v1.7.1` (Build `11`)
* **Package**: `com.gxdevs.screenx`
* **Output Path**: `app/build/outputs/apk/release/app-release.apk`
* **Target SDK**: `37` (Android 15 / 16 ready)
* **Min SDK**: `26` (Android 8.0+)
