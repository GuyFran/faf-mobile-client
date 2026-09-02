package com.faforever.mobile.chat

import android.util.Log
import com.faforever.mobile.auth.DeviceCodeAuth
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val tokenManager: TokenManager,
    private val apiService: FafApiService,
    private val deviceCodeAuth: DeviceCodeAuth,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ircClient = IrcClient()

    private val _channels = MutableStateFlow<Map<String, ChatChannel>>(emptyMap())
    val channels: StateFlow<Map<String, ChatChannel>> = _channels.asStateFlow()

    val connectionState = ircClient.connectionState

    init {
        // Every internal IRC reconnect gets a FRESH ergochat token (the old one may be expired).
        ircClient.tokenProvider = { fetchIrcToken() }
        scope.launch { collectIrcEvents() }
    }

    suspend fun connect() {
        var username = tokenManager.username.first()
        if (username == null) {
            username = tokenManager.username.filterNotNull().first()
        }

        // No silent fallback to the raw OAuth token — ergo would reject it and mask the real
        // problem. Retry the proper token endpoint a few times, then give up visibly
        // (DISCONNECTED + the UI's Reconnect button).
        var token: String? = null
        for (attempt in 1..3) {
            token = fetchIrcToken()
            if (token != null) break
            Log.w("ChatRepository", "IRC token fetch failed (attempt $attempt/3)")
            delay(2000L * attempt)
        }
        if (token == null) {
            Log.e("ChatRepository", "Could not obtain an IRC token — chat stays disconnected")
            return
        }

        ircClient.connect(username, username, token)
        ircClient.joinChannel(FafConfig.IRC_DEFAULT_CHANNEL)
    }

    private suspend fun fetchIrcToken(): String? = try {
        deviceCodeAuth.ensureFreshToken()
        apiService.getIrcToken("${FafConfig.USER_API_BASE_URL}irc/ergochat/token").value
    } catch (e: Exception) {
        Log.w("ChatRepository", "IRC token fetch error: ${e.message}")
        null
    }

    /** Manual retry from the UI (also used after long offline periods). */
    fun reconnect() {
        scope.launch { connect() }
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
                        // CHATHISTORY replays after a reconnect — dedupe by message id.
                        if (ch.messages.any { it.id == event.message.id }) ch
                        else ch.copy(messages = (ch.messages + event.message).toMutableList())
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
                    // Only channels the user was actually in get the quit notice.
                    _channels.value = _channels.value.mapValues { (_, ch) ->
                        if (ch.users.none { it.name == event.username }) ch
                        else ch.copy(
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
