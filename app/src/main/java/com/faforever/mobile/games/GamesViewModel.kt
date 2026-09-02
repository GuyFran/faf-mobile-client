package com.faforever.mobile.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faforever.mobile.companion.CompanionConfig
import com.faforever.mobile.companion.CompanionPrefs
import com.faforever.mobile.companion.CompanionRepository
import com.faforever.mobile.companion.RelayState
import com.faforever.mobile.games.model.Game
import com.faforever.mobile.games.model.GameState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GameFilter { ALL, OPEN, PLAYING }

@HiltViewModel
class GamesViewModel @Inject constructor(
    private val companionRepository: CompanionRepository,
    private val companionPrefs: CompanionPrefs,
) : ViewModel() {

    private val _filter = MutableStateFlow(GameFilter.OPEN)
    val filter: StateFlow<GameFilter> = _filter.asStateFlow()

    /** null until DataStore has actually emitted — prevents flashing the setup form on entry. */
    val config: StateFlow<CompanionConfig?> = companionPrefs.config
        .map { it as CompanionConfig? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** True while the user explicitly reopened the connection settings. */
    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    val relayState: StateFlow<RelayState> = companionRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RelayState.DISCONNECTED)

    val lastError: StateFlow<String?> = companionRepository.lastError
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val games: StateFlow<List<Game>> = combine(
        companionRepository.games,
        _filter,
    ) { gamesMap, filter ->
        gamesMap.values
            .filter { game ->
                when (filter) {
                    GameFilter.ALL -> game.state != GameState.CLOSED
                    GameFilter.OPEN -> game.state == GameState.OPEN
                    GameFilter.PLAYING -> game.state == GameState.PLAYING
                }
            }
            .sortedByDescending { it.numPlayers }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _expandedGameId = MutableStateFlow<Int?>(null)
    val expandedGameId: StateFlow<Int?> = _expandedGameId.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = companionPrefs.config.first()
            if (cfg.isConfigured && cfg.enabled) companionRepository.connect(cfg)
        }
    }

    /** Called when the Play tab becomes visible: revive a given-up reconnect loop. */
    fun onTabVisible() {
        viewModelScope.launch {
            val cfg = companionPrefs.config.first()
            if (cfg.isConfigured && cfg.enabled &&
                companionRepository.state.value == RelayState.DISCONNECTED
            ) {
                companionRepository.retry()
            }
        }
    }

    fun setFilter(filter: GameFilter) {
        _filter.value = filter
    }

    fun toggleGameExpanded(uid: Int) {
        _expandedGameId.value = if (_expandedGameId.value == uid) null else uid
    }

    fun startEditing() {
        _editing.value = true
    }

    fun cancelEditing() {
        _editing.value = false
    }

    fun saveAndConnect(host: String, port: Int, token: String) {
        viewModelScope.launch {
            companionPrefs.save(host, port, token, enabled = true)
            _editing.value = false
            companionRepository.disconnect()
            companionRepository.connect(CompanionConfig(host, port, token, enabled = true))
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            val cfg = companionPrefs.config.first()
            if (cfg.isConfigured && cfg.enabled) companionRepository.retry()
        }
    }
}
