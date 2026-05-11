package com.samwise.unshelved.core.di

import android.content.Context
import androidx.room.Room
import com.samwise.unshelved.core.database.UnshelvedDatabase
import com.samwise.unshelved.core.network.ApiProvider
import com.samwise.unshelved.core.network.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): UnshelvedDatabase =
        Room.databaseBuilder(context, UnshelvedDatabase::class.java, "unshelved.db")
            .addMigrations(UnshelvedDatabase.MIGRATION_1_2, UnshelvedDatabase.MIGRATION_2_3, UnshelvedDatabase.MIGRATION_3_4, UnshelvedDatabase.MIGRATION_4_5, UnshelvedDatabase.MIGRATION_5_6, UnshelvedDatabase.MIGRATION_6_7, UnshelvedDatabase.MIGRATION_7_8, UnshelvedDatabase.MIGRATION_8_9)
            .build()

    @Provides
    fun provideDownloadDao(db: UnshelvedDatabase) = db.downloadDao()

    @Provides
    fun provideOfflineProgressDao(db: UnshelvedDatabase) = db.offlineProgressDao()

    @Provides
    fun provideCachedListDao(db: UnshelvedDatabase) = db.cachedListDao()

    @Provides
    fun provideQueueDao(db: UnshelvedDatabase) = db.queueDao()

    @Provides
    fun provideAutoDownloadDao(db: UnshelvedDatabase) = db.autoDownloadDao()

    @Provides
    fun provideNowPlayingDao(db: UnshelvedDatabase) = db.nowPlayingDao()
}
