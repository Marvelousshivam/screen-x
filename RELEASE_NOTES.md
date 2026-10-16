# ScreenX Release Notes — v1.8.0

## 🌟 What's New in v1.8.0

### 🔊 Speaker Audio Capture for Stealth (ADB) Recording
* **Acoustic Speaker Capture Without System Dialog**:
  * Android's internal `screenrecord` binary cannot record audio, and standard internal audio capture requires a visible system `MediaProjection` prompt. ScreenX now introduces high-fidelity acoustic speaker capture utilizing low-latency microphone DSP without triggering any screen-capture dialogs.
* **Smart Volume Boost & Noise Clearing**:
  * **Digital Pre-Amp Amplification**: Multiplies speaker audio output (2.5x gain) so game sound and app audio are loud and crisp.
  * **Soft-Knee `tanh` Saturation Limiter**: Prevents digital clipping, distortion, and harsh peaks during intense sound effects.
  * **Hardware Noise Suppression & Rumble High-Pass Filter**: Bypasses ambient room rumble and fan hum using Android's native hardware `NoiseSuppressor` combined with a second-order Butterworth high-pass filter.
* **Lossless Audio-Video Muxing**:
  * High-efficiency AAC audio stream is encoded in real-time and losslessly remuxed with the video track upon completion with synchronized PTS timestamps.
* **Dedicated Settings Toggle**:
  * Added switch in **Settings > Stealth Recording**: *"Record Audio from Speaker"* with instant permission handling.

---

### ♾️ Unlimited ADB Recording with Auto-Continue & Lossless Merge
* **Bypass Android's 3-Minute Limit**:
  * Android's `/system/bin/screenrecord` has a hard 3-minute cutoff. ScreenX now seamlessly continues recording past the 3-minute limit without any gap by automatically spinning up the next recording segment.
* **Lossless Background Video Concatenation**:
  * When recording stops, all video segments are losslessly joined into a single unified `ADB_*.mp4` file in the device gallery.
* **Progress & Permission Notifications**:
  * Added active progress notifications ("Merging X recording parts into a single video…" / "Finalizing and saving video to gallery…") keeping the user informed during file processing.
* **Auto-Merge Switch in Settings**:
  * Toggle *"Auto-Continue Past 3 Min & Merge"* to enable or disable automatic segment concatenation.

---

### 🛠️ In-Depth Diagnostic Logs Export (`log.txt`)
* **One-Click Diagnostic Sharing**:
  * Added **"Export Diagnostic Logs (log.txt)"** under **Settings > Diagnostics & Logs**.
  * Logs comprehensive device metrics at every step: WindowManager metrics, physical display rotation (`ROTATION_0`, `ROTATION_90`, `ROTATION_270`), Sensor HAL orientation states, aspect ratio calculations, encoder dimensions, and ADB connection diagnostics.
  * Easily export and share directly with developers to resolve device-specific custom ROM anomalies.

---

### 📱 Orientation Simplification & Custom ROM Fixes
* **Streamlined UI Options**:
  * Removed confusing "Reverse Landscape" option; simplified orientation choices to **Auto**, **Portrait**, and **Landscape**.
* **Hardened Custom ROM Display Dimension Safeguard**:
  * Fixed landscape pillarboxing issues on devices like POCO X3 Pro (vayu) running custom ROMs (InfinityX 3.12, crDroid, LineageOS) where system bounds erroneously report portrait dimensions during landscape capture.

---

### 🌐 Multi-Language Localization
* Added complete strings and descriptions for all new features across 5 languages:
  * English (EN)
  * Spanish (ES)
  * Russian (RU)
  * Ukrainian (UK)
  * Hindi (HI)

---

## 📦 Artifact Details
* **File**: `app-release.apk`
* **Version**: `v1.8.0` (Build `13`)
* **Package**: `com.gxdevs.screenx`
* **Output Path**: `app/build/outputs/apk/release/app-release.apk`
* **Target SDK**: `37` (Android 15 / 16 ready)
* **Min SDK**: `26` (Android 8.0+)
