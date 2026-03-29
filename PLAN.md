# YouTube Audio Downloader - Android App

## Context
Build an Android app that downloads audio from YouTube videos and channels. The user wants to subscribe to YouTube channels (auto-check every 6 hours), download individual video URLs, play audio with standard controls (forward/back 10s), and store files on the phone. Claude must be able to test the app on an Android emulator during development.

## Tech Stack
- **Language**: Kotlin + Jetpack Compose (native Android)
- **YouTube extraction**: NewPipe Extractor (pure Java, no yt-dlp needed)
- **Audio playback**: Media3 / ExoPlayer with MediaSessionService
- **Database**: Room (channels, videos, download status)
- **Background tasks**: WorkManager (6-hour periodic channel checks)
- **DI**: Hilt
- **File storage**: MediaStore API (Music/YouTubeDownloads/)
- **Downloads**: OkHttp streaming + Foreground Service

---

## Phase 0: Environment Setup
Set up JDK, Android SDK, and emulator on Windows 11 (no Android Studio needed).

1. Install JDK 17 via `choco install temurin17 -y`
2. Download Android command-line tools to `C:\android-sdk\`
3. Install SDK packages: `platform-tools`, `platforms;android-34`, `build-tools;34.0.0`, `system-images;android-34;google_apis;x86_64`, `emulator`
4. Create AVD: `avdmanager create avd -n test_device -k "system-images;android-34;google_apis;x86_64" --device "pixel_6"`
5. Launch emulator headless: `emulator -avd test_device -no-window -no-audio -gpu swiftshader_indirect`
6. Verify: `adb devices` shows emulator

## Phase 1: Project Scaffolding
Generate the Gradle project structure manually in `c:\app\`.

**Key files:**
- `settings.gradle.kts` - project settings with JitPack repo
- `build.gradle.kts` - root build file (AGP 8.3.2, Kotlin 1.9.24, Hilt, KSP)
- `app/build.gradle.kts` - dependencies (NewPipe Extractor, Media3, Room, Hilt, WorkManager, OkHttp)
- `app/src/main/AndroidManifest.xml` - permissions (INTERNET, FOREGROUND_SERVICE, POST_NOTIFICATIONS)
- `gradle/wrapper/gradle-wrapper.properties` - Gradle 8.7

**Verify**: `./gradlew assembleDebug` succeeds

## Phase 2: Data Layer
- `app/src/main/java/com/ytaudio/app/data/local/entity/ChannelEntity.kt` - Room entity (id, name, url, lastCheckedAt)
- `app/src/main/java/com/ytaudio/app/data/local/entity/VideoEntity.kt` - Room entity (id, channelId, title, duration, downloadStatus, localFilePath)
- `app/src/main/java/com/ytaudio/app/data/local/dao/ChannelDao.kt` - CRUD + Flow queries
- `app/src/main/java/com/ytaudio/app/data/local/dao/VideoDao.kt` - CRUD + Flow queries
- `app/src/main/java/com/ytaudio/app/data/local/AppDatabase.kt` - Room database
- `app/src/main/java/com/ytaudio/app/data/remote/YouTubeExtractorService.kt` - NewPipe Extractor wrapper (extract audio URL, get channel videos, get video info). Requires custom OkHttp-based `Downloader` implementation for NewPipe.
- `app/src/main/java/com/ytaudio/app/data/repository/ChannelRepository.kt`
- `app/src/main/java/com/ytaudio/app/data/repository/VideoRepository.kt`

**Verify**: `./gradlew test` (unit tests with in-memory Room DB)

## Phase 3: Download Service
- `app/src/main/java/com/ytaudio/app/service/AudioDownloadService.kt` - Foreground service: extracts audio stream URL via NewPipe, downloads via OkHttp, saves to MediaStore `Music/YouTubeDownloads/`, updates Room DB
- `app/src/main/java/com/ytaudio/app/data/repository/DownloadRepository.kt` - orchestrates extraction + download

**Verify**: Install on emulator, download a video, check file exists via `adb shell`

## Phase 4: Audio Playback
- `app/src/main/java/com/ytaudio/app/service/AudioPlaybackService.kt` - Media3 MediaSessionService with ExoPlayer, background playback, lock screen controls
- `app/src/main/java/com/ytaudio/app/ui/player/PlayerViewModel.kt` - play, pause, seekForward10s, seekBack10s, position/duration flows
- `app/src/main/java/com/ytaudio/app/ui/player/PlayerBar.kt` - Mini player composable (bottom bar)
- `app/src/main/java/com/ytaudio/app/ui/player/PlayerScreen.kt` - Full screen player

**Verify**: Push test audio to emulator, play it, verify controls work

## Phase 5: UI Screens (Jetpack Compose)
- `app/src/main/java/com/ytaudio/app/ui/navigation/AppNavGraph.kt` - Bottom nav: Home, Channels, Downloads
- `app/src/main/java/com/ytaudio/app/ui/home/HomeScreen.kt` - Paste YouTube URL + download button
- `app/src/main/java/com/ytaudio/app/ui/channels/ChannelsScreen.kt` - List channels, refresh button per channel, FAB to add
- `app/src/main/java/com/ytaudio/app/ui/channels/AddChannelDialog.kt` - Input channel URL dialog
- `app/src/main/java/com/ytaudio/app/ui/downloads/DownloadsScreen.kt` - List downloaded audio, tap to play
- `app/src/main/java/com/ytaudio/app/ui/theme/Theme.kt` - Material 3 theming

**Verify**: `./gradlew installDebug`, take screenshot via `adb exec-out screencap -p > screenshot.png`

## Phase 6: Background Channel Checking
- `app/src/main/java/com/ytaudio/app/worker/ChannelCheckWorker.kt` - CoroutineWorker: fetches new videos for all channels, inserts into Room with `downloadStatus = NONE` (does NOT auto-download; user picks which to download manually)
- Schedule in `YTAudioApp.kt` Application class: `PeriodicWorkRequest` every 6 hours with network constraint
- Per-channel manual refresh: one-time WorkRequest from ChannelsViewModel
- New videos appear in the Downloads/Library screen with a download button next to each

**Verify**: Force-run worker via `adb shell cmd jobscheduler`, verify new videos appear in DB

## Phase 7: Integration Testing
- `./gradlew test` - JVM unit tests (repositories, ViewModels, worker)
- `./gradlew connectedAndroidTest` - Instrumented tests on emulator (UI navigation, download flow, playback)
- `adb logcat -s "YTAudio"` - Check for runtime errors

---

## Key Risks
1. **NewPipe Extractor breakage** - YouTube changes APIs frequently. Pin to v0.26.0, handle errors gracefully.
2. **Audio stream URL expiration** - Extract URL immediately before download, never cache.
3. **Emulator on Windows** - Need HAXM or Hyper-V enabled for x86_64 images.

## Permissions (AndroidManifest.xml)
- `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`
