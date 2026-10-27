package com.sync.music.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sync.music.core.database.dao.TrackDao
import com.sync.music.core.database.entity.OfflineCacheEntity
import com.sync.music.core.database.entity.TrackEntity
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

@Database(
    entities = [TrackEntity::class, OfflineCacheEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        private const val DB_NAME = "sync_encrypted.db"

        @Volatile
        private var INSTANCE: SyncDatabase? = null

        fun getInstance(context: Context, passphraseProvider: () -> ByteArray): SyncDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, passphraseProvider()).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, passphrase: ByteArray): SyncDatabase {
            SQLiteDatabase.loadLibs(context)
            val supportFactory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                SyncDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(supportFactory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
