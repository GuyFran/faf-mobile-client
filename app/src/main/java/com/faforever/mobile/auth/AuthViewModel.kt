package com.faforever.mobile.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AuthState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val deviceCode: DeviceCodeResponse? = null,
    val isPolling: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val deviceCodeAuth: DeviceCodeAuth,
    private val tokenManager: TokenManager,
    private val sessionManager: com.faforever.mobile.network.SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private var pollingJob: Job? = null

    init {
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            val loggedIn = tokenManager.isLoggedIn.first()
            if (loggedIn) sessionManager.startConnections()
            _state.value = AuthState(isLoading = false, isLoggedIn = loggedIn)
        }
    }

    fun getAuthIntent(): Intent = authRepository.buildAuthIntent()

    fun handleAuthResult(intent: Intent) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                authRepository.handleAuthResponse(intent)
                sessionManager.startConnections()
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

    fun startDeviceCodeFlow() {
        pollingJob?.cancel()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, deviceCode = null)
            try {
                val response = withContext(Dispatchers.IO) {
                    deviceCodeAuth.requestDeviceCode()
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    deviceCode = response,
                    isPolling = true,
                )
                pollForToken(response)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to start device login",
                )
            }
        }
    }

    private fun pollForToken(response: DeviceCodeResponse) {
        pollingJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    deviceCodeAuth.pollForToken(
                        deviceCode = response.deviceCode,
                        interval = response.interval,
                        expiresIn = response.expiresIn,
                    )
                }
                sessionManager.startConnections()
                _state.value = AuthState(isLoading = false, isLoggedIn = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isPolling = false,
                    deviceCode = null,
                    error = e.message ?: "Device login failed",
                )
            }
        }
    }

    fun cancelDeviceCodeFlow() {
        pollingJob?.cancel()
        _state.value = _state.value.copy(
            deviceCode = null,
            isPolling = false,
            error = null,
        )
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.value = AuthState(isLoading = false, isLoggedIn = false)
        }
    }
}
