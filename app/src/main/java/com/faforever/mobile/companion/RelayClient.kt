package com.faforever.mobile.companion

import android.util.Log
import com.faforever.mobile.games.model.LobbyMessage
import com.faforever.mobile.games.model.toGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import com.faforever.mobile.games.model.Game

/**
 * DISCONNECTED  — no socket.
 * CONNECTING    — socket open / hello sent, not yet accepted.
 * WAITING       — paired (hello_ok), waiting for the desktop's first authoritative snapshot
 *                 (e.g. the PC's FAF client isn't logged in yet, or source_offline).
 * CONNECTED     — paired and holding a current lobby snapshot.
 */
enum class RelayState { DISCONNECTED, CONNECTING, WAITING, CONNECTED }

/**
 * Connects to the desktop companion relay (the patched FAF client on the user's PC, same LAN)
 * instead of to FAF directly — no FAF auth, no anti-smurf UID involved.
 *
 * Wire protocol (one JSON object per message, newline-terminated; authoritative doc:
 * fork `src/companion/relay.py`):
 *   → phone : {"type":"hello","token":"<pairing token>"}
 *   ← relay : {"type":"hello_ok"}                          (pairing accepted)
 *             {"type":"source_offline"}                    (PC not logged into FAF)
 *             {"type":"snapshot_begin","epoch":N}
 *             <single game_info lines>                     (current open lobbies)
 *             {"type":"snapshot_end","epoch":N}
 *             <single game_info lines>                     (live add/update/close)
 * Only `game_info` is ever forwarded — no player_info, no games-array batches.
 */
class RelayClient(private val json: Json) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(RelayState.DISCONNECTED)
    val state: StateFlow<RelayState> = _state.asStateFlow()

    private val _games = MutableStateFlow<Map<Int, Game>>(emptyMap())
    val games: StateFlow<Map<Int, Game>> = _games.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var manuallyStopped = false
    @Volatile private var reconnectAttempts = 0
    private var url: String = ""
    private var token: String = ""
    private val lineBuffer = StringBuilder()

    // Pure, epoch-validated display-snapshot state machine (unit-tested separately).
    private val assembler = SnapshotAssembler()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    @Synchronized
    fun connect(host: String, port: Int, token: String) {
        this.url = "ws://$host:$port"
        this.token = token
        manuallyStopped = false
        reconnectAttempts = 0
        openSocket()
    }

    @Synchronized
    private fun openSocket() {
        // Never leak a superseded socket: cancel it before opening a new one, or a backgrounded
        // process cycle would stack live pairings until the relay's client cap refuses us.
        webSocket?.cancel()
        webSocket = null

        _state.value = RelayState.CONNECTING
        lineBuffer.setLength(0)
        assembler.abortStaging()  // drop any half-staged snapshot; keep committed to avoid flicker

        // A malformed host/port (e.g. "192.168.1.20:6900" typed into the host field) makes
        // Request.Builder throw — surface it instead of crashing, and don't retry a config error.
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            Log.w("RelayClient", "Invalid relay address $url: ${e.message}")
            _lastError.value = "Invalid PC address \"$url\" — edit the connection settings."
            _state.value = RelayState.DISCONNECTED
            return
        }

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrent(webSocket)) return
                Log.i("RelayClient", "Socket open to relay; authenticating")
                val hello = buildJsonObject {
                    put("type", "hello")
                    put("token", token)
                }
                webSocket.send(hello.toString() + "\n")
                // Stay CONNECTING until the relay accepts our token (hello_ok).
                _lastError.value = null
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (isCurrent(webSocket)) onData(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (isCurrent(webSocket)) onData(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrent(webSocket)) return  // a superseded socket must not touch state
                Log.e("RelayClient", "Relay failure: ${t.message}")
                _lastError.value = "Can't reach PC relay at $url: ${t.message}"
                _state.value = RelayState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrent(webSocket)) return
                if (_state.value == RelayState.CONNECTING) {
                    // Closed before hello_ok: almost always a rejected pairing token.
                    _lastError.value = "The PC closed the connection during pairing — " +
                        "check the pairing token (Edit connection)."
                }
                _state.value = RelayState.DISCONNECTED
                if (!manuallyStopped) scheduleReconnect()
            }
        })
    }

    /** Ignore callbacks from an old socket after a reconnect replaced it. */
    private fun isCurrent(ws: WebSocket): Boolean = ws === webSocket

    @Synchronized
    fun disconnect() {
        manuallyStopped = true
        webSocket?.close(1000, "stopped")
        webSocket = null
        _state.value = RelayState.DISCONNECTED
    }

    @Synchronized
    private fun onData(data: String) {
        lineBuffer.append(data)
        while (true) {
            val idx = lineBuffer.indexOf("\n")
            if (idx < 0) break
            val line = lineBuffer.substring(0, idx).trim()
            lineBuffer.delete(0, idx + 1)
            if (line.isNotEmpty()) handleLine(line)
        }
    }

    private fun handleLine(raw: String) {
        // Control messages (hello_ok / source_offline / snapshot_*) carry a "type" field.
        val obj = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            null
        }
        val type = obj?.get("type")?.jsonPrimitive?.contentOrNull
        if (type != null) {
            val epoch = obj["epoch"]?.jsonPrimitive?.intOrNull
            when (type) {
                "hello_ok" -> {
                    // Pairing succeeded — only now is a connection attempt truly successful.
                    reconnectAttempts = 0
                    if (_state.value != RelayState.CONNECTED) _state.value = RelayState.WAITING
                }
                "source_offline" -> {
                    // The PC is paired but its FAF client isn't logged into the lobby (yet, or
                    // it dropped). Show WAITING with no games rather than a misleading empty list.
                    assembler.clear()
                    _games.value = emptyMap()
                    _state.value = RelayState.WAITING
                }
                "snapshot_begin" -> assembler.snapshotBegin(epoch)
                "snapshot_end" -> {
                    if (assembler.snapshotEnd(epoch)) {
                        _games.value = assembler.committed
                        _state.value = RelayState.CONNECTED
                    }
                    // else: stale/mismatched/no-epoch end — leave the active snapshot untouched.
                }
            }
            return
        }

        // The relay streams single game_info lines (snapshots are begin + lines + end).
        val msg = try {
            json.decodeFromString<LobbyMessage>(raw)
        } catch (_: Exception) {
            return
        }
        if (msg.command != "game_info") return
        val g = msg.toGame() ?: return
        if (assembler.game(g)) _games.value = assembler.committed
    }

    private fun scheduleReconnect() {
        if (manuallyStopped || reconnectAttempts >= 30) return
        reconnectAttempts++
        scope.launch {
            delay((reconnectAttempts * 2000L).coerceAtMost(20000L))
            if (_state.value == RelayState.DISCONNECTED && !manuallyStopped) openSocket()
        }
    }

    /** Manual retry from the UI: resets the attempt budget. */
    @Synchronized
    fun retry() {
        if (url.isEmpty()) return
        manuallyStopped = false
        reconnectAttempts = 0
        openSocket()
    }
}
