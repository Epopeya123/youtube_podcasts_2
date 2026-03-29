package com.ytaudio.app.di

import android.content.Context
import androidx.room.Room
import com.ytaudio.app.data.local.AppDatabase
import com.ytaudio.app.data.local.dao.ChannelDao
import com.ytaudio.app.data.local.dao.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ytaudio.db"
        ).build()
    }

    @Provides
    fun provideChannelDao(database: AppDatabase): ChannelDao = database.channelDao()

    @Provides
    fun provideVideoDao(database: AppDatabase): VideoDao = database.videoDao()
}
