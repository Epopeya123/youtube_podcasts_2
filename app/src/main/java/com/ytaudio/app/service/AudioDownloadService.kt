package com.ytaudio.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ytaudio.app.data.local.dao.VideoDao
import com.ytaudio.app.data.remote.YouTubeExtractorService
import com.ytaudio.app.domain.model.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@AndroidEntryPoint
class AudioDownloadService : Service() {

    @Inject lateinit var videoDao: VideoDao
    @Inject lateinit var extractorService: YouTubeExtractorService

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val activeDownloads = AtomicInteger(0)
    private val downloadMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoId = intent?.getStringExtra(EXTRA_VIDEO_ID) ?: return START_NOT_STICKY
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: return START_NOT_STICKY

        Log.i(TAG, "Download requested: videoId=$videoId url=$videoUrl")

        startForeground(NOTIFICATION_ID, buildNotification("Preparing download..."))
        activeDownloads.incrementAndGet()

        scope.launch {
            try {
                // Serialize downloads to avoid NewPipe extractor conflicts
                downloadMutex.withLock {
                    downloadAudio(videoId, videoUrl)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $videoId", e)
                videoDao.updateDownloadStatus(videoId, DownloadStatus.FAILED)
                updateNotification("Download failed: ${e.message?.take(50)}")
            } finally {
                val remaining = activeDownloads.decrementAndGet()
                if (remaining <= 0) {
                    Log.i(TAG, "All downloads complete, stopping service")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun downloadAudio(videoId: String, videoUrl: String) {
        videoDao.updateDownloadStatus(videoId, DownloadStatus.DOWNLOADING)
        updateNotification("Extracting audio stream...")

        val streamInfo = extractorService.extractAudioStreamUrl(videoUrl)
        val video = videoDao.getVideoById(videoId)
        if (video == null) {
            Log.e(TAG, "Video $videoId not found in database")
            return
        }

        Log.i(TAG, "Downloading: '${video.title}' from ${streamInfo.url.take(80)}...")
        updateNotification("Downloading: ${video.title}")

        val request = Request.Builder()
            .url(streamInfo.url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code}: ${response.message}")
        }

        val responseBody = response.body ?: throw RuntimeException("Empty response body")
        val contentLength = responseBody.contentLength()
        Log.i(TAG, "Response OK, content-length: $contentLength bytes")

        val fileName = sanitizeFileName(video.title) + "." + streamInfo.extension
        val uri = saveToMediaStore(fileName, streamInfo.mimeType, responseBody.byteStream())

        videoDao.updateDownloadComplete(
            id = videoId,
            status = DownloadStatus.COMPLETED,
            path = uri.toString(),
            downloadedAt = System.currentTimeMillis()
        )

        updateNotification("Downloaded: ${video.title}")
        Log.i(TAG, "Download complete: '${video.title}' -> $uri ($contentLength bytes)")
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
            ?: throw RuntimeException("Failed to create MediaStore entry for $fileName")

        var totalBytes = 0L
        resolver.openOutputStream(uri)?.use { outputStream ->
            val buffer = ByteArray(16384)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }
        } ?: throw RuntimeException("Failed to open output stream for $uri")

        contentValues.clear()
        contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        Log.i(TAG, "Saved to MediaStore: $uri ($totalBytes bytes)")
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
