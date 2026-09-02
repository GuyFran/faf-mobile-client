package com.faforever.mobile.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faforever.mobile.auth.AuthRepository
import com.faforever.mobile.auth.TokenManager
import com.faforever.mobile.chat.ChatRepository
import com.faforever.mobile.chat.IrcConnectionState
import com.faforever.mobile.companion.CompanionRepository
import com.faforever.mobile.companion.RelayState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RatingHistoryState(
    val isLoading: Boolean = false,
    val leaderboard: String? = null,
    val points: List<RatingPoint> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val companionRepository: CompanionRepository,
    private val profileRepository: ProfileRepository,
    private val sessionManager: com.faforever.mobile.network.SessionManager,
) : ViewModel() {

    val username: StateFlow<String?> = tokenManager.username
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userId: StateFlow<Long?> = tokenManager.userId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chatConnectionState: StateFlow<IrcConnectionState> = chatRepository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IrcConnectionState.DISCONNECTED)

    val companionState: StateFlow<RelayState> = companionRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RelayState.DISCONNECTED)

    private val _ratings = MutableStateFlow<List<PlayerRating>>(emptyList())
    val ratings: StateFlow<List<PlayerRating>> = _ratings.asStateFlow()

    private val _ratingsLoading = MutableStateFlow(true)
    val ratingsLoading: StateFlow<Boolean> = _ratingsLoading.asStateFlow()

    private val _ratingsError = MutableStateFlow<String?>(null)
    val ratingsError: StateFlow<String?> = _ratingsError.asStateFlow()

    private val _history = MutableStateFlow(RatingHistoryState())
    val history: StateFlow<RatingHistoryState> = _history.asStateFlow()

    init {
        viewModelScope.launch { loadIdentityThenRatings() }
    }

    private suspend fun loadIdentityThenRatings() {
        // Don't spin forever if the login-time userinfo fetch failed — bound the wait,
        // try a backfill, then surface an actionable error.
        var id = kotlinx.coroutines.withTimeoutOrNull(8_000) {
            tokenManager.userId.filterNotNull().first()
        }
        if (id == null) {
            sessionManager.ensureIdentity()
            id = tokenManager.userId.first()
        }
        if (id == null) {
            _ratingsLoading.value = false
            _ratingsError.value = "Couldn't load your identity — check the connection and retry."
            return
        }
        loadRatings(id)
    }

    private suspend fun loadRatings(playerId: Long) {
        _ratingsLoading.value = true
        _ratingsError.value = null
        try {
            val ratings = profileRepository.fetchRatings(playerId)
            _ratings.value = ratings

            // Auto-select the leaderboard with the most games for the graph
            val defaultBoard = ratings.maxByOrNull { it.totalGames }?.technicalName
            if (defaultBoard != null) {
                selectLeaderboard(defaultBoard)
            }
        } catch (e: Exception) {
            _ratingsError.value = e.message ?: "Failed to load ratings"
        } finally {
            _ratingsLoading.value = false
        }
    }

    fun selectLeaderboard(technicalName: String) {
        if (_history.value.leaderboard == technicalName && _history.value.points.isNotEmpty()) return

        viewModelScope.launch {
            val playerId = tokenManager.userId.first() ?: return@launch
            _history.value = RatingHistoryState(isLoading = true, leaderboard = technicalName)
            try {
                val points = profileRepository.fetchRatingHistory(playerId, technicalName)
                _history.value = RatingHistoryState(
                    isLoading = false,
                    leaderboard = technicalName,
                    points = points,
                )
            } catch (e: Exception) {
                _history.value = RatingHistoryState(
                    isLoading = false,
                    leaderboard = technicalName,
                    error = e.message ?: "Failed to load history",
                )
            }
        }
    }

    fun retryRatings() {
        viewModelScope.launch {
            _ratingsLoading.value = true
            _ratingsError.value = null
            loadIdentityThenRatings()
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            sessionManager.stopConnections()
            companionRepository.disconnect()
            authRepository.logout()
            onLoggedOut()
        }
    }
}
