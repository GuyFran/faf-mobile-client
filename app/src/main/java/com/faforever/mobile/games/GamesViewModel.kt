package com.faforever.mobile.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faforever.mobile.games.model.Game
import com.faforever.mobile.games.model.GameState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GameFilter { ALL, OPEN, PLAYING }

@HiltViewModel
class GamesViewModel @Inject constructor(
    private val gamesRepository: GamesRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(GameFilter.ALL)
    val filter: StateFlow<GameFilter> = _filter.asStateFlow()

    val connectionState = gamesRepository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LobbyConnectionState.DISCONNECTED)

    val games: StateFlow<List<Game>> = combine(
        gamesRepository.games,
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
        viewModelScope.launch { gamesRepository.connect() }
    }

    fun setFilter(filter: GameFilter) {
        _filter.value = filter
    }

    fun toggleGameExpanded(uid: Int) {
        _expandedGameId.value = if (_expandedGameId.value == uid) null else uid
    }

    override fun onCleared() {
        super.onCleared()
        gamesRepository.disconnect()
    }
}
