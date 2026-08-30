package com.chess.players.event

import java.time.Instant

/** A running match is over; both players are idle again. */
data class MatchEndedEvent(
    val matchId: String,
    val endedBy: String,
    val playerIds: List<String>,
    val endedAt: Instant,
)
