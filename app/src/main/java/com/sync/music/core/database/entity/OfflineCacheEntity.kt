package com.sync.music.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_cache",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trackId"], unique = true)]
)
data class OfflineCacheEntity(
    @PrimaryKey val trackId: String,
    val localFilePath: String,
    val ivBase64: String,
    val fileSizeBytes: Long,
    val checksumSha256: String,
    val mimeType: String,
    val downloadedAt: Long = System.currentTimeMillis()
)
