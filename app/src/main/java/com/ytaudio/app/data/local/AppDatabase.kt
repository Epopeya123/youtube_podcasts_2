package com.ytaudio.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ytaudio.app.data.local.dao.ChannelDao
import com.ytaudio.app.data.local.dao.VideoDao
import com.ytaudio.app.data.local.entity.ChannelEntity
import com.ytaudio.app.data.local.entity.VideoEntity

@Database(
    entities = [ChannelEntity::class, VideoEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun videoDao(): VideoDao
}
