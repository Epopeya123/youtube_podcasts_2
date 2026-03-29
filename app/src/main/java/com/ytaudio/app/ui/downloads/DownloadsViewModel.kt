package com.ytaudio.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytaudio.app.data.local.entity.VideoEntity
import com.ytaudio.app.data.repository.DownloadRepository
import com.ytaudio.app.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val allVideos: StateFlow<List<VideoEntity>> = videoRepository.getAllVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startDownload(videoId: String) {
        viewModelScope.launch {
            downloadRepository.startDownload(videoId)
        }
    }
}
