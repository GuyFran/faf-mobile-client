package com.faforever.mobile.companion

import com.faforever.mobile.games.model.Game
import com.faforever.mobile.games.model.GameState

/**
 * Pure display-snapshot state machine for the companion relay stream. Separate from
 * [LobbyEventReducer] (which derives notifications); this one derives the current games map the
 * Play tab shows.
 *
 * Epoch-validated (Turn 18): `snapshot_begin` needs a non-null epoch; `snapshot_end` commits ONLY
 * when its epoch matches the in-progress begin. A stale/mismatched/no-epoch end leaves the active
 * staged snapshot (and the committed map) untouched, so it can neither destroy a valid baseline nor
 * blindly promote incomplete state. No Android/IO deps → unit-testable.
 */
class SnapshotAssembler {
    var committed: Map<Int, Game> = emptyMap()
        private set

    private var staging: MutableMap<Int, Game>? = null
    private var epoch: Int? = null

    val isStaging: Boolean get() = staging != null

    /** Begin staging a fresh snapshot. A null epoch is malformed → ignored (no staging started). */
    fun snapshotBegin(epoch: Int?) {
        if (epoch == null) return
        staging = mutableMapOf()
        this.epoch = epoch
    }

    /**
     * Commit the staged snapshot iff [endEpoch] matches the begin epoch. Returns true on commit;
     * on any mismatch/stale/no-epoch it leaves staging + committed untouched and returns false.
     */
    fun snapshotEnd(endEpoch: Int?): Boolean {
        val s = staging
        if (s != null && endEpoch != null && endEpoch == epoch) {
            committed = s
            staging = null
            epoch = null
            return true
        }
        return false
    }

    /**
     * Apply one game update. While staging it accumulates into the pending snapshot (no commit,
     * committed unchanged). Otherwise it is a live update to the committed map. Returns true iff
     * [committed] changed (i.e. a live update the caller should publish).
     */
    fun game(g: Game): Boolean {
        val s = staging
        if (s != null) {
            if (g.state == GameState.CLOSED) s.remove(g.uid) else s[g.uid] = g
            return false
        }
        committed = committed.toMutableMap().apply {
            if (g.state == GameState.CLOSED) remove(g.uid) else put(g.uid, g)
        }
        return true
    }

    /** Drop any half-staged snapshot but keep the committed map (used on reconnect — avoids flicker). */
    fun abortStaging() {
        staging = null
        epoch = null
    }

    /** Full reset (used on source_offline). */
    fun clear() {
        staging = null
        epoch = null
        committed = emptyMap()
    }
}
