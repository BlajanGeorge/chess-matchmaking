package com.chess.players.match

import com.chess.matching.lobby.LobbyPlayer
import java.time.Instant

/**
 * A proposed match awaiting both accepts. Keeps the original lobby entries so a returned partner keeps their wait time.
 *
 * @property accepted ids of the players who have accepted so far.
 */
data class PendingMatch(
    val matchId: String,
    val playerA: LobbyPlayer,
    val playerB: LobbyPlayer,
    val proposedAt: Instant,
    val accepted: Set<String> = emptySet(),
) {
    val bothAccepted: Boolean get() = playerA.id in accepted && playerB.id in accepted

    fun partnerOf(playerId: String): LobbyPlayer? = when (playerId) {
        playerA.id -> playerB
        playerB.id -> playerA
        else -> null
    }
}
