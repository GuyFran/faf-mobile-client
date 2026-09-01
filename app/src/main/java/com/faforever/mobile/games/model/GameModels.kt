package com.faforever.mobile.games.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LobbyMessage(
    val command: String,
    val games: List<GameInfo>? = null,
    val players: List<PlayerInfo>? = null,
    // game_info can come as a single game or as part of a list
    val uid: Int? = null,
    val title: String? = null,
    val state: String? = null,
    val host: String? = null,
    val mapname: String? = null,
    @SerialName("map_file_path") val mapFilePath: String? = null,
    @SerialName("featured_mod") val featuredMod: String? = null,
    @SerialName("num_players") val numPlayers: Int? = null,
    @SerialName("max_players") val maxPlayers: Int? = null,
    val teams: Map<String, List<String>>? = null,
    @SerialName("password_protected") val passwordProtected: Boolean? = null,
    val visibility: String? = null,
    @SerialName("game_type") val gameType: String? = null,
    @SerialName("sim_mods") val simMods: Map<String, String>? = null,
    @SerialName("launched_at") val launchedAt: Double? = null,
    @SerialName("rating_min") val ratingMin: Double? = null,
    @SerialName("rating_max") val ratingMax: Double? = null,
    @SerialName("enforce_rating_range") val enforceRatingRange: Boolean? = null,
    // welcome / auth
    val me: PlayerInfo? = null,
    val text: String? = null,
    val style: String? = null,
    // social
    val autojoin: List<String>? = null,
    val channels: List<String>? = null,
)

@Serializable
data class PlayerInfo(
    val id: Int = 0,
    val login: String = "",
    val country: String? = null,
    val clan: String? = null,
    val ratings: Map<String, RatingInfo>? = null,
    @SerialName("number_of_games") val numberOfGames: Int? = null,
)

@Serializable
data class RatingInfo(
    val rating: List<Double>? = null,
    @SerialName("number_of_games") val numberOfGames: Int? = null,
)

data class Game(
    val uid: Int,
    val title: String,
    val host: String,
    val state: GameState,
    val mapName: String,
    val mapFilePath: String,
    val featuredMod: String,
    val numPlayers: Int,
    val maxPlayers: Int,
    val teams: Map<String, List<String>>,
    val passwordProtected: Boolean,
    val gameType: String,
    val simMods: Map<String, String> = emptyMap(),
    val ratingMin: Double? = null,
    val ratingMax: Double? = null,
)

enum class GameState {
    OPEN, PLAYING, CLOSED;

    companion object {
        fun fromString(s: String): GameState = when (s.lowercase()) {
            "open" -> OPEN
            "playing" -> PLAYING
            else -> CLOSED
        }
    }
}

fun LobbyMessage.toGame(): Game? {
    val id = uid ?: return null
    val st = state ?: return null
    return Game(
        uid = id,
        title = title ?: "",
        host = host ?: "",
        state = GameState.fromString(st),
        mapName = mapname ?: "",
        mapFilePath = mapFilePath ?: "",
        featuredMod = featuredMod ?: "faf",
        numPlayers = numPlayers ?: 0,
        maxPlayers = maxPlayers ?: 0,
        teams = teams ?: emptyMap(),
        passwordProtected = passwordProtected ?: false,
        gameType = gameType ?: "custom",
        simMods = simMods ?: emptyMap(),
        ratingMin = ratingMin,
        ratingMax = ratingMax,
    )
}
