package com.sync.music.core.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.sync.music.core.database.dao.TrackDao
import com.sync.music.core.download.OfflineDownloadManager
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec

class EncryptedAesDataSource(
    private val offlineDownloadManager: OfflineDownloadManager,
    private val trackDao: TrackDao
) : BaseDataSource(true) {

    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        val trackId = uri?.getQueryParameter("trackId") ?: uri?.lastPathSegment?.substringBefore(".") ?: ""
        val offlineRecord = runBlocking { trackDao.getOfflineCache(trackId) }
            ?: throw IllegalStateException("No offline cache found for trackId: $trackId")

        val file = File(offlineRecord.localFilePath)
        if (!file.exists()) {
            throw IllegalStateException("Encrypted audio file does not exist: ${offlineRecord.localFilePath}")
        }

        val iv = android.util.Base64.decode(offlineRecord.ivBase64, android.util.Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, offlineDownloadManager.getMasterKey(), GCMParameterSpec(128, iv))

        val fileIn = FileInputStream(file)
        val cipherIn = CipherInputStream(fileIn, cipher)

        // Read all into memory or buffered stream for gapless random seeking
        val decryptedBytes = cipherIn.readBytes()
        cipherIn.close()

        val length = decryptedBytes.size.toLong()
        val offset = dataSpec.position
        val remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            length - offset
        }

        val bais = ByteArrayInputStream(decryptedBytes)
        bais.skip(offset)
        inputStream = bais
        bytesRemaining = remaining

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            bytesRemaining.coerceAtMost(length.toLong()).toInt()
        }

        val bytesRead = inputStream?.read(buffer, offset, bytesToRead) ?: -1
        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }

        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        if (opened) {
            opened = false
            try {
                inputStream?.close()
            } finally {
                inputStream = null
                transferEnded()
            }
        }
    }
}
