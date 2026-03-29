package com.ytaudio.app.ui.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.ytaudio.app.data.local.entity.VideoEntity
import com.ytaudio.app.service.AudioPlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var mediaController: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentVideo = MutableStateFlow<VideoEntity?>(null)
    val currentVideo: StateFlow<VideoEntity?> = _currentVideo.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    init {
        connectToService()
        startPositionUpdater()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, AudioPlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            _isConnected.value = true

            mediaController?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateDuration()
                }
            })
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionUpdater() {
        viewModelScope.launch {
            while (true) {
                mediaController?.let { controller ->
                    _currentPosition.value = controller.currentPosition
                    if (_duration.value == 0L) updateDuration()
                }
                delay(500)
            }
        }
    }

    private fun updateDuration() {
        mediaController?.let { controller ->
            val dur = controller.duration
            if (dur > 0) _duration.value = dur
        }
    }

    fun play(video: VideoEntity) {
        _currentVideo.value = video
        val uri = video.localFilePath ?: return

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.title)
                    .build()
            )
            .build()

        mediaController?.apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) controller.pause()
            else controller.play()
        }
    }

    fun seekForward() {
        mediaController?.let { controller ->
            controller.seekTo(controller.currentPosition + 10_000)
        }
    }

    fun seekBack() {
        mediaController?.let { controller ->
            controller.seekTo(maxOf(0, controller.currentPosition - 10_000))
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
    }
}
