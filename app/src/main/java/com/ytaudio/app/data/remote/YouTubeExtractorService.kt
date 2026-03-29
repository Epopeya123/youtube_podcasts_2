package com.ytaudio.app.data.remote

import com.ytaudio.app.data.local.entity.ChannelEntity
import com.ytaudio.app.data.local.entity.VideoEntity
import com.ytaudio.app.domain.model.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeExtractorService @Inject constructor() {

    init {
        NewPipe.init(DownloaderImpl.getInstance())
    }

    /**
     * Retry an extraction with cache clearing on failure.
     * NewPipe often fails on first attempt with "page needs to be reloaded".
     */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "First attempt failed, clearing caches and retrying: ${e.message}")
            YoutubeJavaScriptPlayerManager.clearAllCaches()
            block()
        }
    }

    /**
     * Extract the best audio stream URL from a YouTube video.
     * Must be called immediately before downloading - URLs expire quickly.
     */
    suspend fun extractAudioStreamUrl(videoUrl: String): AudioStreamInfo = withContext(Dispatchers.IO) {
        withRetry {
            val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
            extractor.fetchPage()

            val audioStreams = extractor.audioStreams
                .filterNotNull()
                .sortedByDescending { it.averageBitrate }

            // Prefer M4A/MP4 streams (MediaStore-compatible), fall back to WebM/Opus
            val m4aStreams = audioStreams.filter {
                it.format?.mimeType?.contains("mp4") == true ||
                it.format?.mimeType?.contains("m4a") == true
            }
            val best = m4aStreams.firstOrNull() ?: audioStreams.firstOrNull()
                ?: throw IllegalStateException("No audio streams available for $videoUrl")

            // Map MIME types to MediaStore-compatible values
            val rawMime = best.format?.mimeType ?: "audio/mp4"
            val mimeType = when {
                rawMime.contains("webm") -> "audio/ogg"  // WebM Opus -> OGG for MediaStore
                else -> rawMime
            }
            val extension = when {
                rawMime.contains("webm") -> "ogg"
                else -> best.format?.suffix ?: "m4a"
            }

            AudioStreamInfo(
                url = best.content,
                mimeType = mimeType,
                extension = extension,
                bitrate = best.averageBitrate
            )
        }
    }

    /**
     * Get video metadata from a YouTube video URL.
     */
    suspend fun getVideoInfo(videoUrl: String): VideoEntity = withContext(Dispatchers.IO) {
        withRetry {
            val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
            extractor.fetchPage()

            VideoEntity(
                id = extractVideoId(extractor),
                title = extractor.name ?: "Unknown",
                thumbnailUrl = bestThumbnail(extractor.thumbnails),
                duration = extractor.length,
                publishedAt = parseUploadDate(extractor),
                downloadStatus = DownloadStatus.NONE
            )
        }
    }

    /**
     * Get channel info from a YouTube channel URL.
     */
    suspend fun getChannelInfo(channelUrl: String): ChannelEntity = withContext(Dispatchers.IO) {
        withRetry {
            val extractor = ServiceList.YouTube.getChannelExtractor(channelUrl)
            extractor.fetchPage()

            ChannelEntity(
                id = extractor.id,
                name = extractor.name ?: "Unknown Channel",
                url = extractor.url,
                thumbnailUrl = bestThumbnail(extractor.avatars)
            )
        }
    }

    /**
     * Get recent videos from a YouTube channel.
     * Uses the Videos tab from the channel page.
     */
    suspend fun getChannelVideos(channelUrl: String): List<VideoEntity> = withContext(Dispatchers.IO) {
        withRetry {
            val channelExtractor = ServiceList.YouTube.getChannelExtractor(channelUrl)
            channelExtractor.fetchPage()

            val channelId = channelExtractor.id

            // Find the Videos tab
            val videosTab = channelExtractor.tabs.firstOrNull { tab ->
                tab.contentFilters.contains(ChannelTabs.VIDEOS)
            } ?: return@withRetry emptyList()

            val tabInfo = ChannelTabInfo.getInfo(ServiceList.YouTube, videosTab)

            tabInfo.relatedItems.mapNotNull { item ->
                try {
                    val streamItem = item as? StreamInfoItem ?: return@mapNotNull null
                    VideoEntity(
                        id = extractIdFromUrl(streamItem.url),
                        channelId = channelId,
                        title = streamItem.name ?: "Unknown",
                        thumbnailUrl = bestThumbnail(streamItem.thumbnails),
                        duration = streamItem.duration,
                        publishedAt = streamItem.uploadDate?.offsetDateTime()
                            ?.toInstant()?.toEpochMilli() ?: 0L,
                        downloadStatus = DownloadStatus.NONE
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun bestThumbnail(images: List<Image>?): String? {
        return images?.maxByOrNull { it.height }?.url
    }

    private fun extractVideoId(extractor: StreamExtractor): String {
        return extractIdFromUrl(extractor.url)
    }

    private fun extractIdFromUrl(url: String): String {
        val regex = Regex("""(?:v=|youtu\.be/)([a-zA-Z0-9_-]{11})""")
        return regex.find(url)?.groupValues?.get(1) ?: url.hashCode().toString()
    }

    private fun parseUploadDate(extractor: StreamExtractor): Long {
        return try {
            extractor.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    companion object {
        private const val TAG = "YTAudio"
    }
}

data class AudioStreamInfo(
    val url: String,
    val mimeType: String,
    val extension: String,
    val bitrate: Int
)
