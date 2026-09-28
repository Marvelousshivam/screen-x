<div align="center">

  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" alt="ScreenX Logo" width="128" height="128" />

# ScreenX

**Pure, powerful screen recording for Android. No limits, no nonsense.**

  <p>
    <a href="https://github.com/gtxprime/screen-x/stargazers">
      <img src="https://img.shields.io/github/stars/gtxprime/screen-x?style=for-the-badge&logo=github&color=21262d&logoColor=white" alt="Stars" />
    </a>
    <a href="https://github.com/gtxprime/screen-x/network/members">
      <img src="https://img.shields.io/github/forks/gtxprime/screen-x?style=for-the-badge&logo=github&color=21262d&logoColor=white" alt="Forks" />
    </a>
    <a href="https://github.com/gtxprime/screen-x/issues">
      <img src="https://img.shields.io/github/issues/gtxprime/screen-x?style=for-the-badge&logo=github&color=21262d&logoColor=white" alt="Issues" />
    </a>
    <a href="https://github.com/gtxprime/screen-x/blob/main/LICENSE">
      <img src="https://img.shields.io/badge/License-MIT-21262d?style=for-the-badge&logoColor=white" alt="License" />
    </a>
    <a href="#">
      <img src="https://img.shields.io/badge/Platform-Android-21262d?logo=android&logoColor=white&style=for-the-badge" alt="Platform" />
    </a>
    <a href="https://github.com/gtxprime/screen-x/releases/latest">
      <img src="https://img.shields.io/github/downloads/gtxprime/screen-x/total?label=Downloads&logo=github&style=for-the-badge&color=21262d&logoColor=white" alt="GitHub Downloads" />
    </a>
  </p>

  <a href="https://github.com/gtxprime/screen-x/releases/latest">
    <img src="https://raw.githubusercontent.com/gtxprime/mind-mint/main/docs/assets/github_badge.png" height="96" alt="Get it on GitHub" />
  </a>

  <h3>
    <a href="#-features">Features</a>
    <span> &bull; </span>
    <a href="#-tech-stack">Tech Stack</a>
    <span> &bull; </span>
    <a href="#-project-structure">Project Structure</a>
    <span> &bull; </span>
    <a href="#-installation">Installation</a>
    <span> &bull; </span>
    <a href="#-roadmap">Roadmap</a>
    <span> &bull; </span>
    <a href="#-contributing">Contributing</a>
  </h3>

</div>

---

## <img src="https://api.iconify.design/fa6-solid/mobile-screen.svg?color=%23ffffff#gh-dark-mode-only" height="20" align="center" alt="About" /><img src="https://api.iconify.design/fa6-solid/mobile-screen.svg?color=%23121212#gh-light-mode-only" height="20" align="center" alt="About" /> About ScreenX

> [!NOTE]
> **ScreenX** is a modern, high-performance screen recording utility built for Android. It prioritizes smooth performance, minimal system overhead, and useful productivity features like live annotations and floating overlays. Whether you're recording gameplay, creating app walkthroughs, or capturing bug reports, ScreenX handles it with style.

---

## <a id="-features"></a><img src="https://api.iconify.design/fa6-solid/cubes.svg?color=%23ffffff#gh-dark-mode-only" height="20" align="center" alt="Features" /><img src="https://api.iconify.design/fa6-solid/cubes.svg?color=%23121212#gh-light-mode-only" height="20" align="center" alt="Features" /> Core Features

### <img src="https://api.iconify.design/fa6-solid/user-secret.svg?color=%23ffffff#gh-dark-mode-only" height="18" align="center" alt="Stealth" /><img src="https://api.iconify.design/fa6-solid/user-secret.svg?color=%23121212#gh-light-mode-only" height="18" align="center" alt="Stealth" /> Stealth Recording (Wireless ADB)
> Completely rootless, background recording without system prompts or app-level detection.

