package com.ytaudio.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val lastCheckedAt: Long = 0L
)
