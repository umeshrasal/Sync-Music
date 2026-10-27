package com.sync.music.core.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import com.sync.music.MainActivity
import com.sync.music.R
import com.sync.music.core.database.dao.TrackDao
import com.sync.music.core.download.OfflineDownloadManager
import com.sync.music.core.network.StreamResolver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import kotlin.math.pow

@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var streamResolver: StreamResolver
    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var offlineDownloadManager: OfflineDownloadManager

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "sync_music_playback_channel"
        const val NOTIFICATION_ID = 1001

        private const val MAX_CACHE_SIZE_BYTES = 512L * 1024L * 1024L // 512MB RAM/Disk Audio Cache
        private var simpleCacheInstance: SimpleCache? = null

        @Synchronized
        fun getCache(context: Context): SimpleCache {
            if (simpleCacheInstance == null) {
                val cacheDir = File(context.cacheDir, "media3_audio_cache")
                val databaseProvider = StandaloneDatabaseProvider(context)
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
                simpleCacheInstance = SimpleCache(cacheDir, evictor, databaseProvider)
            }
            return simpleCacheInstance!!
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 1. Initialize High-Performance Chunk Cache & Custom DataSource
        val cache = getCache(this)
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("SyncMusicPlayer/1.0.0 (Linux; Android)")

        val upstreamFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        // ResolvingDataSource automatically resolves expired YouTube streams or maps to offline encrypted cache
        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            upstreamFactory,
            ResolvingDataSource.Resolver { dataSpec ->
                val uri = dataSpec.uri
                val scheme = uri.scheme

                // Check for local encrypted offline playback scheme
                if (scheme == "sync-offline") {
                    return@Resolver dataSpec
                }

                // Check if file is available in offline database
                val trackId = uri.getQueryParameter("video_id") ?: uri.lastPathSegment ?: ""
                val offlineRecord = runBlocking { trackDao.getOfflineCache(trackId) }
                if (offlineRecord != null && File(offlineRecord.localFilePath).exists()) {
                    return@Resolver dataSpec.buildUpon()
                        .setUri(android.net.Uri.parse("sync-offline://$trackId"))
                        .build()
                }

                // Check stream cache validity in database
                val now = System.currentTimeMillis()
                val cached = runBlocking { trackDao.getValidStreamCache(trackId, now + 60_000) }
                if (cached != null && !cached.cachedStreamUrl.isNullOrEmpty()) {
                    return@Resolver dataSpec.buildUpon()
                        .setUri(android.net.Uri.parse(cached.cachedStreamUrl))
                        .build()
                }

                // Fresh network resolution if expired
                val resolved = runBlocking { streamResolver.resolveStream(trackId) }
                if (resolved.isSuccess) {
                    val streamInfo = resolved.getOrThrow()
                    runBlocking {
                        val track = trackDao.getTrackById(trackId)
                        if (track != null) {
                            trackDao.insertOrUpdateTrack(
                                track.copy(
                                    cachedStreamUrl = streamInfo.streamUrl,
                                    streamExpiresAt = streamInfo.expiresAtEpochMs,
                                    loudnessDb = streamInfo.loudnessDb
                                )
                            )
                        }
                    }
                    dataSpec.buildUpon()
                        .setUri(android.net.Uri.parse(streamInfo.streamUrl))
                        .build()
                } else {
                    dataSpec
                }
            }
        )

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // 2. Configure Gapless LoadControl with optimized buffers
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableAudioTrackPlaybackParams(true)
        }

        // 3. Build ExoPlayer instance
        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true) // Automatic audio focus handling
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // 4. Loudness Normalization Listener (ReplayGain / Perceptual Loudness)
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (mediaItem == null) return

                val videoId = mediaItem.mediaId
                serviceScope.launch {
                    val track = trackDao.getTrackById(videoId)
                    val loudnessDb = track?.loudnessDb
                    if (loudnessDb != null) {
                        // Normalize against standard -14 LUFS baseline
                        // gain = 10^((targetLufs - loudnessDb) / 20)
                        val targetLufs = -14.0f
                        val gainDb = (targetLufs - loudnessDb).coerceIn(-10.0f, 6.0f)
                        val linearVolume = 10.0.pow(gainDb / 20.0).toFloat().coerceIn(0.2f, 1.0f)
                        exoPlayer.volume = linearVolume
                    } else {
                        exoPlayer.volume = 1.0f
                    }
                }
            }
        })

        // 5. Build MediaSession with pending activity intent
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(SyncMediaSessionCallback())
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class SyncMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand("ACTION_TOGGLE_LIKE", android.os.Bundle.EMPTY))
                .build()

            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle
        ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
            if (customCommand.customAction == "ACTION_TOGGLE_LIKE") {
                // Handle optimistic like toggle
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
            )
        }
    }
}
