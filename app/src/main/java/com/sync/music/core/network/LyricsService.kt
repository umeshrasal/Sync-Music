package com.sync.music.core.network

import com.sync.music.core.model.TimedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    /**
     * Resolves synchronized LRC lyrics for a track given title, artist, and duration.
     */
    suspend fun getLyrics(
        videoId: String,
        trackName: String,
        artistName: String,
        durationSeconds: Long
    ): TimedLyrics = withContext(Dispatchers.IO) {
        // 1. Query LRCLIB primary API
        try {
            val qTrack = URLEncoder.encode(trackName, "UTF-8")
            val qArtist = URLEncoder.encode(artistName, "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=$qTrack&artist_name=$qArtist&duration=$durationSeconds"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SyncMusicAndroid/1.0.0 (https://github.com/sync-music)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val syncedLyrics = json.optString("syncedLyrics")
                    if (syncedLyrics.isNotEmpty()) {
                        return@withContext TimedLyrics.parseLrc(videoId, syncedLyrics)
                    }

                    val plainLyrics = json.optString("plainLyrics")
                    if (plainLyrics.isNotEmpty()) {
                        return@withContext TimedLyrics(
                            videoId = videoId,
                            lines = plainLyrics.lines().map { com.sync.music.core.model.LyricLine(0L, it) },
                            isSynced = false
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to fallback
        }

        // Return empty structure if not found
        TimedLyrics(
            videoId = videoId,
            lines = emptyList(),
            isSynced = false
        )
    }
}
