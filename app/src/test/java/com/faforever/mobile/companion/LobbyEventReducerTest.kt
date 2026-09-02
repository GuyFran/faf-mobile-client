package com.faforever.mobile.companion

import com.faforever.mobile.games.model.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyEventReducerTest {

    private fun run(vararg inputs: RelayInput): Pair<ReducerState, List<LobbyEvent>> {
        var state = ReducerState()
        val events = mutableListOf<LobbyEvent>()
        for (i in inputs) {
            val (next, ev) = LobbyEventReducer.reduce(state, i)
            state = next
            events += ev
        }
        return state to events
    }

    // Compact view of emitted events: (type, uid).
    private fun evs(events: List<LobbyEvent>) =
        events.map { it::class.simpleName!! to it.ctx.uid }

    private fun game(uid: Int, state: GameState, n: Int = 0, max: Int = 8) =
        RelayInput.GameInfo(uid, state, n, max, "g$uid", "h$uid")

    private fun open(uid: Int, n: Int = 1, max: Int = 8) = game(uid, GameState.OPEN, n, max)
    private fun playing(uid: Int) = game(uid, GameState.PLAYING, 8, 8)
    private fun closed(uid: Int) = game(uid, GameState.CLOSED)

    private fun baseline(epoch: Int = 1, vararg games: RelayInput.GameInfo): List<RelayInput> =
        listOf(RelayInput.SnapshotBegin(epoch)) + games.toList() + RelayInput.SnapshotEnd(epoch)

    private fun run(inputs: List<RelayInput>) = run(*inputs.toTypedArray())

    // -- baselines emit nothing ---------------------------------------------
    @Test fun initialBaselineEmitsNothing() {
        val (state, events) = run(baseline(1, open(1), open(2)))
        assertTrue(events.isEmpty())
        assertEquals(SyncPhase.LIVE, state.phase)
        assertEquals(setOf(1, 2), state.games.keys)
    }

    @Test fun reconnectBaselineEmitsNothing() {
        val (_, events) = run(baseline(1, open(1)) + baseline(2, open(1), open(2)))
        assertTrue(events.isEmpty())
    }

    @Test fun preBaselineUpdateIsIgnored() {
        val (state, events) = run(open(5))
        assertTrue(events.isEmpty())
        assertTrue(state.games.isEmpty())
    }

    // -- epoch validation (Turn 16) -----------------------------------------
    @Test fun endWithoutBeginIsIgnored() {
        val (state, events) = run(RelayInput.SnapshotEnd(1), open(1))
        assertEquals(SyncPhase.WAITING, state.phase) // never went LIVE
        assertTrue(events.isEmpty())
        assertTrue(state.games.isEmpty())            // the update had no baseline to land in
    }

    @Test fun staleMismatchedEndDoesNotGoLive() {
        val (state, _) = run(RelayInput.SnapshotBegin(2), open(1), RelayInput.SnapshotEnd(1))
        assertEquals(SyncPhase.SYNCING, state.phase)  // epoch 1 != 2, still syncing
    }

    @Test fun newerBeginSupersedesOlder() {
        val inputs = listOf(
            RelayInput.SnapshotBegin(1), open(1),
            RelayInput.SnapshotBegin(2), open(2),
            RelayInput.SnapshotEnd(2),
        )
        val (state, events) = run(inputs)
        assertTrue(events.isEmpty())                  // all baseline, no notifications
        assertEquals(SyncPhase.LIVE, state.phase)
        assertEquals(setOf(2), state.games.keys)      // begin(2) cleared uid 1
    }

    // -- open / close / reopen ----------------------------------------------
    @Test fun newOpenLobbyEmitsOpened() {
        val (_, events) = run(baseline() + open(1))
        assertEquals(listOf("LobbyOpened" to 1), evs(events))
    }

    @Test fun openedEventCarriesContext() {
        val (_, events) = run(baseline() + open(1, n = 3, max = 8))
        val ctx = events.single().ctx
        assertEquals("g1", ctx.title)
        assertEquals("h1", ctx.host)
        assertEquals(3, ctx.curPlayers)
        assertEquals(8, ctx.maxPlayers)
    }

    @Test fun duplicateUpdateEmitsNothing() {
        val (_, events) = run(baseline() + open(1, n = 2) + open(1, n = 2))
        assertEquals(listOf("LobbyOpened" to 1), evs(events))
    }

    @Test fun explicitOpenToClosedEmitsClosed() {
        val (_, events) = run(baseline(1, open(1)) + closed(1))
        assertEquals(listOf("LobbyClosed" to 1), evs(events))
    }

    @Test fun playingToClosedIsSilent() {
        val (_, events) = run(baseline() + open(1) + playing(1) + closed(1))
        assertEquals(listOf("LobbyOpened" to 1, "LobbyLaunched" to 1), evs(events))
    }

    @Test fun closeThenReopenReemitsOpened() {
        val (_, events) = run(baseline(1, open(1)) + closed(1) + open(1))
        assertEquals(listOf("LobbyClosed" to 1, "LobbyOpened" to 1), evs(events))
    }

    // -- launch --------------------------------------------------------------
    @Test fun openToPlayingEmitsLaunched() {
        val (_, events) = run(baseline(1, open(1)) + playing(1))
        assertEquals(listOf("LobbyLaunched" to 1), evs(events))
    }

    // -- thresholds ----------------------------------------------------------
    @Test fun crossingToAlmostThenFull() {
        val (_, events) = run(baseline(1, open(1, n = 1, max = 8)) + open(1, n = 7, max = 8) + open(1, n = 8, max = 8))
        assertEquals(listOf("LobbyAlmostFull" to 1, "LobbyFull" to 1), evs(events))
    }

    @Test fun directJumpToFullEmitsOnlyFull() {
        val (_, events) = run(baseline(1, open(1, n = 1, max = 8)) + open(1, n = 8, max = 8))
        assertEquals(listOf("LobbyFull" to 1), evs(events))
    }

    @Test fun leaveThenRefillReemitsFull() {
        val (_, events) = run(
            baseline(1, open(1, n = 8, max = 8)) + open(1, n = 7, max = 8) + open(1, n = 8, max = 8),
        )
        assertEquals(listOf("LobbyFull" to 1), evs(events))
    }

    @Test fun invalidCapacityEmitsNoThreshold() {
        val (_, events) = run(baseline(1, open(1, n = 0, max = 0)) + open(1, n = 0, max = 0))
        assertTrue(events.isEmpty())
    }

    @Test fun capacityOfOneNeverEmitsAlmostFull() {
        // max=1: max-1 = 0, which is meaningless for "almost full" — only full may fire.
        val (_, events) = run(baseline(1, open(1, n = 0, max = 1)) + open(1, n = 1, max = 1))
        assertEquals(listOf("LobbyFull" to 1), evs(events))
    }

    // -- source offline resets ----------------------------------------------
    @Test fun sourceOfflineClearsAndSuppresses() {
        val (state, events) = run(baseline(1, open(1)) + RelayInput.SourceOffline + open(2))
        assertEquals(SyncPhase.WAITING, state.phase)
        assertTrue(state.games.isEmpty())
        assertTrue(events.isEmpty())
    }
}
