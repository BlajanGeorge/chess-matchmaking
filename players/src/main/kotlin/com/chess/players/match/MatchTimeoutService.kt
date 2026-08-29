package com.chess.players.match

import com.chess.matching.lobby.LobbyPlayer
import com.chess.matching.lobby.LobbyRepository
import com.chess.players.event.MatchCancelledEvent
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStateRepository
import com.chess.players.state.PlayerStatus
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Duration

/**
 * Expires proposed matches nobody resolved within [acceptTimeout]. Server-side: it must not depend on the client
 * ever calling back (closed tab, dead network).
 *
 * Whoever had accepted goes back to the lobby with their original wait time; whoever never answered is dropped.
 * A match that turns out to be accepted by both (the second accept raced us) is started, not cancelled.
 */
class MatchTimeoutService(
    private val states: PlayerStateRepository,
    private val pendingMatches: PendingMatchRepository,
    private val lobby: LobbyRepository,
    private val acceptTimeout: Duration,
    private val clock: Clock,
    private val publisher: ApplicationEventPublisher,
    private val starter: MatchStartService,
) {
    /** @property started true when the match turned out to be fully accepted and was started instead of cancelled. */
    data class Expired(
        val matchId: String,
        val returnedToLobby: List<String> = emptyList(),
        val dropped: List<String> = emptyList(),
        val started: Boolean = false,
    )

    fun expireOverdue(): List<Expired> {
        val cutoff = clock.instant().minus(acceptTimeout)
        return pendingMatches.findProposedBefore(cutoff).mapNotNull { candidate ->
            // remove is the exclusivity guard against a last-moment accept/decline resolving it first
            val match = pendingMatches.remove(candidate.matchId) ?: return@mapNotNull null
            // both accepted, the second one just lost the remove race to us: that is a start, not a timeout
            if (match.bothAccepted) return@mapNotNull Expired(match.matchId, started = starter.start(match))
            val returned = mutableListOf<String>()
            val dropped = mutableListOf<String>()
            for (p in listOf(match.playerA, match.playerB)) {
                if (p.id in match.accepted) { returnToLobby(p); returned += p.id }
                else { states.removeIf(p.id, PlayerState.PENDING); dropped += p.id }
            }
            publisher.publishEvent(MatchCancelledEvent(match.matchId, MatchCancelledEvent.Reason.TIMEOUT, returned, dropped))
            Expired(match.matchId, returned, dropped)
        }
    }

    private fun returnToLobby(player: LobbyPlayer) {
        val reverted = states.compareAndSet(
            player.id,
            PlayerState.PENDING,
            PlayerStatus(player.id, PlayerState.WAITING, since = player.joinedAt),
        )
        if (reverted) lobby.join(player)
    }
}
