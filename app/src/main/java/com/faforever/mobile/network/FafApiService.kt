package com.faforever.mobile.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface FafApiService {

    @GET
    suspend fun getIrcToken(@Url url: String): IrcTokenResponse

    @GET("data/leaderboardRating")
    suspend fun getLeaderboardRatings(
        @Query("filter") filter: String,
        @Query("include") include: String = "leaderboard",
    ): JsonObject

    @GET("data/leaderboardRatingJournal")
    suspend fun getRatingJournal(
        @Query("filter") filter: String,
        @Query("include") include: String = "gamePlayerStats",
        @Query("sort") sort: String = "-gamePlayerStats.scoreTime",
        @Query("page[size]") pageSize: Int = 2000,
    ): JsonObject
}

@Serializable
data class IrcTokenResponse(val value: String)
