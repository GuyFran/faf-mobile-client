package com.faforever.mobile.companion

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.companionDataStore by preferencesDataStore(name = "faf_companion")

data class CompanionConfig(
    val host: String = "",
    val port: Int = 6900,
    val token: String = "",
    val enabled: Boolean = false,
) {
    val isConfigured: Boolean get() = host.isNotBlank() && token.isNotBlank()
}

@Singleton
class CompanionPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        val HOST = stringPreferencesKey("relay_host")
        val PORT = intPreferencesKey("relay_port")
        val TOKEN = stringPreferencesKey("relay_token")
        val ENABLED = booleanPreferencesKey("relay_enabled")
    }

    val config: Flow<CompanionConfig> = context.companionDataStore.data.map { p ->
        CompanionConfig(
            host = p[HOST] ?: "",
            port = p[PORT] ?: 6900,
            token = p[TOKEN] ?: "",
            enabled = p[ENABLED] ?: false,
        )
    }

    suspend fun save(host: String, port: Int, token: String, enabled: Boolean) {
        context.companionDataStore.edit { p ->
            p[HOST] = host.trim()
            p[PORT] = port
            p[TOKEN] = token.trim()
            p[ENABLED] = enabled
        }
    }
}