* <img src="https://api.iconify.design/fa6-solid/eye-slash.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/eye-slash.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Undetected by Apps:** Runs via a local Android Debug Bridge (ADB) daemon session. Because it operates outside the standard `MediaProjection` API surface, apps that actively monitor screen recording listeners (such as **Snapchat**) won't detect or trigger recording notifications.
* <img src="https://api.iconify.design/fa6-solid/bolt.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/bolt.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **1-Tap Notification Pairing:** Easily pair your device using Android 11+ Wireless Debugging&mdash;simply tap Reply on the ScreenX notification and submit your 6-digit code.
* <img src="https://api.iconify.design/fa6-solid/wifi.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/wifi.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Zero-Config mDNS Discovery:** Automatically scans and detects wireless debugging ports on your local Wi-Fi.
* <img src="https://api.iconify.design/fa6-solid/volume-xmark.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/volume-xmark.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Video-Only (No Audio/Voice):** Runs on Android's native `screenrecord` shell command, which only captures the display surface. It **does not record audio or voice** (internal or microphone audio is not captured in Stealth mode). Use standard recording mode if audio capture is required.
* <img src="https://api.iconify.design/fa6-solid/shield-halved.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/shield-halved.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Limitations & `FLAG_SECURE`:** 
  > [!IMPORTANT]
  > Stealth Recording **cannot bypass Android's OS-level `FLAG_SECURE`**. Banking apps, DRM video players (e.g. Netflix, Prime Video), or protected views that explicitly set `WindowManager.LayoutParams.FLAG_SECURE` will be rendered as black screens by Android's hardware surface flinger.

---

### <img src="https://api.iconify.design/fa6-solid/video.svg?color=%23ffffff#gh-dark-mode-only" height="18" align="center" alt="Video" /><img src="https://api.iconify.design/fa6-solid/video.svg?color=%23121212#gh-light-mode-only" height="18" align="center" alt="Video" /> High-Fidelity Recording
> Configure video output exactly to your device and storage needs.

* <img src="https://api.iconify.design/fa6-solid/sliders.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/sliders.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Custom Configurations:** Adjust resolution (up to 1080p+), frame rates (30/60 FPS), and bitrates.
* <img src="https://api.iconify.design/fa6-solid/film.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/film.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Format Control:** Output `.mp4` video files using hardware-accelerated MediaCodec API.
* <img src="https://api.iconify.design/fa6-solid/rotate.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/rotate.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Dynamic Orientation:** Adapts recording orientation automatically based on device state.

---

### <img src="https://api.iconify.design/fa6-solid/microphone-lines.svg?color=%23ffffff#gh-dark-mode-only" height="18" align="center" alt="Audio" /><img src="https://api.iconify.design/fa6-solid/microphone-lines.svg?color=%23121212#gh-light-mode-only" height="18" align="center" alt="Audio" /> Capture Options
> Clean sound options for any recording context.

* <img src="https://api.iconify.design/fa6-solid/headphones.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/headphones.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Synchronized Dual Audio:** Record internal system audio and external microphone audio simultaneously with synchronized sample interleaving.
* <img src="https://api.iconify.design/fa6-solid/volume-high.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/volume-high.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Audio Sources:** Switch effortlessly between Microphone, System Audio (Android 10+), or Dual Audio.
* <img src="https://api.iconify.design/fa6-solid/sliders.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/sliders.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Custom Quality:** Configure sample rates and audio bitrates for crystal-clear sound.

---

### <img src="https://api.iconify.design/fa6-solid/paintbrush.svg?color=%23ffffff#gh-dark-mode-only" height="18" align="center" alt="Brush" /><img src="https://api.iconify.design/fa6-solid/paintbrush.svg?color=%23121212#gh-light-mode-only" height="18" align="center" alt="Brush" /> Live Annotations & Brush
> Annotate your screen on-the-fly while recording is active.

* <img src="https://api.iconify.design/fa6-solid/pen-nib.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/pen-nib.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Draw on Screen:** Canvas overlay lets you draw directly on top of active apps.
* <img src="https://api.iconify.design/fa6-solid/palette.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/palette.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Custom Styling:** Choose brush colors dynamically and adjust brush size.
* <img src="https://api.iconify.design/fa6-solid/broom.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/broom.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Quick Actions:** Erase strokes or clear the canvas instantly.

---

### <img src="https://api.iconify.design/fa6-solid/table-columns.svg?color=%23ffffff#gh-dark-mode-only" height="18" align="center" alt="Overlay" /><img src="https://api.iconify.design/fa6-solid/table-columns.svg?color=%23121212#gh-light-mode-only" height="18" align="center" alt="Overlay" /> Floating Control Panel
> Non-intrusive widget for quick, easy management.

