# CINEMA TV — Testing & Hardware Validation Documentation

## 1. Automated Test Suite

### Android Unit Tests (`./gradlew :app:testDebugUnitTest`)
- `ArabicSubtitleEngineTest.kt`: Verifies SRT, WebVTT, and ASS/SSA parsing, Unicode BiDi RTL isolation (`\u2067...\u2069`), connected Arabic characters, mixed Arabic/English sentences, Arabic & Latin numerals, punctuation preservation, multi-line cues, subtitle synchronization offset, Arabic-first language fallback, and explicit reporting of unsupported subtitle formats.
- `ProviderAndValidationTest.kt`: Verifies Jellyfin URL normalization/validation, watch-completion threshold enforcement (`>= 90%`), credential log redaction, and bilingual Arabic/English provider search.

### Android Instrumentation Tests (`./gradlew :app:connectedDebugAndroidTest`)
- `CinemaTvInstrumentationTest.kt`: Runs on API 35 emulator via `reactivecircus/android-emulator-runner@v2` in GitHub Actions. Verifies app launch, Arabic title rendering, navigation to Settings, live Arabic RTL subtitle preview, Movie details flow, and Instagram `@houjix_` contact control.

### Web & Pure-Logic Verification (`npx --yes tsx --test`)
- `src/utils/arabicSubtitles.test.ts` and `src/utils/mediaLogic.test.ts` verify all shared subtitle parsing, BiDi formatting, search, Jellyfin validation, and progress rules in Node.js.

## 2. Environment Honesty Statement
- **Verified in sandbox:** Complete Kotlin/Compose/Media3 Android Studio project files, GitHub Actions CI workflow, Web/TV interactive streaming application, `node:test` unit tests, and Playwright headless Chromium visual/DOM tests.
- **Requires JDK 17 + Android SDK / GitHub Actions runner:** Running `./gradlew :app:assembleDebug` and `:app:connectedDebugAndroidTest` to produce the compiled `.apk` binary and execute on an Android emulator.

## 3. Physical Android TV Hardware Validation Checklist
An Android phone emulator does NOT prove Android TV hardware behavior. Validate on a physical Android TV / Google TV device (or Android TV AVD):

- [ ] **TV Launcher Visibility:** App icon & 320×180 banner appear in the main Android TV Apps row (`LEANBACK_LAUNCHER`).
- [ ] **D-Pad Navigation:** Up / Down / Left / Right smoothly traverse left NavigationRail and horizontal poster rows with no dead focus traps.
- [ ] **Focus Indicators & Restoration:** Gold focus ring + scale highlight clearly indicate focused element; returning from Details restores focus predictably.
- [ ] **Remote Select & Back:** Center/Enter opens items and controls; Back exits Player → Details → Home cleanly without unexpected app termination.
- [ ] **Video Playback (1080p & 4K TV):** HLS, DASH, and MP4 streams decode using hardware decoders without frame drops or memory leaks across repeated open/close cycles.
- [ ] **Arabic Subtitle Rendering on TV:** Connected Arabic letters, RTL reading order, mixed Arabic/English, numerals, punctuation, and sync offset are legible from 10 feet away.
- [ ] **Instagram Contact on TV:** `@houjix_` card is focusable via D-pad and opens cleanly in the TV browser fallback when Instagram is not installed.
- [ ] **Low-RAM TV Box Stability:** Image disk/memory cache bounds and ExoPlayer release prevent OOM crashes on 1–2 GB RAM Android TV boxes.
