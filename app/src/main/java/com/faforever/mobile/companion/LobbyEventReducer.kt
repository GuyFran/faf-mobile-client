package com.faforever.mobile.companion

import com.faforever.mobile.games.model.GameState

/**
 * Pure, Android-free reducer that turns the relay's message stream into notification-worthy
 * lobby events. It knows nothing about delivery, notifications, or the user's opt-in filter —
 * those live outside (Turn 14). Feed it every RelayInput in order; it returns the next state
 * plus any events, each carrying an immutable context snapshot.
 *
 * Rules (COLLAB_PLAYTAB.md §9.5, refined through Turn 16):
 * - Baseline suppression: a snapshot (begin..end) rebuilds the baseline and emits NOTHING.
 * - Epoch-validated snapshots: SnapshotEnd only completes a SYNCING phase whose epoch matches its
 *   SnapshotBegin. An end-without-begin, a stale/mismatched end, or a superseding newer begin can
 *   never flip us LIVE without a real baseline.
 * - `lobby_opened`   : a uid appears OPEN that wasn't tracked.
 * - `lobby_closed`   : an explicit live OPEN→closed update (playing→closed is silent).
 * - `lobby_launched` : a tracked lobby goes OPEN→PLAYING.
 * - thresholds (OPEN, non-negative counts): `lobby_full` on a rising edge to maxPlayers
 *   (maxPlayers>=1); `lobby_almost_full` on a rising edge to maxPlayers-1 (maxPlayers>=2). A leave
 *   (falling edge) fires nothing; a refill rises again and re-fires (predicate-edge dedup).
 */

sealed interface RelayInput {
    data class SnapshotBegin(val epoch: Int) : RelayInput
    data class SnapshotEnd(val epoch: Int) : RelayInput
    data object SourceOffline : RelayInput
    data class GameInfo(
        val uid: Int,
        val state: GameState,
        val numPlayers: Int,
        val maxPlayers: Int,
        val title: String,
        val host: String,
    ) : RelayInput
}

/** Immutable context captured at the moment the event fired (Turn 16). */
data class LobbyEventContext(
    val uid: Int,
    val title: String,
    val host: String,
    val prevPlayers: Int,
    val curPlayers: Int,
    val maxPlayers: Int,
)

sealed class LobbyEvent {
    abstract val ctx: LobbyEventContext
    data class LobbyOpened(override val ctx: LobbyEventContext) : LobbyEvent()
    data class LobbyClosed(override val ctx: LobbyEventContext) : LobbyEvent()
    data class LobbyLaunched(override val ctx: LobbyEventContext) : LobbyEvent()
    data class LobbyFull(override val ctx: LobbyEventContext) : LobbyEvent()
    data class LobbyAlmostFull(override val ctx: LobbyEventContext) : LobbyEvent()
}

enum class SyncPhase { WAITING, SYNCING, LIVE }

data class TrackedGame(
    val state: GameState,
    val numPlayers: Int,
    val maxPlayers: Int,
    val title: String,
    val host: String,
)

data class ReducerState(
    val phase: SyncPhase = SyncPhase.WAITING,
    val games: Map<Int, TrackedGame> = emptyMap(),
    val syncEpoch: Int? = null, // epoch of the in-progress SnapshotBegin, while SYNCING
)

object LobbyEventReducer {

    fun reduce(state: ReducerState, input: RelayInput): Pair<ReducerState, List<LobbyEvent>> =
        when (input) {
            RelayInput.SourceOffline ->
                ReducerState(SyncPhase.WAITING, emptyMap(), syncEpoch = null) to emptyList()

            is RelayInput.SnapshotBegin ->
                // A new begin always starts (or supersedes) a fresh baseline at its epoch.
                state.copy(phase = SyncPhase.SYNCING, games = emptyMap(), syncEpoch = input.epoch) to emptyList()

            is RelayInput.SnapshotEnd ->
                if (state.phase == SyncPhase.SYNCING && state.syncEpoch == input.epoch) {
                    state.copy(phase = SyncPhase.LIVE, syncEpoch = null) to emptyList()
                } else {
                    // end-without-begin, stale, or mismatched epoch → ignore; never go LIVE blind.
                    state to emptyList()
                }

            is RelayInput.GameInfo -> when (state.phase) {
                SyncPhase.SYNCING -> state.copy(games = applyToBaseline(state.games, input)) to emptyList()
                SyncPhase.WAITING -> state to emptyList()
                SyncPhase.LIVE -> live(state, input)
            }
        }

    private fun applyToBaseline(games: Map<Int, TrackedGame>, g: RelayInput.GameInfo): Map<Int, TrackedGame> =
        if (g.state == GameState.CLOSED) games - g.uid else games + (g.uid to track(g))

    private fun live(state: ReducerState, g: RelayInput.GameInfo): Pair<ReducerState, List<LobbyEvent>> {
        val prev = state.games[g.uid]
        val events = mutableListOf<LobbyEvent>()

        if (g.state == GameState.CLOSED) {
            if (prev != null && prev.state == GameState.OPEN) {
                events += LobbyEvent.LobbyClosed(
                    LobbyEventContext(g.uid, prev.title, prev.host, prev.numPlayers, 0, prev.maxPlayers),
                )
            }
            return state.copy(games = state.games - g.uid) to events
        }

        val cur = track(g)
        val prevPlayers = prev?.numPlayers ?: 0
        fun ctx() = LobbyEventContext(g.uid, g.title, g.host, prevPlayers, g.numPlayers, g.maxPlayers)

        if (prev == null) {
            if (g.state == GameState.OPEN) events += LobbyEvent.LobbyOpened(ctx())
        } else {
            if (prev.state == GameState.OPEN && g.state == GameState.PLAYING) {
                events += LobbyEvent.LobbyLaunched(ctx())
            }
            if (g.state == GameState.OPEN && g.numPlayers >= 0) {
                val roseToFull = g.maxPlayers >= 1 &&
                    g.numPlayers >= g.maxPlayers && prev.numPlayers < g.maxPlayers
                val roseToAlmost = g.maxPlayers >= 2 &&
                    g.numPlayers == g.maxPlayers - 1 && prev.numPlayers < g.maxPlayers - 1
                if (roseToFull) {
                    events += LobbyEvent.LobbyFull(ctx())
                } else if (roseToAlmost) {
                    events += LobbyEvent.LobbyAlmostFull(ctx())
                }
            }
        }
        return state.copy(games = state.games + (g.uid to cur)) to events
    }

    private fun track(g: RelayInput.GameInfo) =
        TrackedGame(g.state, g.numPlayers, g.maxPlayers, g.title, g.host)
}
