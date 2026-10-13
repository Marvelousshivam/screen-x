# ScreenX Release Notes — v1.5.0

## 🌟 What's New in v1.5.0

### 🚀 Peak Performance & Universal Playback Optimization
* **Universal Macroblock 16-Pixel Hardware Alignment**:
  * Resolved playback jerkiness and frame stuttering on devices with non-standard panel resolutions.
  * Automatically calculates macroblock-aligned encoder dimensions (`height % 16 == 0`, `width % 16 == 0`) across Qualcomm Snapdragon, MediaTek Dimensity/Helio, Samsung Exynos, and Google Tensor VPUs.
* **AVC Baseline Profile & Monotonic Timestamps**:
  * Enforces `AVCProfileBaseline` encoding to prevent B-frame reordering judder, ensuring presentation timestamps match decode timestamps strictly (`PTS == DTS`).
  * Normalizes track timestamps with unified monotonic offsets across both video and internal/microphone audio tracks to prevent A/V desync and playback freezing.
* **SurfaceFlinger Auto-Mirroring**:
  * Configured virtual display projection with `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR` to ensure consistent compositor mirroring without dropped frames on aggressive OEM vendor skins (Realme UI, ColorOS, MIUI, HyperOS).
* **Dynamic Hardware Capability Negotiation**:
  * Intelligently queries encoder supported resolutions, frame rates, and bitrates with safe universal fallbacks. Clamped universal recording bitrate to an optimal 10 Mbps and 60 FPS standard.

---

### 📱 Responsive Wide Screen, Tablet & Foldable Experience
* **Adaptive Multi-Column Layout**:
  * Automatically activates dual-column dashboard layout on landscape mode and unfolded foldable displays (`screenWidthDp >= 600`), such as Samsung Galaxy Z Fold, Google Pixel Fold, and OnePlus Open.
  * Constrained maximum content widths (`1200.dp` for multi-column dashboards, `600.dp` for single-column portrait, `768.dp` for Settings, and `900.dp` for Video Trimmer) to provide a clean, modern interface on wide screens, tablets, and Chromebooks.
* **Adaptive Gallery Grid**:
  * Dynamic grid scaling (`GridCells.Adaptive(160.dp)`) gracefully renders 2 columns on phones, 3–4 columns on foldables, and 5–6 columns on tablets.
* **Optimized Dialogs & Bottom Sheets**:
  * All bottom sheets (Update Dialog, ADB Pairing Sheet, ADB Limitations Sheet) and option dialogs are now width-constrained (`440.dp` – `640.dp`) and center-aligned.
  * Added vertical scrolling to option selection dialogs to prevent clipping in split-screen, fold, or landscape modes.

---

## 📦 Artifact Details
* **File**: `app-release.apk`
* **Version**: `v1.5.0` (Build `8`)
* **Package**: `com.gxdevs.screenx`
* **Target SDK**: `37` (Android 15 ready)
* **Min SDK**: `26` (Android 8.0+)
