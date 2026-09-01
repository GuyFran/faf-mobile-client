package com.faforever.mobile.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FafApiService {

    @GET("irc/ergochat/token")
    suspend fun getIrcToken(): IrcTokenResponse

    @GET("data/player/{id}")
    suspend fun getPlayer(@Path("id") playerId: Int): JsonApiResponse<PlayerData>

    @GET("data/player")
    suspend fun searchPlayers(
        @Query("filter") filter: String,
        @Query("page[limit]") limit: Int = 10,
    ): JsonApiListResponse<PlayerData>
}

@Serializable
data class IrcTokenResponse(val value: String)

@Serializable
data class JsonApiResponse<T>(val data: JsonApiResource<T>)

@Serializable
data class JsonApiListResponse<T>(val data: List<JsonApiResource<T>>)

@Serializable
data class JsonApiResource<T>(
    val type: String,
    val id: String,
    val attributes: T,
)

@Serializable
data class PlayerData(
    val login: String,
    val country: String? = null,
    val clan: String? = null,
)
