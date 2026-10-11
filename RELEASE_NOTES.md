# ScreenX Release Notes — v1.4.1

## 🚀 What's New & Enhancements

* **🎨 Native Mipmap App Icon on Home Screen**
  * Integrated the official app launcher icon (`R.mipmap.ic_launcher`) alongside the "ScreenX" title on the Home Screen across both portrait and landscape headers with smooth rounded squircle styling.

---

## 🐛 Bug Fixes & Stability Improvements (Comprehensive Line-by-Line Audit)

* **⏱️ Countdown Overlay Timer Correction**:
  * Fixed an issue where the countdown timer started at 3 but decremented before rendering, resulting in only ~2 seconds of visible countdown and firing 1 second early. Now smoothly counts 3 → 2 → 1 for the full 3 seconds before recording begins.

* **🛡️ Android 14+ Foreground Service Stability**:
  * Guarded `startRecordingSequence` with foreground service check (`!isForeground`) to eliminate `ForegroundServiceTypeNotAllowedException` / crash when switching from floating control overlay to active recording.
  * Modernized display metrics to use `WindowManager.currentWindowMetrics.bounds` and `densityDpi` on Android 11+ (API 30+), eliminating deprecated API usages.

* **🎬 MediaMuxer Corrupt Output Prevention**:
  * Added safety checks in `mergeVideoAndAudio` to cleanly invoke `muxer.stop()` before `release()` if muxer was started, preventing corrupted MP4 files and native crashes during error states.

* **✂️ Video Trimmer Segment Preservation**:
  * Fixed segment loss in `cutMiddle` on OEM filesystems where `renameTo()` could cause intermediate clips to be erroneously deleted in the `finally` cleanup block. Added fallback file copying and rename tracking.
  * Resolved timeline PTS measurement in `measureDuration` by continuing scan past keyframes to accurately detect the true last frame PTS for smooth clip concatenation without audio/video overlap.

* **🧹 Memory Leak & Resource Cleanup**:
  * Screenshot processing now explicitly recycles intermediate ARGB bitmaps immediately after writing to MediaStore, freeing up ~10MB of heap per screenshot.
  * Attached `SupervisorJob` to `FloatingControlOverlay` coroutine scope with clean cancellation on overlay dismiss, preventing background coroutine leaks.
  * Guarded `stopForeground()` in `exitService()` to only fire when the service is actively in foreground mode.

* **🔒 Scoped Storage & Security Enhancements**:
  * Fixed `IllegalArgumentException` on devices where `DATA` and `RESOLUTION` MediaStore columns return `-1` index by using safe column checks in `VideoHelper`.
  * Resticted `RECORDING_SAVED` broadcast in `AdbRecordService` using explicit package targeting (`setPackage(packageName)`), preventing broadcast leaks on Android 13+.
  * Upgraded ADB X.509 certificate serial number generator to use `SecureRandom` in compliance with RFC 5280.
  * Corrected `isMicMuted` UI state in floating controls to directly track service status rather than optimistic flips.

---

## 📦 Artifact Details
* **File**: `app-release.apk`
* **Version**: `v1.4.1` (Build `6`)
* **Package**: `com.gxdevs.screenx`
* **Target SDK**: `37` (Android 15 ready)
* **Min SDK**: `26` (Android 8.0+)