* <img src="https://api.iconify.design/fa6-solid/bolt.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/bolt.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Quick Access:** Expanded controls for record, pause, stop, and brush tools.
* <img src="https://api.iconify.design/fa6-solid/magnet.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/magnet.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Smart Snapping:** Drag-and-drop widget snaps to screen edges and saves position.
* <img src="https://api.iconify.design/fa6-solid/eye.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/eye.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Auto-Hide:** Fades/hides during inactivity or user interaction.

---

### <img src="https://api.iconify.design/fa6-solid/toggle-on.svg?color=%23ffffff#gh-dark-mode-only" height="18" align="center" alt="Tile" /><img src="https://api.iconify.design/fa6-solid/toggle-on.svg?color=%23121212#gh-light-mode-only" height="18" align="center" alt="Tile" /> Quick Settings Tile Integration
> Start recording in a single tap without opening the main app interface.

* <img src="https://api.iconify.design/fa6-solid/hand-pointer.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/hand-pointer.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **One-Tap Recording:** Instantly initiate or stop recordings directly from Android Quick Settings.
* <img src="https://api.iconify.design/fa6-solid/gears.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/gears.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Background Launching:** Handles foreground service and media projection requests seamlessly.

---

## <a id="-tech-stack"></a><img src="https://api.iconify.design/fa6-solid/microchip.svg?color=%23ffffff#gh-dark-mode-only" height="20" align="center" alt="Tech Stack" /><img src="https://api.iconify.design/fa6-solid/microchip.svg?color=%23121212#gh-light-mode-only" height="20" align="center" alt="Tech Stack" /> Tech Stack & Architecture

ScreenX is designed with modern Android development practices, ensuring scalability, performance, and clean code division:

* **Language:** 100% Kotlin
* **UI Framework:** Jetpack Compose with Material Design 3 and AGSL (Android Graphics Shading Language) dynamic hardware-accelerated shaders
* **Background Tasks:** Android Foreground Services (`ScreenRecordService`, `AdbRecordService`, `PairingInputService`)
* **Media Pipelines:** MediaProjection API, Wireless ADB daemon capture, and synchronized dual `AudioRecord` / `AudioPlaybackCapture`
* **State Management:** Kotlin Coroutines and Flows for reactive settings management
* **Data Layer:** Jetpack DataStore Preferences for storing user configurations

---

## <a id="-project-structure"></a><img src="https://api.iconify.design/fa6-solid/folder-tree.svg?color=%23ffffff#gh-dark-mode-only" height="20" align="center" alt="Structure" /><img src="https://api.iconify.design/fa6-solid/folder-tree.svg?color=%23121212#gh-light-mode-only" height="20" align="center" alt="Structure" /> Project Structure

```
screen-x
│
├── app/src/main/java/com/gxdevs/screenx/
│   ├── data/
│   │   ├── AdbManager.kt            # TLS key-exchange, pairing, and ADB daemon commands
│   │   ├── AdbMdns.kt               # Local mDNS discovery for Wireless Debugging ports
│   │   └── SettingsManager.kt       # Manages recording, audio, and UI settings
│   │
│   ├── service/
│   │   ├── ScreenRecordService.kt   # Standard MediaProjection recording service
│   │   ├── AdbRecordService.kt      # Stealth recording service via local ADB socket
│   │   ├── PairingInputService.kt   # Background notification reply receiver for pairing
│   │   ├── AudioCaptureHelper.kt    # Synchronized dual internal & microphone audio capture
│   │   ├── FloatingControlOverlay.kt # Draggable overlay control panel
│   │   ├── BrushDrawingOverlay.kt   # Canvas overlay for drawing on screen
│   │   ├── CountdownOverlay.kt      # Initial countdown overlay before recording
│   │   ├── ScreenXTileService.kt    # Quick Settings Tile service
│   │   └── TileHelperActivity.kt    # Invisible activity helper for tile launches
│   │
│   ├── ui/
│   │   ├── components/
│   │   │   └── ShaderGradientCard.kt # AGSL brushed gunmetal metallic shader with touch ripple
│   │   ├── screens/
│   │   │   ├── HomeScreen.kt        # Home UI with stealth/standard modes & video gallery
│   │   │   └── SettingsScreen.kt    # Material You stacked settings & audio configurations
│   │   └── theme/
│   │       ├── Color.kt             # Material 3 theme colors & dark container styling
│   │       ├── Theme.kt             # Application theme initialization
│   │       └── Type.kt              # Font and typography settings
│   │
│   ├── utils/
│   │   └── VideoHelper.kt           # Utilities for video file queries and deletions
│   │
│   └── MainActivity.kt              # Entry point activity handling permissions & navigation
```

