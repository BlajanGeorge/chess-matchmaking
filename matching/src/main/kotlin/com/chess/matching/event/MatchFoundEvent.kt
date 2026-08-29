package com.chess.matching.event

import com.chess.matching.lobby.LobbyPlayer
import java.time.Instant

/**
 * Published once per pair the matcher has claimed from the lobby. The pair is the unit of atomicity:
 * a consumer sees both players together or not at all.
 */
data class MatchFoundEvent(
    val matchId: String,
    val playerA: LobbyPlayer,
    val playerB: LobbyPlayer,
    val foundAt: Instant,
)
