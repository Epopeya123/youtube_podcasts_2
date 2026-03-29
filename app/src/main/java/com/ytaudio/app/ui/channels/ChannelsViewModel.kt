package com.ytaudio.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytaudio.app.data.local.entity.ChannelEntity
import com.ytaudio.app.data.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val channelRepository: ChannelRepository
) : ViewModel() {

    val channels: StateFlow<List<ChannelEntity>> = channelRepository.getAllChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _refreshingChannelId = MutableStateFlow<String?>(null)
    val refreshingChannelId: StateFlow<String?> = _refreshingChannelId.asStateFlow()

    fun addChannel(channelUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val channel = channelRepository.addChannel(channelUrl)
                _message.value = "Added: ${channel.name}"
                // Also fetch initial videos
                channelRepository.refreshChannel(channel.id)
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteChannel(channelId: String) {
        viewModelScope.launch {
            channelRepository.deleteChannel(channelId)
        }
    }

    fun refreshChannel(channelId: String) {
        viewModelScope.launch {
            _refreshingChannelId.value = channelId
            try {
                val count = channelRepository.refreshChannel(channelId)
                _message.value = "Found $count videos"
            } catch (e: Exception) {
                _message.value = "Refresh failed: ${e.message}"
            } finally {
                _refreshingChannelId.value = null
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
