package com.sync.music.core.network

import com.sync.music.core.model.MoodChip
import com.sync.music.core.model.RadioQueueResult
import com.sync.music.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqAE4An4q7CH7Il3ndWZ_g64e"
        private const val NEXT_ENDPOINT = "https://music.youtube.com/youtubei/v1/next?key=$INNERTUBE_API_KEY"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Resolves an infinite algorithmic radio queue using the YouTube seed RDAMVM<videoId>.
     */
    suspend fun getRadioQueue(
        seedVideoId: String,
        continuationToken: String? = null,
        chipParams: String? = null
    ): Result<RadioQueueResult> = withContext(Dispatchers.IO) {
        try {
            val radioPlaylistId = "RDAMVM$seedVideoId"

            val payload = JSONObject().apply {
                if (continuationToken != null) {
                    put("continuation", continuationToken)
                } else {
                    put("videoId", seedVideoId)
                    put("playlistId", radioPlaylistId)
                    put("isAudioOnly", true)
                    if (chipParams != null) {
                        put("params", chipParams)
                    }
                }

                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_MUSIC")
                        put("clientVersion", "6.20.51")
                        put("androidSdkVersion", 34)
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }

            val request = Request.Builder()
                .url(NEXT_ENDPOINT)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "com.google.android.apps.youtube.music/6.20.51 (Linux; U; Android 14; en_US; Pixel 8)")
                .header("Content-Type", "application/json")
                .header("X-YouTube-Client-Name", "21")
                .header("X-YouTube-Client-Version", "6.20.51")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Radio queue HTTP error: ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty next response"))
            val json = JSONObject(body)

            val tracks = mutableListOf<Track>()
            val chips = mutableListOf<MoodChip>()
            var nextContinuation: String? = null

            // Parse chips (e.g. "Familiar", "Discover", "Upbeat", "Chill")
            val chipCloud = json.optJSONObject("continuationContents")
                ?.optJSONObject("musicRadioContinuation")
                ?.optJSONObject("chipRenderer")
                ?: json.optJSONObject("contents")
                    ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                    ?.optJSONObject("tabbedRenderer")
                    ?.optJSONObject("watchNextTabbedResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("musicQueueRenderer")
                    ?.optJSONObject("subHeaderChips")
                    ?.optJSONObject("chipCloudRenderer")

            chipCloud?.optJSONArray("chips")?.let { chipArray ->
                for (i in 0 until chipArray.length()) {
                    val chipRenderer = chipArray.getJSONObject(i).optJSONObject("chipCloudChipRenderer") ?: continue
                    val text = chipRenderer.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                    val isSelected = chipRenderer.optBoolean("isSelected", false)
                    val params = chipRenderer.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("params") ?: ""

                    if (text.isNotEmpty()) {
                        chips.add(MoodChip(id = "chip_$i", title = text, params = params, isSelected = isSelected))
                    }
                }
            }

            // Parse tracks from queue contents
            val queueItems = json.optJSONObject("continuationContents")
                ?.optJSONObject("musicRadioContinuation")
                ?.optJSONArray("contents")
                ?: json.optJSONObject("contents")
                    ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                    ?.optJSONObject("tabbedRenderer")
                    ?.optJSONObject("watchNextTabbedResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("musicQueueRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("playlistPanelRenderer")
                    ?.optJSONArray("contents")

            if (queueItems != null) {
                for (i in 0 until queueItems.length()) {
                    val item = queueItems.getJSONObject(i)
                    val renderer = item.optJSONObject("playlistPanelVideoRenderer") ?: continue

                    val videoId = renderer.optString("videoId")
                    if (videoId.isEmpty()) continue

                    val title = renderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown Title"
                    val artist = renderer.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown Artist"
                    val album = renderer.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(2)?.optString("text")
                    val durationText = renderer.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "0:00"

                    val thumbnails = renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""

                    tracks.add(
                        Track(
                            id = videoId,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = parseDurationTextToMs(durationText),
                            thumbnailUrl = thumbUrl
                        )
                    )
                }
            }

            // Extract continuation token
            val continuations = json.optJSONObject("continuationContents")
                ?.optJSONObject("musicRadioContinuation")
                ?.optJSONArray("continuations")
                ?: json.optJSONObject("contents")
                    ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                    ?.optJSONObject("tabbedRenderer")
                    ?.optJSONObject("watchNextTabbedResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("musicQueueRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("playlistPanelRenderer")
                    ?.optJSONArray("continuations")

            nextContinuation = continuations?.optJSONObject(0)
                ?.optJSONObject("nextRadioContinuationData")
                ?.optString("continuation")
                ?: continuations?.optJSONObject(0)
                    ?.optJSONObject("nextContinuationData")
                    ?.optString("continuation")

            Result.success(
                RadioQueueResult(
                    tracks = tracks,
                    continuationToken = nextContinuation,
                    chips = chips
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDurationTextToMs(text: String): Long {
        val parts = text.split(":")
        return when (parts.size) {
            2 -> {
                val min = parts[0].toLongOrNull() ?: 0L
                val sec = parts[1].toLongOrNull() ?: 0L
                (min * 60 + sec) * 1000L
            }
            3 -> {
                val hr = parts[0].toLongOrNull() ?: 0L
                val min = parts[1].toLongOrNull() ?: 0L
                val sec = parts[2].toLongOrNull() ?: 0L
                (hr * 3600 + min * 60 + sec) * 1000L
            }
            else -> 0L
        }
    }
}
