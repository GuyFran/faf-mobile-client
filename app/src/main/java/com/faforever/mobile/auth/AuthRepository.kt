package com.faforever.mobile.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.faforever.mobile.network.FafConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.NoClientAuthentication
import net.openid.appauth.ResponseTypeValues
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Browser-redirect (authorization code + PKCE) login. Kept for when FAF registers a dedicated
 * mobile client id; the working day-to-day path is the device-code flow in [DeviceCodeAuth],
 * which also owns token refresh.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
    private val json: Json,
) {
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(FafConfig.OAUTH_AUTH_ENDPOINT),
        Uri.parse(FafConfig.OAUTH_TOKEN_ENDPOINT),
    )

    private val authService by lazy { AuthorizationService(context) }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun buildAuthIntent(): Intent {
        // AppAuth generates and applies a PKCE code verifier by default — do not disable it.
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            FafConfig.OAUTH_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(FafConfig.OAUTH_REDIRECT_URI),
        )
            .setScope(FafConfig.OAUTH_SCOPES)
            .build()

        return authService.getAuthorizationRequestIntent(request)
    }

    suspend fun handleAuthResponse(intent: Intent) {
        val response = AuthorizationResponse.fromIntent(intent)
            ?: throw IllegalStateException("Authorization failed")

        val tokenResponse = suspendCoroutine { cont ->
            authService.performTokenRequest(
                response.createTokenExchangeRequest(),
                NoClientAuthentication.INSTANCE, // public client: no secret
            ) { tokenResp, exception ->
                if (tokenResp != null) {
                    cont.resume(tokenResp)
                } else {
                    cont.resumeWithException(
                        exception ?: IllegalStateException("Token exchange failed"),
                    )
                }
            }
        }

        val accessToken = tokenResponse.accessToken!!
        val userInfo = withContext(Dispatchers.IO) { fetchUserInfo(accessToken) }
        val username = userInfo?.preferredUsername ?: userInfo?.name ?: userInfo?.sub

        tokenManager.saveTokens(
            accessToken = accessToken,
            refreshToken = tokenResponse.refreshToken,
            expiresIn = tokenResponse.accessTokenExpirationTime
                ?.let { (it - System.currentTimeMillis()) / 1000 }
                ?: 3600,
            username = username,
            userId = userInfo?.sub?.toLongOrNull(),
        )
    }

    suspend fun logout() {
        tokenManager.clearTokens()
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
