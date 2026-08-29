package com.chess.players.event

import com.chess.matching.lobby.LobbyPlayer
import java.time.Instant

/** Both players accepted; the game is on. Hook for durable storage and for notifying the players. */
data class MatchStartedEvent(
    val matchId: String,
    val playerA: LobbyPlayer,
    val playerB: LobbyPlayer,
    val startedAt: Instant,
)
