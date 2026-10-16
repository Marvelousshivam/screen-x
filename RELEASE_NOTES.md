# ScreenX Release Notes — v1.7.2

## 🌟 What's New in v1.7.2

### 📱 Complete Auto-Rotation Fix (POCO X3 Pro, POCO X4 GT & Custom ROMs)
* **Root Cause Resolved**:
  * In versions after v1.4.2, dimension calculation had issues on devices like POCO X3 Pro (vayu), POCO X4 GT, Redmi, and custom ROMs (InfinityX, crDroid, LineageOS) where WindowManager metrics or background display queries reported portrait bounds (1080x2400) during landscape games, leading to portrait pillarboxing (black bars on top and bottom).
* **Hard-Enforced Dimension Safeguards**:
  * **Fail-Safe Orientation Detection**: Combines `Display.rotation` (90°/270°), system `Configuration.orientation`, and real-time window metrics.
  * **Dimension-Swap Safeguard**: Hard-enforces `screenWidth = maxOf(w, h)` and `screenHeight = minOf(w, h)` whenever landscape is active, guaranteeing an encoder never allocates a vertical canvas during landscape gameplay regardless of misconfigured ROM metrics.
  * **Stealth ADB Mode Parity**: Applied the identical dimension-swap constraint mapping to `/system/bin/screenrecord` execution in `AdbRecordService`.
  * **MediaMuxer Metadata Hint Preservation**: Automatically preserves orientation track matrix hints in remuxed MP4 headers.
  * **Simplified UI Options**: Kept clean and intuitive with exactly **Auto**, **Portrait**, and **Landscape**.

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
