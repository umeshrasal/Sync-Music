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
        private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        private const val PLAYER_ENDPOINT = "https://music.youtube.com/youtubei/v1/player?key=$INNERTUBE_API_KEY"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val PREFERRED_ITAGS = listOf(
            251, // Opus 160kbps (highest dynamic audio fidelity)
            140, // AAC 128kbps (high compatibility)
            250, // Opus 70kbps
            249  // Opus 50kbps
        )

        private val FALLBACK_MIRRORS = listOf(
            "https://yt.chocolatemoo53.com",
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de"
        )
    }

    /**
     * Resolves the optimal direct audio stream, with Innertube primary and resilient fallback mirrors.
     */
    suspend fun resolveStream(videoId: String): Result<StreamResolution> = withContext(Dispatchers.IO) {
        // Attempt 1: Direct Innertube Player API
        val directResult = tryResolveDirectInnertube(videoId)
        if (directResult.isSuccess) {
            return@withContext directResult
        }

        // Attempt 2: Resilient Stream Mirror Resolver
        for (mirror in FALLBACK_MIRRORS) {
            val fallbackResult = tryResolveFromMirror(mirror, videoId)
            if (fallbackResult.isSuccess) {
                return@withContext fallbackResult
            }
        }

        Result.failure(directResult.exceptionOrNull() ?: Exception("Unable to resolve audio stream for $videoId"))
    }

    private fun tryResolveDirectInnertube(videoId: String): Result<StreamResolution> {
        return try {
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
                return Result.failure(Exception("HTTP ${response.code}"))
            }

            val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty player response"))
            val json = JSONObject(responseBody)

            val playabilityStatus = json.optJSONObject("playabilityStatus")
            val status = playabilityStatus?.optString("status")
            if (status != "OK") {
                return Result.failure(Exception("Innertube status: $status"))
            }

            val playerConfig = json.optJSONObject("playerConfig")
            val loudnessDb = playerConfig?.optJSONObject("audioConfig")?.optDouble("loudnessDb")?.toFloat()

            val streamingData = json.optJSONObject("streamingData") ?: return Result.failure(Exception("No streaming data"))
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return Result.failure(Exception("No adaptive formats"))

            extractBestAudioFormat(adaptiveFormats, loudnessDb)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun tryResolveFromMirror(mirrorUrl: String, videoId: String): Result<StreamResolution> {
        return try {
            val url = "$mirrorUrl/api/v1/videos/$videoId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SyncMusicAndroid/1.0.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Mirror $mirrorUrl HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty mirror body"))
            val json = JSONObject(body)

            val adaptiveFormats = json.optJSONArray("adaptiveFormats") ?: return Result.failure(Exception("No adaptive formats on mirror"))

            for (preferredItag in PREFERRED_ITAGS) {
                for (i in 0 until adaptiveFormats.length()) {
                    val format = adaptiveFormats.getJSONObject(i)
                    val itag = format.optInt("itag")
                    val type = format.optString("type")
                    val streamUrl = format.optString("url")

                    if (itag == preferredItag && type.startsWith("audio/") && streamUrl.isNotEmpty()) {
                        val bitrate = format.optInt("bitrate", 160000)
                        val expiresAt = parseExpiryFromUrl(streamUrl)
                        return Result.success(
                            StreamResolution(
                                streamUrl = streamUrl,
                                itag = itag,
                                mimeType = type.substringBefore(";"),
                                bitrate = bitrate,
                                loudnessDb = null,
                                expiresAtEpochMs = expiresAt
                            )
                        )
                    }
                }
            }

            Result.failure(Exception("No preferred audio itags found on mirror"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractBestAudioFormat(adaptiveFormats: org.json.JSONArray, loudnessDb: Float?): Result<StreamResolution> {
        var selectedUrl: String? = null
        var selectedItag = 0
        var selectedMimeType = "audio/webm"
        var selectedBitrate = 0

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
                        selectedMimeType = mimeType.substringBefore(";")
                        selectedBitrate = format.optInt("bitrate", 160000)
                        break
                    }
                }
            }
            if (selectedUrl != null) break
        }

        return if (selectedUrl != null) {
            Result.success(
                StreamResolution(
                    streamUrl = selectedUrl,
                    itag = selectedItag,
                    mimeType = selectedMimeType,
                    bitrate = selectedBitrate,
                    loudnessDb = loudnessDb,
                    expiresAtEpochMs = parseExpiryFromUrl(selectedUrl)
                )
            )
        } else {
            Result.failure(Exception("No suitable audio streams found"))
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
                System.currentTimeMillis() + 6 * 3600 * 1000L
            }
        } catch (_: Exception) {
            System.currentTimeMillis() + 6 * 3600 * 1000L
        }
    }
}
