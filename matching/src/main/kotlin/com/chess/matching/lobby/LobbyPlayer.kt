package com.chess.matching.lobby

import java.time.Instant

/**
 * A player waiting in the lobby to be matched.
 *
 * `joinedAt` lives here, not on the connection: a player put back into the lobby after a declined match
 * keeps their original wait time.
 */
data class LobbyPlayer(
    val id: String,
    val rating: Int,
    val joinedAt: Instant,
)
