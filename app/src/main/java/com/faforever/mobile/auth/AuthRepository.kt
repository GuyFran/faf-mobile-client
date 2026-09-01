package com.faforever.mobile.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.faforever.mobile.network.FafConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
) {
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(FafConfig.OAUTH_AUTH_ENDPOINT),
        Uri.parse(FafConfig.OAUTH_TOKEN_ENDPOINT),
    )

    private val authService by lazy { AuthorizationService(context) }

    fun buildAuthIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            FafConfig.OAUTH_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(FafConfig.OAUTH_REDIRECT_URI),
        )
            .setScope(FafConfig.OAUTH_SCOPES)
            .setCodeVerifier(null)
            .build()

        return authService.getAuthorizationRequestIntent(request)
    }

    suspend fun handleAuthResponse(intent: Intent) {
        val response = AuthorizationResponse.fromIntent(intent)
            ?: throw IllegalStateException("Authorization failed")

        val tokenResponse = suspendCoroutine { cont ->
            authService.performTokenRequest(
                response.createTokenExchangeRequest(),
                getClientAuthentication(),
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

        tokenManager.saveTokens(
            accessToken = tokenResponse.accessToken!!,
            refreshToken = tokenResponse.refreshToken,
            expiresIn = tokenResponse.accessTokenExpirationTime
                ?.let { (it - System.currentTimeMillis()) / 1000 }
                ?: 3600,
            username = null,
            userId = null,
        )
    }

    suspend fun refreshAccessToken(): Boolean {
        val currentRefreshToken = tokenManager.refreshToken.first() ?: return false

        return try {
            val request = TokenRequest.Builder(
                serviceConfig,
                FafConfig.OAUTH_CLIENT_ID,
            )
                .setGrantType("refresh_token")
                .setRefreshToken(currentRefreshToken)
                .build()

            val tokenResponse = suspendCoroutine { cont ->
                authService.performTokenRequest(
                    request,
                    getClientAuthentication(),
                ) { tokenResp, exception ->
                    if (tokenResp != null) cont.resume(tokenResp)
                    else cont.resumeWithException(
                        exception ?: IllegalStateException("Refresh failed"),
                    )
                }
            }

            tokenManager.saveTokens(
                accessToken = tokenResponse.accessToken!!,
                refreshToken = tokenResponse.refreshToken ?: currentRefreshToken,
                expiresIn = tokenResponse.accessTokenExpirationTime
                    ?.let { (it - System.currentTimeMillis()) / 1000 }
                    ?: 3600,
                username = null,
                userId = null,
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun logout() {
        tokenManager.clearTokens()
    }

    private fun getClientAuthentication(): ClientAuthentication =
        ClientSecretBasic("")
}
