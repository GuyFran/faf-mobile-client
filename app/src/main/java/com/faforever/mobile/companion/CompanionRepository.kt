package com.faforever.mobile.companion

import com.faforever.mobile.games.model.Game
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the LAN relay connection to the desktop companion (patched FAF client on the PC).
 * This is the source of the open-lobby list when companion mode is enabled — it avoids the
 * anti-smurf UID entirely because the desktop client is the authenticated party.
 */
@Singleton
class CompanionRepository @Inject constructor(
    json: Json,
) {
    private val relay = RelayClient(json)

    val games: StateFlow<Map<Int, Game>> = relay.games
    val state: StateFlow<RelayState> = relay.state
    val lastError: StateFlow<String?> = relay.lastError

    fun connect(config: CompanionConfig) {
        if (!config.isConfigured) return
        relay.connect(config.host, config.port, config.token)
    }

    /** Re-attempt with the already-configured address (resets the retry budget). */
    fun retry() = relay.retry()

    fun disconnect() = relay.disconnect()
}
