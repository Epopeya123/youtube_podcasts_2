package com.ytaudio.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ytaudio.app.data.repository.ChannelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ChannelCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val channelRepository: ChannelRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.i(TAG, "Starting periodic channel check")
            val newVideos = channelRepository.refreshAllChannels()
            Log.i(TAG, "Channel check complete: $newVideos videos found")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Channel check failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "YTAudio"
        const val WORK_NAME = "channel_check"
    }
}
