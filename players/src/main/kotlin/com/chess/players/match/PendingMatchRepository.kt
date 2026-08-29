package com.chess.players.match

/** Proposed matches by id. Shaped after `pending:{matchId}` in Redis. */
interface PendingMatchRepository {
    fun find(matchId: String): PendingMatch?
    /** Matches proposed strictly before [before], oldest first. In Redis: a sorted set on proposedAt, not a scan. */
    fun findProposedBefore(before: java.time.Instant): List<PendingMatch>
    fun save(match: PendingMatch)
    /** Atomically removes and returns the match, or null if already resolved by someone else. */
    fun remove(matchId: String): PendingMatch?
    /**
     * Atomically records [playerId]'s accept and returns the updated match, or null if the match no longer exists.
     * Idempotent for the same player. The caller that observes [PendingMatch.bothAccepted] flip to true is the one
     * that starts the match.
     */
    fun markAccepted(matchId: String, playerId: String): PendingMatch?
}
