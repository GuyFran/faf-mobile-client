package com.faforever.mobile.companion

import com.faforever.mobile.games.model.Game
import com.faforever.mobile.games.model.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotAssemblerTest {

    private fun g(uid: Int, state: GameState = GameState.OPEN) = Game(
        uid = uid, title = "g$uid", host = "h$uid", state = state, mapName = "m",
        mapFilePath = "", featuredMod = "faf", numPlayers = 1, maxPlayers = 8,
        teams = emptyMap(), passwordProtected = false, gameType = "custom",
    )

    @Test fun matchingSnapshotCommits() {
        val a = SnapshotAssembler()
        a.snapshotBegin(1)
        a.game(g(1))
        a.game(g(2))
        assertEquals(emptyMap<Int, Game>(), a.committed) // nothing committed while staging
        assertTrue(a.snapshotEnd(1))
        assertEquals(setOf(1, 2), a.committed.keys)
    }

    @Test fun mismatchedEndPreservesStagingAndCommitsOnLaterMatch() {
        val a = SnapshotAssembler()
        a.snapshotBegin(2)
        a.game(g(5))
        // Stale end for a different epoch must NOT drop the staged snapshot.
        assertFalse(a.snapshotEnd(1))
        assertTrue(a.isStaging)
        assertEquals(emptyMap<Int, Game>(), a.committed)
        // The correct end still commits the preserved staging.
        assertTrue(a.snapshotEnd(2))
        assertEquals(setOf(5), a.committed.keys)
    }

    @Test fun endWithoutBeginIsInert() {
        val a = SnapshotAssembler()
        assertFalse(a.snapshotEnd(1))
        assertFalse(a.isStaging)
        assertEquals(emptyMap<Int, Game>(), a.committed)
    }

    @Test fun nullEpochBeginDoesNotStage() {
        val a = SnapshotAssembler()
        a.snapshotBegin(null)
        assertFalse(a.isStaging)
    }

    @Test fun nullEpochEndDoesNotCommit() {
        val a = SnapshotAssembler()
        a.snapshotBegin(3)
        a.game(g(1))
        assertFalse(a.snapshotEnd(null)) // no-epoch end must not coalesce/commit
        assertTrue(a.isStaging)
    }

    @Test fun liveUpdateAfterCommitChangesCommitted() {
        val a = SnapshotAssembler()
        a.snapshotBegin(1); a.snapshotEnd(1)  // empty baseline, now live
        assertTrue(a.game(g(7)))              // returns true → caller should publish
        assertEquals(setOf(7), a.committed.keys)
        assertTrue(a.game(g(7, GameState.CLOSED)))
        assertEquals(emptySet<Int>(), a.committed.keys)
    }

    @Test fun stagingUpdatesDoNotTouchCommitted() {
        val a = SnapshotAssembler()
        a.snapshotBegin(1); a.game(g(1)); a.snapshotEnd(1) // committed = {1}
        a.snapshotBegin(2)                                  // new staging in progress
        assertFalse(a.game(g(2)))                           // staged, not live
        assertEquals(setOf(1), a.committed.keys)            // old committed still shown
    }

    @Test fun abortStagingKeepsCommitted() {
        val a = SnapshotAssembler()
        a.snapshotBegin(1); a.game(g(1)); a.snapshotEnd(1)
        a.snapshotBegin(2); a.game(g(2))
        a.abortStaging()
        assertFalse(a.isStaging)
        assertEquals(setOf(1), a.committed.keys) // committed preserved across reconnect
    }

    @Test fun clearResetsEverything() {
        val a = SnapshotAssembler()
        a.snapshotBegin(1); a.game(g(1)); a.snapshotEnd(1)
        a.clear()
        assertEquals(emptyMap<Int, Game>(), a.committed)
        assertFalse(a.isStaging)
    }
}
