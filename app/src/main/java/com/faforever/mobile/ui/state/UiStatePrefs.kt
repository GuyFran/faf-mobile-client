package com.faforever.mobile.ui.state

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uiStateDataStore by preferencesDataStore(name = "faf_ui_state")

/**
 * The app's single store for UI layout/view state (selected tab, channel, filters, expanded rows,
 * scroll positions, drawer/panel open state). Survives rotation and process death.
 *
 * Values are loaded once, synchronously, when the singleton is first created (MainActivity injects
 * it in onCreate, before the first frame), so screens restore without a visible jump. Reads are then
 * served from memory; writes update memory at once and are persisted in order on a background
 * single-lane dispatcher. Keys are listed in docs/UI_PERSISTENCE.md.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class UiStatePrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.uiStateDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val cache = ConcurrentHashMap<String, String>()

    init {
        runCatching {
            runBlocking {
                store.data.first().asMap().forEach { (k, v) -> if (v is String) cache[k.name] = v }
            }
        }
    }

    fun getString(key: String): String? = cache[key]

    fun putString(key: String, value: String?) {
        if (cache[key] == value) return
        if (value == null) cache.remove(key) else cache[key] = value
        scope.launch {
            runCatching {
                store.edit { p ->
                    val k = stringPreferencesKey(key)
                    if (value == null) p.remove(k) else p[k] = value
                }
            }
        }
    }

    fun getInt(key: String): Int? = getString(key)?.toIntOrNull()
    fun putInt(key: String, value: Int?) = putString(key, value?.toString())

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        getString(key)?.toBooleanStrictOrNull() ?: default

    fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    /** Scroll position as (first visible item index, offset in px). */
    fun getScroll(key: String): Pair<Int, Int> = parseScroll(getString("scroll.$key"))

    fun putScroll(key: String, index: Int, offset: Int) = putString("scroll.$key", "$index,$offset")

    companion object Keys {
        const val NAV_TAB = "nav.tab"
        const val CHAT_CHANNEL = "chat.channel"
        const val CHAT_USERS_DRAWER_OPEN = "chat.usersDrawerOpen"
        const val GAMES_FILTER = "games.filter"
        const val GAMES_EXPANDED = "games.expandedUid"
        const val GAMES_EDITING = "games.connectionEditorOpen"
        const val PROFILE_LEADERBOARD = "profile.leaderboard"

        /** "index,offset" → pair; anything malformed or negative falls back to the top (0, 0). */
        fun parseScroll(raw: String?): Pair<Int, Int> {
            val parts = raw?.split(',') ?: return 0 to 0
            val index = parts.getOrNull(0)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val offset = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            return index to offset
        }
    }
}
