package com.chess.players.match

import com.chess.matching.lobby.LobbyRepository
import com.chess.players.event.MatchCancelledEvent
import com.chess.players.event.MatchStartedEvent
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStateRepository
import com.chess.players.state.PlayerStatus
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock

/**
 * The single place a match goes from "both accepted" to "playing". Called by whoever wins the
 * `pendingMatches.remove` race with a fully-accepted match in hand — the second accept, or the timeout sweeper
 * that happened to expire it in the same instant.
 */
class MatchStartService(
    private val states: PlayerStateRepository,
    private val lobby: LobbyRepository,
    private val activeMatches: ActiveMatchRepository,
    private val publisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    /** Returns true if the match started; false if a player was no longer PENDING (then nobody is left IN_MATCH). */
    fun start(match: PendingMatch): Boolean {
        require(match.bothAccepted) { "${match.matchId}: cannot start, not both accepted" }
        val now = clock.instant()
        val players = listOf(match.playerA, match.playerB)
        val moved = players.filter { p ->
            states.compareAndSet(p.id, PlayerState.PENDING, PlayerStatus(p.id, PlayerState.IN_MATCH, match.matchId, now))
        }
        if (moved.size == players.size) {
            activeMatches.save(ActiveMatch(match.matchId, match.playerA, match.playerB, now))
            publisher.publishEvent(MatchStartedEvent(match.matchId, match.playerA, match.playerB, now))
            return true
        }
        // Safety net: an accept is final, so this should be unreachable — but never start a match with one player.
        // Whoever we did move goes back to the lobby with their original wait time.
        for (p in moved) {
            if (states.compareAndSet(p.id, PlayerState.IN_MATCH, PlayerStatus(p.id, PlayerState.WAITING, since = p.joinedAt))) {
                lobby.join(p)
            }
        }
        publisher.publishEvent(
            MatchCancelledEvent(match.matchId, MatchCancelledEvent.Reason.PARTNER_UNAVAILABLE, moved.map { it.id }, emptyList()),
        )
        return false
    }
}
