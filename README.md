<div align="center">
  <h1>🎬 Ryzix Player</h1>
  <p><strong>A powerful Android video player like MX Player, built with Kotlin & Media3/ExoPlayer</strong></p>

  <a href="https://github.com/RD7890/Ryzix-Player/actions/workflows/ci.yml">
    <img src="https://github.com/RD7890/Ryzix-Player/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI" />
  </a>
  <img src="https://img.shields.io/github/v/release/RD7890/Ryzix-Player?style=flat-square&color=6200EE" />
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin" />
  <img src="https://img.shields.io/badge/Min%20SDK-24-orange?style=flat-square" />
  <img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" />
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

# Release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test
```

## 🔑 Signing

A stable PKCS12 keystore is committed at `app/signing/ryzix-signing.b64` (base64-encoded).  
CI decodes it at build time — **every build uses the same key**, so you never see  
"App not installed as package conflicts with an existing package" when sideloading updates.

The keystore credentials are:

| Field | Value |
|-------|-------|
| Alias | `ryzix-key` |
| Store password | `ryzix1234` |
| Key password | `ryzix1234` |

> For local builds the Gradle script auto-discovers `app/signing/ryzix-signing.p12` if present.
> Decode it once with: `base64 -d app/signing/ryzix-signing.b64 > app/signing/ryzix-signing.p12`

## 📦 Creating a Release

```bash
git tag v1.0.0
git push origin v1.0.0
```
GitHub Actions will automatically build the APKs and create a GitHub Release.

## 🤖 AI Build Log Integration

Every CI run automatically captures the full Gradle output and commits it to the `build-logs/` folder in this repo — **no manual log download ever needed**.

### Log file location & naming

```
build-logs/
└── run-<run_number>-<version_tag>.log   e.g. run-26-v0.0.26-beta.log
```

Each log file contains:
- Run number, version tag, date, commit SHA, and trigger event at the top
- Full `assembleDebug` Gradle output
- Full `assembleRelease` Gradle output

Logs are committed by `github-actions[bot]` at the end of every build, whether it succeeded or failed.

### How AI uses this

When a build fails, an AI agent (e.g. Replit Agent) can autonomously:

1. **Fetch the latest log** from `build-logs/` via the GitHub API — no file attachment needed
2. **Parse error lines** directly (e.g. `error: attribute X not found`, `cannot find symbol`)
3. **Apply targeted fixes** to the relevant source files
4. **Push a new commit** to re-trigger the CI pipeline

This creates a fully automated fix loop:
```
Build fails → Log committed to repo → AI reads log via API → AI fixes code → New build triggered
```

The AI only needs the repo URL and a PAT with `contents: write` — the rest is automatic.

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
