package com.faforever.mobile.games

import com.faforever.mobile.auth.TokenManager
import com.faforever.mobile.games.model.Game
import com.faforever.mobile.games.model.PlayerInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamesRepository @Inject constructor(
    private val tokenManager: TokenManager,
    json: Json,
) {
    private val lobbyClient = LobbyClient(json)

    val games: StateFlow<Map<Int, Game>> = lobbyClient.games
    val players: StateFlow<Map<Int, PlayerInfo>> = lobbyClient.players
    val connectionState: StateFlow<LobbyConnectionState> = lobbyClient.connectionState

    suspend fun connect() {
        val token = tokenManager.accessToken.first() ?: return
        lobbyClient.connect(token)
    }

    fun disconnect() {
        lobbyClient.disconnect()
    }
}
