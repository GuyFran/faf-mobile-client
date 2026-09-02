package com.faforever.mobile.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "faf_auth")

class TokenManager(private val context: Context) {

    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val TOKEN_EXPIRY = longPreferencesKey("token_expiry")
        private val USERNAME = stringPreferencesKey("username")
        private val USER_ID = longPreferencesKey("user_id")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[REFRESH_TOKEN] }
    val username: Flow<String?> = context.dataStore.data.map { it[USERNAME] }
    val userId: Flow<Long?> = context.dataStore.data.map { it[USER_ID] }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map {
        it[ACCESS_TOKEN] != null
    }

    fun accessTokenBlocking(): String? = runBlocking {
        context.dataStore.data.first()[ACCESS_TOKEN]
    }

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresIn: Long,
        username: String?,
        userId: Long?,
    ) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = accessToken
            refreshToken?.let { prefs[REFRESH_TOKEN] = it }
            prefs[TOKEN_EXPIRY] = System.currentTimeMillis() + expiresIn * 1000
            username?.let { prefs[USERNAME] = it }
            userId?.let { prefs[USER_ID] = it }
        }
    }

    suspend fun saveIdentity(name: String?, id: Long?) {
        context.dataStore.edit { prefs ->
            name?.let { prefs[USERNAME] = it }
            id?.let { prefs[USER_ID] = it }
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun isTokenExpired(): Boolean {
        val expiry = context.dataStore.data.first()[TOKEN_EXPIRY] ?: return true
        return System.currentTimeMillis() >= expiry
    }
}
