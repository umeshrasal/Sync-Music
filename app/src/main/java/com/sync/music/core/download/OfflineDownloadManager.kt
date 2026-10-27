package com.sync.music.core.download

import android.content.Context
import com.sync.music.core.database.dao.TrackDao
import com.sync.music.core.database.entity.OfflineCacheEntity
import com.sync.music.core.database.entity.TrackEntity
import com.sync.music.core.model.Track
import com.sync.music.core.network.StreamResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val trackId: String, val progressFraction: Float) : DownloadState()
    data class Completed(val trackId: String) : DownloadState()
    data class Error(val trackId: String, val message: String) : DownloadState()
}

@Singleton
class OfflineDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val streamResolver: StreamResolver,
    private val trackDao: TrackDao
) {
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val vaultDir: File by lazy {
        File(context.filesDir, "offline_vault").apply {
            if (!exists()) mkdirs()
        }
    }

    // Static master key derivation for local storage (AES-256)
    private val masterKey: SecretKey by lazy {
        val prefs = context.getSharedPreferences("sync_vault_security", Context.MODE_PRIVATE)
        val storedKey = prefs.getString("vault_key_b64", null)
        if (storedKey != null) {
            val keyBytes = android.util.Base64.decode(storedKey, android.util.Base64.DEFAULT)
            SecretKeySpec(keyBytes, "AES")
        } else {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val newKey = keyGen.generateKey()
            val b64 = android.util.Base64.encodeToString(newKey.encoded, android.util.Base64.DEFAULT)
            prefs.edit().putString("vault_key_b64", b64).apply()
            newKey
        }
    }

    /**
     * Downloads and stores an audio track using AES-256-GCM encryption with SHA-256 checksum verification.
     */
    suspend fun downloadTrack(track: Track): Result<Unit> = withContext(Dispatchers.IO) {
        _downloadState.value = DownloadState.Downloading(track.id, 0f)

        try {
            // 1. Resolve direct audio stream
            val streamResult = streamResolver.resolveStream(track.id)
            if (streamResult.isFailure) {
                val errorMsg = streamResult.exceptionOrNull()?.message ?: "Failed to resolve stream"
                _downloadState.value = DownloadState.Error(track.id, errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }
            val stream = streamResult.getOrThrow()

            // 2. Fetch the audio stream
            val request = Request.Builder().url(stream.streamUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val msg = "Download HTTP failed with code ${response.code}"
                _downloadState.value = DownloadState.Error(track.id, msg)
                return@withContext Result.failure(Exception(msg))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty download body"))
            val contentLength = body.contentLength()
            val inputStream: InputStream = body.byteStream()

            // 3. Prepare AES-GCM cipher
            val iv = ByteArray(12) // 96-bit IV for GCM
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, GCMParameterSpec(128, iv))

            val encryptedFile = File(vaultDir, "${track.id}.sync")
            val outputStream = FileOutputStream(encryptedFile)
            val cipherOutputStream = CipherOutputStream(outputStream, cipher)

            val sha256Digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                cipherOutputStream.write(buffer, 0, bytesRead)
                sha256Digest.update(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (contentLength > 0) {
                    val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                    _downloadState.value = DownloadState.Downloading(track.id, progress)
                }
            }

            cipherOutputStream.flush()
            cipherOutputStream.close()
            inputStream.close()

            val checksumHex = sha256Digest.digest().joinToString("") { "%02x".format(it) }
            val ivBase64 = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)

            // 4. Save metadata to Room database
            trackDao.insertOrUpdateTrack(
                TrackEntity.fromTrack(
                    track.copy(loudnessDb = stream.loudnessDb),
                    streamExpiresAt = stream.expiresAtEpochMs
                )
            )

            trackDao.insertOfflineCache(
                OfflineCacheEntity(
                    trackId = track.id,
                    localFilePath = encryptedFile.absolutePath,
                    ivBase64 = ivBase64,
                    fileSizeBytes = encryptedFile.length(),
                    checksumSha256 = checksumHex,
                    mimeType = stream.mimeType
                )
            )

            _downloadState.value = DownloadState.Completed(track.id)
            Result.success(Unit)
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error(track.id, e.message ?: "Download failed")
            Result.failure(e)
        }
    }

    /**
     * Returns the master AES key for decrypting offline streams during playback.
     */
    fun getMasterKey(): SecretKey = masterKey

    /**
     * Removes an offline track from disk and database.
     */
    suspend fun removeOfflineTrack(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val record = trackDao.getOfflineCache(trackId) ?: return@withContext false
        val file = File(record.localFilePath)
        if (file.exists()) {
            file.delete()
        }
        trackDao.deleteOfflineCache(trackId)
        true
    }
}
