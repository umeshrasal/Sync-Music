package com.sync.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sync.music.core.database.entity.OfflineCacheEntity
import com.sync.music.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

data class OfflineTrackRecord(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val thumbnailUrl: String,
    val loudnessDb: Float?,
    val localFilePath: String,
    val mimeType: String,
    val downloadedAt: Long
)

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :trackId AND streamExpiresAt > :minExpiryEpoch")
    suspend fun getValidStreamCache(trackId: String, minExpiryEpoch: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflineCache(offlineRecord: OfflineCacheEntity)

    @Query("SELECT * FROM offline_cache WHERE trackId = :trackId")
    suspend fun getOfflineCache(trackId: String): OfflineCacheEntity?

    @Query("DELETE FROM offline_cache WHERE trackId = :trackId")
    suspend fun deleteOfflineCache(trackId: String)

    @Transaction
    @Query("""
        SELECT t.id, t.title, t.artist, t.album, t.durationMs, t.thumbnailUrl, 
               t.loudnessDb, o.localFilePath, o.mimeType, o.downloadedAt
        FROM offline_cache o
        INNER JOIN tracks t ON o.trackId = t.id
        ORDER BY o.downloadedAt DESC
    """)
    fun observeOfflineTracks(): Flow<List<OfflineTrackRecord>>

    @Query("DELETE FROM tracks WHERE streamExpiresAt IS NOT NULL AND streamExpiresAt < :thresholdEpoch")
    suspend fun pruneExpiredStreamUrls(thresholdEpoch: Long)
}
