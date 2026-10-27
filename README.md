# Sync — High-Performance Native Android Music Client

Sync is a production-grade, native Android music streaming client built with **100% Kotlin**, **Jetpack Compose (Material Design 3)**, and **AndroidX Media3 (ExoPlayer)**.

---

## 🏛 Architecture Overview

```
                      ┌────────────────────────────────────────┐
                      │          Jetpack Compose UI            │
                      │  HomeScreen | MiniPlayer | FullPlayer  │
                      └───────────────────┬────────────────────┘
                                          │ StateFlow / Events
                                          ▼
                      ┌────────────────────────────────────────┐
                      │             MainViewModel              │
                      └──────────────┬──────────────────┬──────┘
                                     │                  │
               ┌─────────────────────▼───┐          ┌───▼─────────────────────┐
               │     PlayerController    │          │ OfflineDownloadManager  │
               └──────────────┬──────────┘          └───────────┬─────────────┘
                              │ MediaController                 │
                              ▼                                 │ AES-256 GCM
               ┌─────────────────────────┐                      ▼
               │ PlaybackService (Media3)│          ┌─────────────────────────┐
               │     + ExoPlayer Core    │◄─────────┤     Encrypted Vault     │
               └──────────────┬──────────┘          └─────────────────────────┘
                              │
          ┌───────────────────┴───────────────────┐
          │                                       │
          ▼                                       ▼
┌────────────────────────┐             ┌────────────────────────┐
│  StreamResolver (APIs) │             │  Room + SQLCipher DB   │
│  Auto Opus/AAC streams │             │  Offline Tracks & TTL  │
└────────────────────────┘             └────────────────────────┘
```

---

## 🚀 Key Modules & Capabilities

1. **YouTube Algorithmic Radio Engine (`RDAMVM<VIDEO_ID>`)**:
   - Seeds dynamic radio queues using YouTube's internal radio seed format.
   - Queries `/youtubei/v1/next` using client `ANDROID_MUSIC` to fetch identical ML-ranked tracks, mood chips, and continuation tokens.
   - Continuous autoplay appends suggested tracks seamlessly when approaching the end of the active queue.

2. **Media3 / ExoPlayer Pipeline (`PlaybackService.kt`)**:
   - `MediaSessionService` running as an Android persistent Foreground Service with `mediaPlayback` type.
   - Lock screen controls with seekbar, high-resolution artwork, and playback actions.
   - Zero video decoding overhead (reduces data footprint by >80%).
   - ReplayGain loudness normalization calculated against -14 LUFS standard from loudnessDb metadata.
   - `CacheDataSource` with 512MB LRU chunk caching to eliminate rebuffering.

3. **Encrypted Offline Storage (`OfflineDownloadManager.kt`)**:
   - AES-256-GCM encryption with 96-bit random IVs and SHA-256 checksum verification.
   - Custom `EncryptedAesDataSource` enabling ExoPlayer to read encrypted local tracks directly with seeking capabilities.

4. **Synchronized LRC Lyrics Engine (`LyricsService.kt`)**:
   - Auto-fetches and parses time-coded lyrics (`[mm:ss.xx]`).
   - Highlights and auto-scrolls the active lyric line synchronously with playback position.

---

## 📁 Source Directory Map

- **`core/model/`**: [`Track.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/model/Track.kt), [`MediaState.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/model/MediaState.kt), [`Lyrics.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/model/Lyrics.kt), [`Recommendation.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/model/Recommendation.kt)
- **`core/network/`**: [`RecommendationApi.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/network/RecommendationApi.kt), [`StreamResolver.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/network/StreamResolver.kt), [`LyricsService.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/network/LyricsService.kt)
- **`core/database/`**: [`SyncDatabase.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/database/SyncDatabase.kt), [`TrackDao.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/database/dao/TrackDao.kt), [`TrackEntity.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/database/entity/TrackEntity.kt), [`OfflineCacheEntity.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/database/entity/OfflineCacheEntity.kt)
- **`core/player/`**: [`PlaybackService.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/player/PlaybackService.kt), [`PlayerController.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/player/PlayerController.kt), [`QueueManager.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/player/QueueManager.kt), [`EncryptedAesDataSource.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/player/EncryptedAesDataSource.kt)
- **`core/download/`**: [`OfflineDownloadManager.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/core/download/OfflineDownloadManager.kt)
- **`ui/`**: [`HomeScreen.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/ui/home/HomeScreen.kt), [`MiniPlayer.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/ui/player/MiniPlayer.kt), [`FullPlayerSheet.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/ui/player/FullPlayerSheet.kt), [`MainViewModel.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/ui/MainViewModel.kt), [`MainActivity.kt`](file:///c:/Users/ELITEBOOK/Desktop/YouTube%20Rplica/app/src/main/java/com/sync/music/MainActivity.kt)
