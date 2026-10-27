package com.sync.music.core.di

import android.content.Context
import com.sync.music.core.database.SyncDatabase
import com.sync.music.core.database.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SyncDatabase {
        return SyncDatabase.getInstance(context) {
            // Secure device-bound passphrase for SQLCipher database
            "Sync_Secure_Room_SQLCipher_Passphrase_Key_2026".toByteArray()
        }
    }

    @Provides
    @Singleton
    fun provideTrackDao(database: SyncDatabase): TrackDao {
        return database.trackDao()
    }
}
