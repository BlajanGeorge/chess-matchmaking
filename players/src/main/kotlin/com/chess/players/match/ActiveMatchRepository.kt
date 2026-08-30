package com.chess.players.match

/** Running matches by id. Shaped after `match:{matchId}` in Redis; the durable history would live in a DB. */
interface ActiveMatchRepository {
    fun find(matchId: String): ActiveMatch?
    fun save(match: ActiveMatch)
    /** Atomically removes and returns the match, or null if it already ended. */
    fun remove(matchId: String): ActiveMatch?
}
