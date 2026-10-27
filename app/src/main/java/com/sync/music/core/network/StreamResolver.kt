package com.sync.music.core.network

import com.sync.music.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class StreamResolution(
    val streamUrl: String,
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val loudnessDb: Float?,
    val expiresAtEpochMs: Long
)

@Singleton
class StreamResolver @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqAE4An4q7CH7Il3ndWZ_g64e"
        private const val PLAYER_ENDPOINT = "https://music.youtube.com/youtubei/v1/player?key=$INNERTUBE_API_KEY"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val PREFERRED_ITAGS = listOf(
            251, // Opus 160kbps (highest dynamic audio fidelity)
            140, // AAC 128kbps (high compatibility)
            250, // Opus 70kbps
            249  // Opus 50kbps
        )
    }

    /**
     * Resolves the optimal direct audio stream from the Innertube player endpoint.
     */
    suspend fun resolveStream(videoId: String): Result<StreamResolution> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_MUSIC")
                        put("clientVersion", "6.20.51")
                        put("androidSdkVersion", 34)
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("signatureTimestamp", 19800)
                    })
                })
            }

            val request = Request.Builder()
                .url(PLAYER_ENDPOINT)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "com.google.android.apps.youtube.music/6.20.51 (Linux; U; Android 14; en_US; Pixel 8)")
                .header("Content-Type", "application/json")
                .header("X-YouTube-Client-Name", "21")
                .header("X-YouTube-Client-Version", "6.20.51")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Stream resolution HTTP error: ${response.code}"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty player response"))
            val json = JSONObject(responseBody)

            val playabilityStatus = json.optJSONObject("playabilityStatus")
            val status = playabilityStatus?.optString("status")
            if (status != "OK") {
                val reason = playabilityStatus?.optString("reason", "Track unplayable")
                return@withContext Result.failure(Exception("Track unplayable: $reason"))
            }

            // Loudness DB extraction for ReplayGain normalization
            val playerConfig = json.optJSONObject("playerConfig")
            val audioConfig = playerConfig?.optJSONObject("audioConfig")
            val loudnessDb = audioConfig?.optDouble("loudnessDb")?.toFloat()

            // Streaming data extraction
            val streamingData = json.optJSONObject("streamingData") ?: return@withContext Result.failure(Exception("No streaming data"))
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return@withContext Result.failure(Exception("No adaptive formats"))

            var selectedUrl: String? = null
            var selectedItag: Int = 0
            var selectedMimeType: String = "audio/webm"
            var selectedBitrate: Int = 0

            // Iterate according to priority itags
            for (preferredItag in PREFERRED_ITAGS) {
                for (i in 0 until adaptiveFormats.length()) {
                    val format = adaptiveFormats.getJSONObject(i)
                    val itag = format.optInt("itag")
                    val mimeType = format.optString("mimeType")

                    if (itag == preferredItag && mimeType.startsWith("audio/")) {
                        var url = format.optString("url")
                        if (url.isEmpty() && format.has("cipher")) {
                            url = parseCipher(format.getString("cipher"))
                        } else if (url.isEmpty() && format.has("signatureCipher")) {
                            url = parseCipher(format.getString("signatureCipher"))
                        }

                        if (url.isNotEmpty()) {
                            selectedUrl = url
                            selectedItag = itag
                            selectedMimeType = mimeType
                            selectedBitrate = format.optInt("bitrate", 160000)
                            break
                        }
                    }
                }
                if (selectedUrl != null) break
            }

            if (selectedUrl == null) {
                return@withContext Result.failure(Exception("No suitable audio streams found"))
            }

            // Parse URL expiration parameter ("expire")
            val expiresAtEpochMs = parseExpiryFromUrl(selectedUrl)

            Result.success(
                StreamResolution(
                    streamUrl = selectedUrl,
                    itag = selectedItag,
                    mimeType = selectedMimeType,
                    bitrate = selectedBitrate,
                    loudnessDb = loudnessDb,
                    expiresAtEpochMs = expiresAtEpochMs
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCipher(cipher: String): String {
        val params = cipher.split("&").associate {
            val parts = it.split("=")
            if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else parts[0] to ""
        }
        val url = params["url"] ?: return ""
        val sig = params["s"] ?: params["sig"]
        return if (sig != null) "$url&sig=$sig" else url
    }

    private fun parseExpiryFromUrl(url: String): Long {
        return try {
            val uri = android.net.Uri.parse(url)
            val expireParam = uri.getQueryParameter("expire")
            if (expireParam != null) {
                expireParam.toLong() * 1000L
            } else {
                System.currentTimeMillis() + 6 * 3600 * 1000L // 6 hour fallback
            }
        } catch (e: Exception) {
            System.currentTimeMillis() + 6 * 3600 * 1000L
        }
    }
}
