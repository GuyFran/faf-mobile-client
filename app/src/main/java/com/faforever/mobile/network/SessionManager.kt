package com.faforever.mobile.network

import com.faforever.mobile.chat.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the session-wide connections that authenticate directly to FAF: currently just IRC chat.
 * The open-lobby list is NOT sourced from FAF here (that needs the anti-smurf UID) — it comes
 * from the desktop companion relay, managed by the Play tab's CompanionRepository.
 */
@Singleton
class SessionManager @Inject constructor(
    private val chatRepository: ChatRepository,
    private val deviceCodeAuth: com.faforever.mobile.auth.DeviceCodeAuth,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val started = AtomicBoolean(false)

    fun startConnections() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            deviceCodeAuth.ensureFreshToken()
            // Chat blocks on the username, Profile on the userId — make sure they exist
            // even if the one-shot userinfo fetch at login failed.
            deviceCodeAuth.backfillIdentity()
            chatRepository.connect()
        }
    }

    /** Re-fetch username/userId if they are missing (used by Profile's retry). */
    suspend fun ensureIdentity(): Boolean = deviceCodeAuth.backfillIdentity()

    fun stopConnections() {
        started.set(false)
        chatRepository.disconnect()
    }
}
