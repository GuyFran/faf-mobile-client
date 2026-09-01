package com.faforever.mobile.games

import com.faforever.mobile.games.model.Game
import com.faforever.mobile.games.model.GameState
import com.faforever.mobile.games.model.LobbyMessage
import com.faforever.mobile.games.model.PlayerInfo
import com.faforever.mobile.games.model.toGame
import com.faforever.mobile.network.FafConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class LobbyConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

class LobbyClient(private val json: Json) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(LobbyConnectionState.DISCONNECTED)
    val connectionState: StateFlow<LobbyConnectionState> = _connectionState.asStateFlow()

    private val _games = MutableStateFlow<Map<Int, Game>>(emptyMap())
    val games: StateFlow<Map<Int, Game>> = _games.asStateFlow()

    private val _players = MutableStateFlow<Map<Int, PlayerInfo>>(emptyMap())
    val players: StateFlow<Map<Int, PlayerInfo>> = _players.asStateFlow()

    private var webSocket: WebSocket? = null
    private var accessToken: String = ""
    private var reconnectAttempts = 0

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(45, TimeUnit.SECONDS)
        .build()

    fun connect(token: String) {
        accessToken = token
        _connectionState.value = LobbyConnectionState.CONNECTING

        val request = Request.Builder()
            .url("${FafConfig.LOBBY_WS_URL}/?verify=$token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendCommand("""{"command":"ask_session","user_agent":"faf-mobile-client","version":"1.0.0"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                text.lines().filter { it.isNotBlank() }.forEach { handleMessage(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = LobbyConnectionState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = LobbyConnectionState.DISCONNECTED
            }
        })
    }

    fun disconnect() {
        reconnectAttempts = Int.MAX_VALUE
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _connectionState.value = LobbyConnectionState.DISCONNECTED
    }

    private fun sendCommand(cmd: String) {
        webSocket?.send(cmd + "\n")
    }

    private fun handleMessage(raw: String) {
        val msg = try {
            json.decodeFromString<LobbyMessage>(raw)
        } catch (_: Exception) {
            return
        }

        when (msg.command) {
            "session" -> {
                sendCommand("""{"command":"auth","token":"$accessToken","unique_id":"faf-mobile"}""")
            }

            "welcome" -> {
                _connectionState.value = LobbyConnectionState.CONNECTED
                reconnectAttempts = 0
            }

            "game_info" -> {
                if (msg.games != null) {
                    // Batch game list on connect
                    // games field contains LobbyMessage items... parse each
                } else {
                    val game = msg.toGame() ?: return
                    if (game.state == GameState.CLOSED) {
                        _games.value = _games.value.toMutableMap().apply { remove(game.uid) }
                    } else {
                        _games.value = _games.value.toMutableMap().apply { put(game.uid, game) }
                    }
                }
            }

            "player_info" -> {
                msg.players?.forEach { player ->
                    _players.value = _players.value.toMutableMap().apply {
                        put(player.id, player)
                    }
                }
            }

            "notice" -> {
                // Server notifications - could log or display
            }

            "authentication_failed" -> {
                _connectionState.value = LobbyConnectionState.DISCONNECTED
            }

            "ping" -> sendCommand("""{"command":"pong"}""")
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= 10) return
        reconnectAttempts++
        scope.launch {
            delay(reconnectAttempts * 5000L)
            if (_connectionState.value == LobbyConnectionState.DISCONNECTED && accessToken.isNotEmpty()) {
                connect(accessToken)
            }
        }
    }
}