---

## <a id="-installation"></a><img src="https://api.iconify.design/fa6-solid/screwdriver-wrench.svg?color=%23ffffff#gh-dark-mode-only" height="20" align="center" alt="Installation" /><img src="https://api.iconify.design/fa6-solid/screwdriver-wrench.svg?color=%23121212#gh-light-mode-only" height="20" align="center" alt="Installation" /> Installation & Development Setup

### Prerequisites
* Android Studio (Ladybug or newer recommended)
* Android SDK 26 (Android 8.0) or higher
* Java Development Kit (JDK) 17

### Building from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/gtxprime/screen-x.git
   cd screen-x
   ```
2. Open the project in Android Studio.
3. Sync Gradle and build the project:
   ```bash
   ./gradlew assembleRelease
   ```
4. Run the app on a connected physical device or emulator.

---

## <a id="-roadmap"></a><img src="https://api.iconify.design/fa6-solid/map-location-dot.svg?color=%23ffffff#gh-dark-mode-only" height="20" align="center" alt="Roadmap" /><img src="https://api.iconify.design/fa6-solid/map-location-dot.svg?color=%23121212#gh-light-mode-only" height="20" align="center" alt="Roadmap" /> Upcoming Roadmap

Here are features actively in development and planned for upcoming updates:

* [ ] <img src="https://api.iconify.design/fa6-solid/camera.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/camera.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Camera Overlay (Facecam):** Floating front/back camera bubble overlay on the screen while recording, with drag-to-move, pinch-to-resize, and circular/rectangular shape customization.
* [x] <img src="https://api.iconify.design/fa6-solid/headphones.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/headphones.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Simultaneous Dual Audio:** Concurrent mic and system audio capture with hardware sample synchronization.
* [x] <img src="https://api.iconify.design/fa6-solid/user-secret.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/user-secret.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Stealth Recording Mode:** Background rootless capture without app-level recording alerts.
* [ ] <img src="https://api.iconify.design/fa6-solid/cloud-arrow-up.svg?color=%23ffffff#gh-dark-mode-only" height="15" align="center" /><img src="https://api.iconify.design/fa6-solid/cloud-arrow-up.svg?color=%23121212#gh-light-mode-only" height="15" align="center" /> **Cloud Backup & Instant Sharing:** Optional export and compression presets optimized for messaging apps.

---

## <a id="-contributing"></a><img src="https://api.iconify.design/fa6-solid/handshake.svg?color=%23ffffff#gh-dark-mode-only" height="20" align="center" alt="Contributing" /><img src="https://api.iconify.design/fa6-solid/handshake.svg?color=%23121212#gh-light-mode-only" height="20" align="center" alt="Contributing" /> Contributing

Contributions are welcome! If you find bugs, have feature requests, or want to enhance ScreenX:
1. **Fork** the repository.
2. **Create a branch** for your feature/bug fix (`git checkout -b feature/amazing-feature`).
3. **Commit** your changes (`git commit -m 'Add amazing feature'`).
4. **Push** to the branch (`git push origin feature/amazing-feature`).
5. **Open a Pull Request**.

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for more details.

---

## <img src="https://api.iconify.design/fa6-solid/chart-line.svg?color=%23ffffff#gh-dark-mode-only" height="20" align="center" alt="Stars" /><img src="https://api.iconify.design/fa6-solid/chart-line.svg?color=%23121212#gh-light-mode-only" height="20" align="center" alt="Stars" /> Star History

[![Star History Chart](https://api.star-history.com/svg?repos=gtxprime/screen-x&type=Date)](https://star-history.com/#gtxprime/screen-x&Date)

---

## <img src="https://api.iconify.design/fa6-solid/scale-balanced.svg?color=%23ffffff#gh-dark-mode-only" height="20" align="center" alt="License" /><img src="https://api.iconify.design/fa6-solid/scale-balanced.svg?color=%23121212#gh-light-mode-only" height="20" align="center" alt="License" /> License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
