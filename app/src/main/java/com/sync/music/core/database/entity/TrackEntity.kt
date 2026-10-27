package com.sync.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sync.music.core.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val thumbnailUrl: String,
    val cachedStreamUrl: String?,
    val streamExpiresAt: Long?,
    val loudnessDb: Float?,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toTrack(isOffline: Boolean = false, offlineUri: String? = null): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            thumbnailUrl = thumbnailUrl,
            streamUrl = cachedStreamUrl,
            loudnessDb = loudnessDb,
            isOffline = isOffline,
            offlineUri = offlineUri
        )
    }

    companion object {
        fun fromTrack(track: Track, streamExpiresAt: Long? = null): TrackEntity {
            return TrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                thumbnailUrl = track.thumbnailUrl,
                cachedStreamUrl = track.streamUrl,
                streamExpiresAt = streamExpiresAt,
                loudnessDb = track.loudnessDb
            )
        }
    }
}
