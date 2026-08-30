package com.chess.players.match

import com.chess.matching.lobby.LobbyPlayer
import java.time.Instant

/** A match both players accepted and are playing. Exists so "end match" can find the partner. */
data class ActiveMatch(
    val matchId: String,
    val playerA: LobbyPlayer,
    val playerB: LobbyPlayer,
    val startedAt: Instant,
) {
    val playerIds: List<String> get() = listOf(playerA.id, playerB.id)
    fun contains(playerId: String) = playerId == playerA.id || playerId == playerB.id
}
