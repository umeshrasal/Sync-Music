package com.sync.music.core.model

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Track(
    val id: String,
    val title: String = "",
    val artist: String = "Unknown Artist",
    val album: String? = null,
    val durationMs: Long = 0L,
    val thumbnailUrl: String = "",
    val streamUrl: String? = null,
    val loudnessDb: Float? = null,
    val isOffline: Boolean = false,
    val offlineUri: String? = null,
    val explicit: Boolean = false
) {
    fun toMediaItem(): MediaItem {
        val uri = if (isOffline && !offlineUri.isNullOrEmpty()) {
            Uri.parse(offlineUri)
        } else {
            Uri.parse(streamUrl ?: "")
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(if (thumbnailUrl.isNotEmpty()) Uri.parse(thumbnailUrl) else null)
            .setIsPlayable(true)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }
}
