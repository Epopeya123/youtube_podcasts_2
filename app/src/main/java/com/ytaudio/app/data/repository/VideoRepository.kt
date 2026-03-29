package com.ytaudio.app.data.repository

import com.ytaudio.app.data.local.dao.VideoDao
import com.ytaudio.app.data.local.entity.VideoEntity
import com.ytaudio.app.data.remote.YouTubeExtractorService
import com.ytaudio.app.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val videoDao: VideoDao,
    private val extractorService: YouTubeExtractorService
) {
    fun getAllVideos(): Flow<List<VideoEntity>> = videoDao.getAllVideos()

    fun getDownloadedVideos(): Flow<List<VideoEntity>> = videoDao.getDownloadedVideos()

    fun getVideosByChannel(channelId: String): Flow<List<VideoEntity>> =
        videoDao.getVideosByChannel(channelId)

    suspend fun getVideoById(id: String): VideoEntity? = videoDao.getVideoById(id)

    /**
     * Add a single video by URL (user pasted a direct YouTube link).
     */
    suspend fun addVideoByUrl(videoUrl: String): VideoEntity {
        val video = extractorService.getVideoInfo(videoUrl)
        videoDao.insertVideo(video)
        return video
    }

    suspend fun updateDownloadStatus(videoId: String, status: DownloadStatus) {
        videoDao.updateDownloadStatus(videoId, status)
    }

    suspend fun updateDownloadComplete(videoId: String, localPath: String) {
        videoDao.updateDownloadComplete(
            id = videoId,
            status = DownloadStatus.COMPLETED,
            path = localPath,
            downloadedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteVideo(videoId: String) {
        videoDao.deleteVideo(videoId)
    }
}
