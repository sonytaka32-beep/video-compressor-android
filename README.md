# Android FFmpeg Video Compressor

A single-screen Material 3 Android video compressor built with **Kotlin**, **Coroutines/Flow**, **MVVM**, **DataStore Preferences**, **Storage Access Framework (SAF)**, and **`com.arthenica:mobile-ffmpeg-full:4.4.3.LTS`**.

## Highlights

- **Zero Storage Permissions**: Uses `ACTION_OPEN_DOCUMENT` (multi-file selection) and `ACTION_OPEN_DOCUMENT_TREE` (output directory) with `takePersistableUriPermission`.
- **Sequential Queue Execution**: Processes videos one at a time to prevent CPU/SoC thermal throttling.
- **Persistence Across Restarts**: Persists the queue, selected compression preset, and SAF output directory URI via Jetpack DataStore Preferences.
- **Exact FFmpeg Presets**:
  1. **Perceptually Lossless HEVC (Default)**
     `-c:v libx265 -preset medium -crf 24 -tag:v hvc1 -c:a copy -movflags +faststart`
  2. **Maximum Quality HEVC (Near-Lossless)**
     `-c:v libx265 -preset slow -crf 18 -tag:v hvc1 -c:a copy -movflags +faststart`
  3. **Maximum Compression AV1**
     `-c:v libsvtav1 -preset 6 -crf 30 -c:a copy -movflags +faststart`
  4. **Fast 720p HEVC (Sharing)**
     `-vf scale=-2:720 -c:v libx265 -preset fast -crf 28 -tag:v hvc1 -c:a aac -b:a 128k -movflags +faststart`

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