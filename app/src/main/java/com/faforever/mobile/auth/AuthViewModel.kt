package com.faforever.mobile.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            val loggedIn = tokenManager.isLoggedIn.first()
            _state.value = AuthState(isLoading = false, isLoggedIn = loggedIn)
        }
    }

    fun getAuthIntent(): Intent = authRepository.buildAuthIntent()

    fun handleAuthResult(intent: Intent) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                authRepository.handleAuthResponse(intent)
                _state.value = AuthState(isLoading = false, isLoggedIn = true)
            } catch (e: Exception) {
                _state.value = AuthState(
                    isLoading = false,
                    isLoggedIn = false,
                    error = e.message ?: "Login failed",
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.value = AuthState(isLoading = false, isLoggedIn = false)
        }
    }
}
