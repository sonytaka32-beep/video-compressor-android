# Android FFmpeg Video Compressor

A single-screen Material 3 Android video compressor built with **Kotlin**, **Coroutines/Flow**, **MVVM**, **DataStore Preferences**, **Storage Access Framework (SAF)**, and **`com.github.arthenica:ffmpeg-kit-full-gpl:5.1.LTS`** (via JitPack).

## Highlights

- **Zero Storage Permissions**: Uses `ACTION_OPEN_DOCUMENT` (multi-file selection) and `ACTION_OPEN_DOCUMENT_TREE` (output directory) with `takePersistableUriPermission`.
- **Sequential Queue Execution**: Processes videos one at a time to prevent CPU/SoC thermal throttling.
- **Persistence Across Restarts**: Persists the queue, selected compression preset, and SAF output directory URI via Jetpack DataStore Preferences.
- **Hardware Acceleration (MediaCodec)**: Automatically detects and uses `hevc_mediacodec` on supported devices for 5-10x faster encoding, with software fallback.
- **Exact FFmpeg Presets** (5 options):

  1. **Perceptually Lossless HEVC (Default)**
     `-c:v libx265 -crf 23 -preset superfast -threads 0 -x265-params pools=+:frame-threads=4 -pix_fmt yuv420p -c:a copy -movflags +faststart`

  2. **Maximum Quality HEVC**
     `-c:v libx265 -crf 20 -preset veryfast -threads 0 -x265-params pools=+:frame-threads=4 -pix_fmt yuv420p -c:a copy -movflags +faststart`

  3. **Maximum Compression (Smallest Size, 30fps)**
     `-c:v libx265 -crf 30 -preset ultrafast -threads 0 -x265-params pools=+:frame-threads=4 -vf scale='min(1280,iw)':-2,fps=30 -pix_fmt yuv420p -c:a aac -b:a 96k -movflags +faststart`
     *(Hardware: `hevc_mediacodec -b:v 1800k -maxrate 2200k -bufsize 3600k`)*

  4. **Maximum Compression 60fps (Smallest Size, 60fps locked)**
     `-c:v libx265 -crf 30 -preset ultrafast -threads 0 -x265-params pools=+:frame-threads=4 -vf scale='min(1280,iw)':-2 -r 60 -pix_fmt yuv420p -c:a aac -b:a 96k -movflags +faststart`
     *(Hardware: `hevc_mediacodec -b:v 1800k -maxrate 2200k -bufsize 3600k -r 60`)*

  5. **Fast 720p HEVC (Sharing)**
     `-c:v libx265 -crf 26 -preset ultrafast -threads 0 -x265-params pools=+:frame-threads=4 -vf scale=-2:720 -pix_fmt yuv420p -c:a copy -movflags +faststart`
     *(Hardware: `hevc_mediacodec -b:v 1500k -maxrate 1800k -bufsize 3000k`)*

## Local Build Instructions

### Prerequisites
- JDK 17 (Temurin recommended)
- Android SDK Platform 34

### Build & Install

1. Generate the Gradle Wrapper (if initializing fresh from source files):
   ```bash
   gradle wrapper --gradle-version 8.7
   ```

2. Build the unsigned release APK:
   ```bash
   ./gradlew assembleRelease
   ```

   The output APK will be located at:
   `app/build/outputs/apk/release/app-release-unsigned.apk`

3. Build and install a debug build directly onto a connected device:
   ```bash
   ./gradlew installDebug
   ```

## GitHub Actions CI

Pushing to `main` or manually triggering the **Android Release APK CI** workflow via `workflow_dispatch` compiles `assembleRelease` on `ubuntu-latest` with JDK 17 and uploads `app-release-unsigned.apk` as a workflow artifact in under 10 minutes.

### CI Requirements
- JitPack repository configured in `settings.gradle.kts` (already present)
- `ffmpeg-kit-full-gpl:5.1.LTS` resolves from JitPack including native `.so` libraries