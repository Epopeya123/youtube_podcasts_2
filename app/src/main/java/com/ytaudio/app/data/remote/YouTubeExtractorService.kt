package com.ytaudio.app.data.remote

import android.util.Log
import com.ytaudio.app.data.local.entity.ChannelEntity
import com.ytaudio.app.data.local.entity.VideoEntity
import com.ytaudio.app.domain.model.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeExtractorService @Inject constructor() {

    init {
        NewPipe.init(DownloaderImpl.getInstance())
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "First attempt failed, clearing caches and retrying: ${e.message}")
            YoutubeJavaScriptPlayerManager.clearAllCaches()
            block()
        }
    }

    suspend fun extractAudioStreamUrl(videoUrl: String): AudioStreamInfo = withContext(Dispatchers.IO) {
        withRetry {
            val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
            extractor.fetchPage()

            val videoTitle = extractor.name
            Log.i(TAG, "Extracting audio for: $videoTitle ($videoUrl)")

            val audioStreams = extractor.audioStreams
                .filterNotNull()
                // Only use direct download URLs, not DASH manifests
                .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }

            Log.i(TAG, "Found ${audioStreams.size} progressive audio streams")
            audioStreams.forEach { stream ->
                Log.d(TAG, "  Stream: ${stream.format?.mimeType} ${stream.averageBitrate}kbps url=${stream.url?.take(80)}...")
            }

            // Prefer M4A/MP4 streams (widely compatible), fall back to WebM/Opus
            val m4aStreams = audioStreams
                .filter { it.format?.mimeType?.contains("mp4") == true }
                .sortedByDescending { it.averageBitrate }
            val webmStreams = audioStreams
                .filter { it.format?.mimeType?.contains("webm") == true }
                .sortedByDescending { it.averageBitrate }

            val best = m4aStreams.firstOrNull() ?: webmStreams.firstOrNull()
                ?: throw IllegalStateException("No downloadable audio streams for $videoUrl")

            val rawMime = best.format?.mimeType ?: "audio/mp4"
            val mimeType = when {
                rawMime.contains("webm") -> "audio/ogg"
                else -> rawMime
            }
            val extension = when {
                rawMime.contains("webm") -> "ogg"
                else -> best.format?.suffix ?: "m4a"
            }

            Log.i(TAG, "Selected stream: ${rawMime} ${best.averageBitrate}kbps -> save as $mimeType .$extension")

            val streamUrl = best.url
                ?: throw IllegalStateException("Audio stream has no URL for $videoUrl")

            AudioStreamInfo(
                url = streamUrl,
                mimeType = mimeType,
                extension = extension,
                bitrate = best.averageBitrate
            )
        }
    }

    suspend fun getVideoInfo(videoUrl: String): VideoEntity = withContext(Dispatchers.IO) {
        withRetry {
            val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
            extractor.fetchPage()

            val id = extractVideoId(extractor)
            val title = extractor.name
            Log.i(TAG, "Got video info: '$title' (id=$id)")

            VideoEntity(
                id = id,
                title = title,
                thumbnailUrl = bestThumbnail(extractor.thumbnails),
                duration = extractor.length,
                publishedAt = parseUploadDate(extractor),
                downloadStatus = DownloadStatus.NONE
            )
        }
    }

    suspend fun getChannelInfo(channelUrl: String): ChannelEntity = withContext(Dispatchers.IO) {
        withRetry {
            val extractor = ServiceList.YouTube.getChannelExtractor(channelUrl)
            extractor.fetchPage()

            ChannelEntity(
                id = extractor.id,
                name = extractor.name,
                url = extractor.url,
                thumbnailUrl = bestThumbnail(extractor.avatars)
            )
        }
    }

    suspend fun getChannelVideos(channelUrl: String): List<VideoEntity> = withContext(Dispatchers.IO) {
        withRetry {
            val channelExtractor = ServiceList.YouTube.getChannelExtractor(channelUrl)
            channelExtractor.fetchPage()

            val channelId = channelExtractor.id

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
