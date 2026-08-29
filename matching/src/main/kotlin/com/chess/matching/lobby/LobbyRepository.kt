package com.chess.matching.lobby

/**
 * The set of players currently waiting to be matched.
 *
 * Shaped after a Redis sorted set keyed by rating: cheap to add/remove a single player, cheap to read
 * everyone in rating order, plus one atomic "take both or neither" operation for the matcher.
 */
interface LobbyRepository {

    /** Adds the player, or replaces their entry if they are already waiting. */
    fun join(player: LobbyPlayer)

    /** Removes the player if present. Returns whether they were waiting. */
    fun leave(playerId: String): Boolean

    fun find(playerId: String): LobbyPlayer?

    /** Everyone currently waiting, sorted by rating ascending. A point-in-time copy — may be stale by the time it is used. */
    fun snapshot(): List<LobbyPlayer>

    /**
     * Atomically removes both players, but only if both are still waiting.
     * Returns false and changes nothing if either has already left or been claimed by someone else.
     */
    fun claim(playerIdA: String, playerIdB: String): Boolean

    fun size(): Int
}
