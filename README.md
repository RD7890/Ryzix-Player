<div align="center">
  <h1>🎬 Ryzix Player</h1>
  <p><strong>A powerful Android video player like MX Player, built with Kotlin & Media3/ExoPlayer</strong></p>

  <img src="https://img.shields.io/github/v/release/RD7890/Ryzix-Player?style=for-the-badge&color=6200EE" />
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin" />
  <img src="https://img.shields.io/badge/Min%20SDK-24-orange?style=for-the-badge" />
  <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" />
</div>

---

## ✨ Features

- 🎥 **Hardware-accelerated playback** via AndroidX Media3 / ExoPlayer
- 📁 **Auto-scan** all videos from internal & external storage, grouped by folder
- 👆 **Gesture controls** — swipe left for brightness, right for volume, double-tap to seek ±10 s
- 🔒 **Screen lock** — one tap locks all touch input
- 📝 **Subtitle support** — SRT / ASS / SSA with auto-detect sidecar files
- 🎵 **Audio track selector** — switch between embedded audio streams
- ⚡ **Playback speed** — 0.25× to 4× with fine control
- 📐 **Aspect ratio modes** — Fit, Fill, Stretch, Zoom, 16:9, 4:3
- 🖼️ **Picture-in-Picture** (Android 8+)
- 🔔 **Background playback** with Media Session notification
- 🕐 **Watch history** with last-position resume
- 🔍 **Search** across all videos
- 🌙 **Material You / Dynamic Color** theming
- 🌐 **Network stream** support (HTTP / RTSP / HLS / DASH)

## 📦 Requirements

| Tool | Version |
|------|---------|
| Android Studio | Ladybug+ |
| Gradle | 8.10.2 |
| AGP | 8.5.2 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 |

## 🚀 Building

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (needs signing config — see below)
./gradlew assembleRelease

# Run unit tests
./gradlew test
```

## 🔑 Release Signing (GitHub Actions)

Add these secrets to your repository (`Settings → Secrets → Actions`):

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

To generate a keystore:
```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias ryzix
base64 -i release.jks | pbcopy   # macOS — copies to clipboard
```

## 📦 Creating a Release

```bash
git tag v1.0.0
git push origin v1.0.0
```
GitHub Actions will automatically build the APK and create a release.

## 🏗️ Architecture

```
app/src/main/java/com/ryzix/player/
├── adapter/        RecyclerView adapters (VideoAdapter, FolderAdapter)
├── db/             Room database — watch history
├── model/          Data models (VideoItem, Folder)
├── service/        Background MediaSession service
├── ui/             Activities (Main, Player, Browser, Splash)
├── utils/          Media, gesture, preference utilities
└── viewmodel/      MVVM ViewModels
```

## 📄 License

MIT © 2024 RD7890
