package com.chess.players.state

/**
 * Per-player matchmaking state. Shaped after a Redis hash per player with Lua-guarded transitions:
 * every state change that must not race goes through [compareAndSet].
 */
interface PlayerStateRepository {

    fun find(playerId: String): PlayerStatus?

    /** Unconditional write. Prefer [saveIfAbsent] / [compareAndSet] for anything that can race. */
    fun save(status: PlayerStatus)

    /** Writes [status] only if the player has no entry (idle → first state). Returns whether it did. */
    fun saveIfAbsent(status: PlayerStatus): Boolean

    /**
     * Writes [next] only if the player is currently in [expected] state.
     * Returns false and changes nothing otherwise (including when the player has no entry).
     */
    fun compareAndSet(playerId: String, expected: PlayerState, next: PlayerStatus): Boolean

    /**
     * Writes [next] only if the player's current entry equals [expected] exactly — state *and* `since` (and matchId).
     * Use when "same state" is not enough: a WAITING that left and re-joined is a different WAITING.
     */
    fun compareAndSet(expected: PlayerStatus, next: PlayerStatus): Boolean

    /** Removes the entry only if the player is currently in [expected] state. Returns whether it did. */
    fun removeIf(playerId: String, expected: PlayerState): Boolean

    /** Unconditional remove (idle). Returns whether an entry existed. */
    fun remove(playerId: String): Boolean
}
