package com.faforever.mobile.profile

import com.faforever.mobile.network.FafApiService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerRating(
    val technicalName: String,
    val displayName: String,
    val rating: Int,
    val mean: Double,
    val deviation: Double,
    val totalGames: Int,
    val wonGames: Int,
)

data class RatingPoint(
    val timeMillis: Long,
    val rating: Double,
)

private val LEADERBOARD_DISPLAY_NAMES = mapOf(
    "global" to "Global",
    "ladder_1v1" to "Ladder 1v1",
    "tmm_2v2" to "TMM 2v2",
    "tmm_3v3" to "TMM 3v3",
    "tmm_4v4_full_share" to "TMM 4v4 Full Share",
    "tmm_4v4_share_until_death" to "TMM 4v4 Share Until Death",
)

@Singleton
class ProfileRepository @Inject constructor(
    private val apiService: FafApiService,
) {

    suspend fun fetchRatings(playerId: Long): List<PlayerRating> {
        val response = apiService.getLeaderboardRatings(filter = "player.id==$playerId")

        // Map included leaderboard id -> technicalName
        val leaderboardNames = mutableMapOf<String, String>()
        response["included"]?.jsonArray?.forEach { inc ->
            val obj = inc.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull == "leaderboard") {
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val technicalName = obj["attributes"]?.jsonObject
                    ?.get("technicalName")?.jsonPrimitive?.contentOrNull ?: return@forEach
                leaderboardNames[id] = technicalName
            }
        }

        return response["data"]?.jsonArray?.mapNotNull { entry ->
            val obj = entry.jsonObject
            val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null

            val leaderboardId = obj["relationships"]?.jsonObject
                ?.get("leaderboard")?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("id")?.jsonPrimitive?.contentOrNull
            val technicalName = leaderboardNames[leaderboardId] ?: leaderboardId ?: "unknown"

            val mean = attrs["mean"]?.jsonPrimitive?.doubleOrNull ?: 1500.0
            val deviation = attrs["deviation"]?.jsonPrimitive?.doubleOrNull ?: 500.0
            val rating = attrs["rating"]?.jsonPrimitive?.doubleOrNull ?: (mean - 3 * deviation)

            PlayerRating(
                technicalName = technicalName,
                displayName = LEADERBOARD_DISPLAY_NAMES[technicalName] ?: technicalName,
                rating = rating.toInt(),
                mean = mean,
                deviation = deviation,
                totalGames = attrs["totalGames"]?.jsonPrimitive?.intOrNull ?: 0,
                wonGames = attrs["wonGames"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        } ?: emptyList()
    }

    suspend fun fetchRatingHistory(playerId: Long, leaderboard: String): List<RatingPoint> {
        val filter = "gamePlayerStats.player.id=='$playerId';" +
            "leaderboard.technicalName=='$leaderboard';" +
            "gamePlayerStats.scoreTime=isnull='false'"
        val response = apiService.getRatingJournal(filter = filter)

        // Map included gamePlayerStats id -> scoreTime millis
        val scoreTimes = mutableMapOf<String, Long>()
        response["included"]?.jsonArray?.forEach { inc ->
            val obj = inc.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull == "gamePlayerStats") {
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val scoreTime = obj["attributes"]?.jsonObject
                    ?.get("scoreTime")?.jsonPrimitive?.contentOrNull ?: return@forEach
                try {
                    scoreTimes[id] = Instant.parse(scoreTime).toEpochMilli()
                } catch (_: Exception) {}
            }
        }

        val points = response["data"]?.jsonArray?.mapNotNull { entry ->
            val obj = entry.jsonObject
            val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null

            val statsId = obj["relationships"]?.jsonObject
                ?.get("gamePlayerStats")?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("id")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val time = scoreTimes[statsId] ?: return@mapNotNull null

            val meanAfter = attrs["meanAfter"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val deviationAfter = attrs["deviationAfter"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null

            RatingPoint(timeMillis = time, rating = meanAfter - 3 * deviationAfter)
        } ?: emptyList()

        return points.sortedBy { it.timeMillis }
    }
}
