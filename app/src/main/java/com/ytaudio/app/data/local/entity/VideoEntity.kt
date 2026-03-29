package com.ytaudio.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ytaudio.app.domain.model.DownloadStatus

@Entity(
    tableName = "videos",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("channelId")]
)
data class VideoEntity(
    @PrimaryKey
    val id: String,
    val channelId: String? = null,
    val title: String,
    val thumbnailUrl: String? = null,
    val duration: Long = 0L,
    val publishedAt: Long = 0L,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val localFilePath: String? = null,
    val downloadedAt: Long = 0L
)
