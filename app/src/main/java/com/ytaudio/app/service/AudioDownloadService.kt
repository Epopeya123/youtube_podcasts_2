package com.ytaudio.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ytaudio.app.R
import com.ytaudio.app.data.local.dao.VideoDao
import com.ytaudio.app.data.remote.AudioStreamInfo
import com.ytaudio.app.data.remote.YouTubeExtractorService
import com.ytaudio.app.domain.model.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class AudioDownloadService : Service() {

    @Inject lateinit var videoDao: VideoDao
    @Inject lateinit var extractorService: YouTubeExtractorService

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoId = intent?.getStringExtra(EXTRA_VIDEO_ID) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Preparing download..."))

        scope.launch {
            try {
                downloadAudio(videoId, videoUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $videoId", e)
                videoDao.updateDownloadStatus(videoId, DownloadStatus.FAILED)
            } finally {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun downloadAudio(videoId: String, videoUrl: String) {
        videoDao.updateDownloadStatus(videoId, DownloadStatus.DOWNLOADING)
        updateNotification("Extracting audio stream...")

        // Extract fresh audio stream URL (they expire quickly)
        val streamInfo = extractorService.extractAudioStreamUrl(videoUrl)
        val video = videoDao.getVideoById(videoId) ?: return

        updateNotification("Downloading: ${video.title}")

        // Download the audio stream
        val request = Request.Builder()
            .url(streamInfo.url)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Download failed with HTTP ${response.code}")
        }

        val responseBody = response.body ?: throw RuntimeException("Empty response body")

        // Save to MediaStore
        val fileName = sanitizeFileName(video.title) + "." + streamInfo.extension
        val uri = saveToMediaStore(fileName, streamInfo.mimeType, responseBody.byteStream())

        // Update database
        videoDao.updateDownloadComplete(
            id = videoId,
            status = DownloadStatus.COMPLETED,
            path = uri.toString(),
            downloadedAt = System.currentTimeMillis()
        )

        updateNotification("Downloaded: ${video.title}")
        Log.i(TAG, "Download complete: ${video.title} -> $uri")
    }

    private fun saveToMediaStore(
        fileName: String,
        mimeType: String,
        inputStream: java.io.InputStream
    ): android.net.Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/YouTubeDownloads")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw RuntimeException("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
        } ?: throw RuntimeException("Failed to open output stream")

        // Mark as complete
        contentValues.clear()
        contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return uri
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").take(200)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows download progress"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YT Audio")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        private const val TAG = "YTAudio"
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_VIDEO_URL = "video_url"

        fun start(context: Context, videoId: String, videoUrl: String) {
            val intent = Intent(context, AudioDownloadService::class.java).apply {
                putExtra(EXTRA_VIDEO_ID, videoId)
                putExtra(EXTRA_VIDEO_URL, videoUrl)
            }
            context.startForegroundService(intent)
        }
    }
}
