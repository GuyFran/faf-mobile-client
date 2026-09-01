package com.faforever.mobile.chat

import com.faforever.mobile.auth.TokenManager
import com.faforever.mobile.chat.model.ChatChannel
import com.faforever.mobile.chat.model.ChatMessage
import com.faforever.mobile.chat.model.ChatUser
import com.faforever.mobile.chat.model.MessageType
import com.faforever.mobile.network.FafApiService
import com.faforever.mobile.network.FafConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val tokenManager: TokenManager,
    private val apiService: FafApiService,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ircClient = IrcClient()

    private val _channels = MutableStateFlow<Map<String, ChatChannel>>(emptyMap())
    val channels: StateFlow<Map<String, ChatChannel>> = _channels.asStateFlow()

    val connectionState = ircClient.connectionState

    init {
        scope.launch { collectIrcEvents() }
    }

    suspend fun connect() {
        val username = tokenManager.username.first() ?: return
        val token = try {
            apiService.getIrcToken().value
        } catch (e: Exception) {
            return
        }

        ircClient.connect(username, username, token)
        ircClient.joinChannel(FafConfig.IRC_DEFAULT_CHANNEL)
    }

    fun disconnect() {
        ircClient.disconnect()
    }

    fun joinChannel(channel: String) {
        ircClient.joinChannel(channel)
    }

    fun leaveChannel(channel: String) {
        ircClient.leaveChannel(channel)
        _channels.value = _channels.value.toMutableMap().apply { remove(channel) }
    }

    fun sendMessage(channel: String, message: String) {
        if (message.startsWith("/me ")) {
            ircClient.sendAction(channel, message.removePrefix("/me "))
        } else {
            ircClient.sendMessage(channel, message)
        }
    }

    private suspend fun collectIrcEvents() {
        ircClient.events.collect { event ->
            when (event) {
                is IrcEvent.MessageReceived -> {
                    updateChannel(event.channel) { ch ->
                        ch.copy(
                            messages = (ch.messages + event.message).toMutableList(),
                        )
                    }
                }

                is IrcEvent.UserJoined -> {
                    updateChannel(event.channel) { ch ->
                        if (ch.users.none { it.name == event.user.name }) {
                            ch.copy(users = (ch.users + event.user).toMutableList())
                        } else ch
                    }
                }

                is IrcEvent.UserLeft -> {
                    updateChannel(event.channel) { ch ->
                        ch.copy(
                            users = ch.users.filter { it.name != event.username }.toMutableList(),
                        )
                    }
                }

                is IrcEvent.UserQuit -> {
                    _channels.value = _channels.value.mapValues { (_, ch) ->
                        ch.copy(
                            users = ch.users.filter { it.name != event.username }.toMutableList(),
                            messages = (ch.messages + ChatMessage(
                                sender = event.username,
                                content = "${event.username} has quit",
                                type = MessageType.SYSTEM,
                            )).toMutableList(),
                        )
                    }
                }

                is IrcEvent.ChannelUsers -> {
                    updateChannel(event.channel) { ch ->
                        val existing = ch.users.associateBy { it.name }
                        val merged = (existing + event.users.associateBy { it.name }).values
                        ch.copy(users = merged.toMutableList())
                    }
                }

                is IrcEvent.TopicChanged -> {
                    updateChannel(event.channel) { ch ->
                        ch.copy(topic = event.topic)
                    }
                }
            }
        }
    }

    private fun updateChannel(name: String, transform: (ChatChannel) -> ChatChannel) {
        _channels.value = _channels.value.toMutableMap().apply {
            val current = get(name) ?: ChatChannel(name = name)
            put(name, transform(current))
        }
    }
}
