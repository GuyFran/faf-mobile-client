package com.faforever.mobile.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faforever.mobile.chat.model.ChatChannel
import com.faforever.mobile.ui.state.UiStatePrefs
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
    private val uiState: UiStatePrefs,
) : ViewModel() {

    val channels: StateFlow<Map<String, ChatChannel>> = chatRepository.channels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val connectionState = chatRepository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IrcConnectionState.DISCONNECTED)

    private val _selectedChannel = MutableStateFlow<String?>(null)
    val selectedChannel: StateFlow<String?> = _selectedChannel.asStateFlow()

    /** The channel the user last picked (persisted); reopened as soon as it exists again. */
    val preferredChannel: String? get() = uiState.getString(UiStatePrefs.CHAT_CHANNEL)

    /** User pick: selected and remembered. */
    fun selectChannel(channel: String) {
        _selectedChannel.value = channel
        uiState.putString(UiStatePrefs.CHAT_CHANNEL, channel)
    }

    /** Automatic fallback pick (the remembered channel is not joined yet): not remembered. */
    fun showChannel(channel: String) {
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
