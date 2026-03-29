# YouTube Audio Downloader - Android App

## What Is This
A native Android app (Kotlin + Jetpack Compose) that downloads audio from YouTube videos. Users can paste individual video URLs or subscribe to channels for automatic new-video discovery every 6 hours. Downloaded audio plays in a built-in player with standard controls.

See [PLAN.md](PLAN.md) for the full implementation roadmap (Phases 0-7).
See [.claude/STATUS.md](.claude/STATUS.md) for current progress.

## Environment Setup

Every session MUST set these environment variables before running any build or SDK command:

```bash
export JAVA_HOME=/c/dev/jdk17/jdk-17.0.18+8
export ANDROID_HOME=/c/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

Tool locations (Windows, accessed via Git Bash):
- JDK 17.0.18: `/c/dev/jdk17/jdk-17.0.18+8`
- Android SDK: `/c/android-sdk`
- ADB: `/c/android-sdk/platform-tools/adb.exe`
- SDK Manager: `/c/android-sdk/cmdline-tools/latest/bin/sdkmanager.bat`
- AVD Manager: `/c/android-sdk/cmdline-tools/latest/bin/avdmanager.bat`
- Emulator: `/c/android-sdk/emulator/emulator.exe`
- AVD name: `test_device` (Pixel 6, Android 14, x86_64)

**Important**: SDK tools are `.bat` files. Call them with full path (e.g., `/c/android-sdk/cmdline-tools/latest/bin/sdkmanager.bat`). `choco` requires admin and often fails - prefer direct downloads.

## Build & Test Commands

All commands assume env vars above are set and working directory is `/c/app`.

```bash
# Build
./gradlew assembleDebug

# Unit tests (JVM, no emulator needed)
./gradlew test

# Start emulator (headless, for testing)
/c/android-sdk/emulator/emulator.exe -avd test_device -no-window -no-audio -gpu swiftshader_indirect &
/c/android-sdk/platform-tools/adb.exe wait-for-device

# Install on emulator
./gradlew installDebug

# Instrumented tests (requires running emulator)
./gradlew connectedAndroidTest

# Launch app on emulator
/c/android-sdk/platform-tools/adb.exe shell am start -n com.ytaudio.app/.MainActivity

# Screenshot from emulator
/c/android-sdk/platform-tools/adb.exe exec-out screencap -p > /tmp/screenshot.png

# Check logs
/c/android-sdk/platform-tools/adb.exe logcat -d -s "YTAudio" | tail -50
```

## Architecture Decisions (and WHY)

| Decision | Why |
|----------|-----|
| Native Kotlin (not Flutter/React Native) | Single platform focus, direct Android API access for MediaStore/WorkManager/ExoPlayer, no JS bridge overhead, simplest maintenance for solo developer |
| NewPipe Extractor (not yt-dlp) | Pure Java library, runs natively on Android without Python runtime, used by established apps (NewPipe, Seal), ~5MB vs ~50MB |
| Hilt for DI | Official Android recommendation, integrates with WorkManager and Compose navigation |
| Room for DB | Reactive queries via Flow, compile-time SQL verification, standard Android choice |
| MediaStore API (not direct file access) | Required for Android 11+ scoped storage, no MANAGE_EXTERNAL_STORAGE permission needed, files visible in other music players |
| WorkManager for background checks | Only approved method for periodic background work on modern Android, survives reboots, respects battery optimization |
| Media3/ExoPlayer for playback | Official Google library, supports MediaSession (lock screen controls, notifications), background playback |
| New channel videos are listed, NOT auto-downloaded | User chose this explicitly - they want to pick which videos to download |

## Project Structure

```
c:\app\
  CLAUDE.md              ← you are here
  PLAN.md                ← implementation roadmap
  .claude/STATUS.md      ← current progress (update after each phase)
  build.gradle.kts       ← root build file
  settings.gradle.kts    ← project settings
  gradle.properties      ← JVM args
  local.properties       ← sdk.dir (gitignored)
  app/
    build.gradle.kts     ← app dependencies
    src/main/
      AndroidManifest.xml
      java/com/ytaudio/app/
        YTAudioApp.kt              ← Application class (Hilt + WorkManager setup)
        MainActivity.kt            ← Single-activity Compose host
        di/                        ← Hilt modules
        data/local/                ← Room entities, DAOs, database
        data/remote/               ← NewPipe Extractor wrapper
        data/repository/           ← Repository pattern
        service/                   ← Download + Playback services
        worker/                    ← WorkManager periodic tasks
        ui/                        ← Compose screens, navigation, theme
    src/test/                      ← JVM unit tests
    src/androidTest/               ← Instrumented tests (run on emulator)
```

## How To Work In This Project

### Git Commits
- Commit after each meaningful unit of work (not just per-phase, but per logical change)
- Write detailed commit messages explaining WHAT changed and WHY
- First line: concise summary (imperative mood, <72 chars)
- Body: explain the reasoning, not just list files
- Example:
  ```
  Add Room database with Channel and Video entities

  Channel tracks YouTube channel subscriptions with lastCheckedAt
  for the 6-hour periodic check. Video tracks individual videos
  with download status (NONE/DOWNLOADING/COMPLETED/FAILED) and
  local file path for MediaStore integration.

  Room was chosen over raw SQLite for compile-time query verification
  and reactive Flow-based queries that integrate with Compose.
  ```

### Testing Loop
After writing code, always:
1. `./gradlew assembleDebug` - verify it compiles
2. `./gradlew test` - run unit tests
3. If UI changes: install on emulator and take screenshot
4. Check `adb logcat` for runtime errors
5. Fix any issues before moving to next task

### Resuming Work
When starting a new session:
1. Read this file (CLAUDE.md) - loaded automatically
2. Read `.claude/STATUS.md` to see current progress
3. Read `PLAN.md` for the full roadmap
4. Run `git log --oneline -20` to see recent work
5. Set environment variables (see "Environment Setup" above)
6. Continue from the next incomplete phase

## Known Pitfalls
- `choco install` fails without admin - use direct downloads instead
- Android SDK tools are `.bat` files, not executables - use full path with `.bat` extension
- `winget` prompts for interactive license acceptance - avoid it
- NewPipe Extractor requires initializing with a custom OkHttp-based `Downloader` implementation
- YouTube audio stream URLs expire quickly - extract immediately before downloading, never cache
- The shell is Git Bash on Windows - use Unix paths (`/c/app` not `C:\app`) in commands
