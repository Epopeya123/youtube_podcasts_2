package com.ytaudio.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ytaudio.app.data.local.entity.VideoEntity
import com.ytaudio.app.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Query("SELECT * FROM videos ORDER BY publishedAt DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE channelId = :channelId ORDER BY publishedAt DESC")
    fun getVideosByChannel(channelId: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE downloadStatus = :status ORDER BY publishedAt DESC")
    fun getVideosByStatus(status: DownloadStatus): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE downloadStatus = 'COMPLETED' ORDER BY downloadedAt DESC")
    fun getDownloadedVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoById(id: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("UPDATE videos SET downloadStatus = :status WHERE id = :id")
    suspend fun updateDownloadStatus(id: String, status: DownloadStatus)

    @Query("UPDATE videos SET downloadStatus = :status, localFilePath = :path, downloadedAt = :downloadedAt WHERE id = :id")
    suspend fun updateDownloadComplete(id: String, status: DownloadStatus, path: String, downloadedAt: Long)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideo(id: String)
}
