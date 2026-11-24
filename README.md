# CINEMA TV — Adaptive Android Phone & Android TV Streaming Application

**Package:** `com.cinematv.app`  
**Minimum Android:** Android 8.0 (API 26) · **Target SDK:** Android 15 (API 35)  
**Targets:** Android Smartphones, Tablets, Android TV, Google TV, Android TV Boxes (single adaptive APK)

## Key Features

- **Dual Adaptive UI Modes:** Automatically detects Android TV (`UI_MODE_TYPE_TELEVISION` / `FEATURE_LEANBACK`) to display a 10-foot D-pad focusable NavigationRail and horizontal poster rows, while presenting a dedicated touch-first bottom-bar mobile layout on phones and tablets.
- **Arabic Subtitle Engine (Highest Priority):** Default subtitle preference is Arabic (`ar` → `en` → `ask`). Full UTF-8 & Windows-1256 fallback decoding, SRT / WebVTT / ASS / SSA parser, Unicode BiDi Right-to-Left Isolate (`\u2067...\u2069`) for connected Arabic letters, mixed Arabic/English sentences, Arabic & Latin numerals, punctuation, live sync offset adjustment, and subtitle style customization.
- **AndroidX Media3 ExoPlayer:** Supports HLS, MPEG-DASH, MP4, and device-supported MKV streams with hardware decoding, quality/audio/speed controls, resume/restart playback, and leak-free lifecycle management.
- **Modular Content Provider Architecture:** Clean `ContentProvider` interface with independent providers:
  1. **Jellyfin Media Server** (full REST authentication, connection verification, library/seasons/episodes, artwork, HLS/Direct streams, subtitles, progress sync).
  2. **Open Cinema (Public-Domain & Creative Commons)** (immediately playable Blender Open Movies with bundled Arabic & English subtitles).
  3. **Authorized Media Sources** (user-added licensed HLS/DASH/MP4 feeds).
  4. **Official Streaming Apps Directory** (deep-link handoff to official Android/TV apps).
- **TMDB Metadata & OpenSubtitles Discovery:** Official API clients cleanly separated from playback providers, storing user API keys in AES-256-GCM `EncryptedSharedPreferences`.
- **Developer Contact:** Direct clickable Instagram link to [@houjix_](https://www.instagram.com/houjix_/) with native Instagram app intent and browser fallback.

## Building the Debug APK

### Option 1: Automatic GitHub Actions Build (Recommended)
1. Push this repository to GitHub on branch `main`.
2. Open **Actions → CINEMA TV Android CI** (runs automatically on push/PR or manually via **Run workflow**).
3. Download the installable APK from **Artifacts → `cinema-tv-debug-apk`**.

> **Note on CI Workflow Actions:** `.github/workflows/android.yml` uses current stable marketplace versions (`checkout@v4`, `setup-java@v4`, `setup-android@v3`, `setup-gradle@v4`, `android-emulator-runner@v2`, `upload-artifact@v4`) and automatically bootstraps `gradle-wrapper.jar` via `gradle wrapper --gradle-version 8.11.1` if not already present in the checkout.

### Option 2: Local Build (Android Studio / Command Line)
Prerequisites: JDK 17 + Android SDK (API 35) + Gradle 8.11.1 (or Android Studio Ladybug+).
```bash
# 1. Bootstrap gradle-wrapper.jar once if absent
gradle wrapper --gradle-version 8.11.1

# 2. Run lint & unit tests
./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest

# 3. Build the installable Debug APK
./gradlew --no-daemon :app:assembleDebug
```
Output APK path: `app/build/outputs/apk/debug/app-debug.apk`

## Installation Instructions

### Android Smartphones & Tablets
1. Transfer `app-debug.apk` to your phone (or download it from GitHub Actions Artifacts).
2. Enable **Install unknown apps** for your file manager or browser in Android Settings.
3. Tap `app-debug.apk` to install, then open **CINEMA TV**.

### Android TV & Google TV Boxes
1. Enable **Developer Options → USB debugging** (or **Network debugging**) on your Android TV box.
2. Install via ADB from your computer:
   ```bash
   adb connect <TV_IP_ADDRESS>:5555
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Launch **CINEMA TV** directly from the Android TV Apps row (it registers `LEANBACK_LAUNCHER` and a 320×180 TV banner automatically).

## Security & Configuration
- Never commit `local.properties`, signing keystores, or API keys.
- Jellyfin tokens, TMDB API keys, and OpenSubtitles API keys are configured in-app via Settings and persisted in `EncryptedSharedPreferences`.
- Contact: [@houjix_](https://www.instagram.com/houjix_/)
