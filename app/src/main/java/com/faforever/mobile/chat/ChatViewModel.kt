package com.faforever.mobile.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faforever.mobile.chat.model.ChatChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    val channels: StateFlow<Map<String, ChatChannel>> = chatRepository.channels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val connectionState = chatRepository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IrcConnectionState.DISCONNECTED)

    private val _selectedChannel = MutableStateFlow<String?>(null)
    val selectedChannel: StateFlow<String?> = _selectedChannel.asStateFlow()

    fun selectChannel(channel: String) {
        _selectedChannel.value = channel
    }

    fun sendMessage(message: String) {
        val channel = _selectedChannel.value ?: return
        if (message.isBlank()) return
        chatRepository.sendMessage(channel, message.trim())
    }

    fun joinChannel(channel: String) {
        chatRepository.joinChannel(channel)
    }

    fun reconnect() {
        chatRepository.reconnect()
    }
}
