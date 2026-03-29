package com.ytaudio.app.data.repository

import android.content.Context
import com.ytaudio.app.data.local.dao.VideoDao
import com.ytaudio.app.data.remote.YouTubeExtractorService
import com.ytaudio.app.domain.model.DownloadStatus
import com.ytaudio.app.service.AudioDownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoDao: VideoDao,
    private val extractorService: YouTubeExtractorService
) {
    /**
     * Start downloading audio for a video.
     * The actual download happens in AudioDownloadService (foreground service).
     */
    suspend fun startDownload(videoId: String) {
        videoDao.getVideoById(videoId) ?: return

        val videoUrl = "https://www.youtube.com/watch?v=$videoId"
        videoDao.updateDownloadStatus(videoId, DownloadStatus.DOWNLOADING)
        AudioDownloadService.start(context, videoId, videoUrl)
    }

    /**
     * Download a video by URL (user pasted a direct link).
     * First extracts metadata, saves to DB, then starts download.
     */
    suspend fun downloadByUrl(videoUrl: String) {
        val video = extractorService.getVideoInfo(videoUrl)
        videoDao.insertVideo(video)
        AudioDownloadService.start(context, video.id, videoUrl)
    }
}
