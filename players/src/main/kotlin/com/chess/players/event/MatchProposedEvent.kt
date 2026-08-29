package com.chess.players.event

import com.chess.matching.lobby.LobbyPlayer
import java.time.Instant

/** Both players are PENDING on this match and should be asked to accept. */
data class MatchProposedEvent(
    val matchId: String,
    val playerA: LobbyPlayer,
    val playerB: LobbyPlayer,
    val proposedAt: Instant,
)
