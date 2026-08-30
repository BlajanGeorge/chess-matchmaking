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
     * Atomically removes both entries, but only if both are still present *exactly as given* (same `joinedAt`).
     * Returns false and changes nothing if either has left, been claimed by someone else, or left and re-joined
     * since the snapshot was taken — that re-joined entry is a different wait and belongs to the next round.
     */
    fun claim(a: LobbyPlayer, b: LobbyPlayer): Boolean

    fun size(): Int
}
