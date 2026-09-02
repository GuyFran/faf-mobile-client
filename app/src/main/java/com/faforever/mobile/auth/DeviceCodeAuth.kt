package com.faforever.mobile.auth

import com.faforever.mobile.network.FafConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5,
)

@Serializable
data class DeviceTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String? = null,
)

@Serializable
data class DeviceTokenError(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null,
)

@Serializable
data class UserInfoResponse(
    val sub: String? = null,
    @SerialName("preferred_username") val preferredUsername: String? = null,
    val name: String? = null,
    val email: String? = null,
)

@Singleton
class DeviceCodeAuth @Inject constructor(
    private val tokenManager: TokenManager,
    private val json: Json,
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun requestDeviceCode(): DeviceCodeResponse {
        val body = FormBody.Builder()
            .add("client_id", FafConfig.OAUTH_DEVICE_CLIENT_ID)
            .add("scope", FafConfig.OAUTH_SCOPES)
            .build()

        val request = Request.Builder()
            .url(FafConfig.OAUTH_DEVICE_AUTH_ENDPOINT)
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw IllegalStateException("Empty response from device auth endpoint")

        if (!response.isSuccessful) {
            throw IllegalStateException("Device auth request failed: $responseBody")
        }

        return json.decodeFromString<DeviceCodeResponse>(responseBody)
    }

    suspend fun pollForToken(deviceCode: String, interval: Int, expiresIn: Int): DeviceTokenResponse {
        val deadline = System.currentTimeMillis() + expiresIn * 1000L
        var pollInterval = interval.toLong().coerceAtLeast(5)

        while (System.currentTimeMillis() < deadline) {
            delay(pollInterval * 1000)

            val body = FormBody.Builder()
                .add("client_id", FafConfig.OAUTH_DEVICE_CLIENT_ID)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("device_code", deviceCode)
                .build()

            val request = Request.Builder()
                .url(FafConfig.OAUTH_TOKEN_ENDPOINT)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw IllegalStateException("Empty token response")

            if (response.isSuccessful) {
                val tokenResponse = json.decodeFromString<DeviceTokenResponse>(responseBody)

                val userInfo = fetchUserInfo(tokenResponse.accessToken)
                val username = userInfo?.preferredUsername ?: userInfo?.name ?: userInfo?.sub

                tokenManager.saveTokens(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresIn = tokenResponse.expiresIn,
                    username = username,
                    userId = userInfo?.sub?.toLongOrNull(),
                )
                return tokenResponse
            }

            val error = json.decodeFromString<DeviceTokenError>(responseBody)
            when (error.error) {
                "authorization_pending" -> continue
                "slow_down" -> pollInterval += 5
                "expired_token" -> throw IllegalStateException("Code expired. Please try again.")
                "access_denied" -> throw IllegalStateException("Access denied by user.")
                else -> throw IllegalStateException(error.errorDescription ?: error.error)
            }
        }

        throw IllegalStateException("Code expired. Please try again.")
    }

    /**
     * Ensures username + userId are persisted, re-fetching userinfo (with retries) if the
     * one-shot fetch at login failed. Chat waits on the username and Profile on the userId,
     * so a transient userinfo failure must not strand them forever.
     */
    suspend fun backfillIdentity(): Boolean {
        if (tokenManager.username.first() != null && tokenManager.userId.first() != null) return true
        repeat(3) { attempt ->
            ensureFreshToken()
            val token = tokenManager.accessToken.first() ?: return false
            val info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                fetchUserInfo(token)
            }
            val name = info?.preferredUsername ?: info?.name ?: info?.sub
            if (name != null) {
                tokenManager.saveIdentity(name, info?.sub?.toLongOrNull())
                if (tokenManager.userId.first() != null) return true
            }
            delay(1500L * (attempt + 1))
        }
        return tokenManager.username.first() != null && tokenManager.userId.first() != null
    }

    /**
     * Refreshes the access token if it is expired (or about to expire).
     * Returns true if a usable token is available afterwards.
     */
    suspend fun ensureFreshToken(): Boolean {
        if (!tokenManager.isTokenExpired()) return true

        val refreshToken = tokenManager.refreshToken.first() ?: return false
        return try {
            val body = FormBody.Builder()
                .add("client_id", FafConfig.OAUTH_DEVICE_CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url(FafConfig.OAUTH_TOKEN_ENDPOINT)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return false
            if (!response.isSuccessful) {
                android.util.Log.w("DeviceCodeAuth", "Token refresh failed: ${responseBody.take(200)}")
                return false
            }

            val tokenResponse = json.decodeFromString<DeviceTokenResponse>(responseBody)
            tokenManager.saveTokens(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken ?: refreshToken,
                expiresIn = tokenResponse.expiresIn,
                username = null,
                userId = null,
            )
            android.util.Log.i("DeviceCodeAuth", "Access token refreshed")
            true
        } catch (e: Exception) {
            android.util.Log.w("DeviceCodeAuth", "Token refresh error: ${e.message}")
            false
        }
    }

    private fun fetchUserInfo(accessToken: String): UserInfoResponse? {
        return try {
            val request = Request.Builder()
                .url(FafConfig.OAUTH_USERINFO_ENDPOINT)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            json.decodeFromString<UserInfoResponse>(body)
        } catch (_: Exception) {
            null
        }
    }
}
