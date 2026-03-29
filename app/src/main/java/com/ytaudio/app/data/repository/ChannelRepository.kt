package com.ytaudio.app.data.repository

import com.ytaudio.app.data.local.dao.ChannelDao
import com.ytaudio.app.data.local.dao.VideoDao
import com.ytaudio.app.data.local.entity.ChannelEntity
import com.ytaudio.app.data.local.entity.VideoEntity
import com.ytaudio.app.data.remote.YouTubeExtractorService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val videoDao: VideoDao,
    private val extractorService: YouTubeExtractorService
) {
    fun getAllChannels(): Flow<List<ChannelEntity>> = channelDao.getAllChannels()

    suspend fun addChannel(channelUrl: String): ChannelEntity {
        val channel = extractorService.getChannelInfo(channelUrl)
        channelDao.insertChannel(channel)
        return channel
    }

    suspend fun deleteChannel(channelId: String) {
        channelDao.deleteChannel(channelId)
    }

    /**
     * Refresh a channel: fetch latest videos and insert any new ones.
     * Returns the number of new videos found.
     */
    suspend fun refreshChannel(channelId: String): Int {
        val channel = channelDao.getChannelById(channelId) ?: return 0
        val videos = extractorService.getChannelVideos(channel.url)
        videoDao.insertVideos(videos) // IGNORE strategy: only new videos inserted
        channelDao.updateLastChecked(channelId, System.currentTimeMillis())
        return videos.size
    }

    /**
     * Refresh all channels. Returns total new videos found.
     * Called from WorkManager, not from UI.
     */
    suspend fun refreshAllChannels(): Int {
        val channels = channelDao.getAllChannelsList()
        var total = 0
        for (channel in channels) {
            try {
                total += refreshChannel(channel.id)
            } catch (e: Exception) {
                // Log but continue with other channels
                android.util.Log.e("YTAudio", "Failed to refresh channel ${channel.name}", e)
            }
        }
        return total
    }
}
