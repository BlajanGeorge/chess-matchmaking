package com.chess.players.match

import com.chess.matching.event.MatchFoundEvent
import com.chess.matching.lobby.LobbyPlayer
import com.chess.matching.lobby.LobbyRepository
import com.chess.players.event.MatchCancelledEvent
import com.chess.players.event.MatchProposedEvent
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStateRepository
import com.chess.players.state.PlayerStatus
import org.springframework.context.ApplicationEventPublisher

/**
 * Applies a [MatchFoundEvent] to player state: both players WAITING → PENDING(matchId).
 *
 * The matcher already removed both from the lobby when it claimed them, so the lobby is not touched on success.
 * If either player is no longer WAITING (left, or in some unexpected state), the proposal is abandoned and the
 * other player — if we had already moved them — goes back to WAITING and into the lobby with their original
 * `joinedAt`, so their wait time is preserved.
 */
class MatchProposalService(
    private val states: PlayerStateRepository,
    private val pendingMatches: PendingMatchRepository,
    private val lobby: LobbyRepository,
    private val publisher: ApplicationEventPublisher,
) {
    enum class Outcome {
        /** Both players are now PENDING on this match. */
        PROPOSED,
        /** One player was unavailable; the other was returned to the lobby. */
        PARTNER_RETURNED,
        /** Neither player was WAITING; nothing to do. */
        DROPPED,
    }

    fun onMatchFound(event: MatchFoundEvent): Outcome {
        val aPending = moveToPending(event.playerA, event)
        val bPending = moveToPending(event.playerB, event)
        return when {
            aPending && bPending -> {
                pendingMatches.save(PendingMatch(event.matchId, event.playerA, event.playerB, event.foundAt))
                publisher.publishEvent(MatchProposedEvent(event.matchId, event.playerA, event.playerB, event.foundAt))
                Outcome.PROPOSED
            }
            aPending -> { returnToLobby(event.playerA, event.matchId); Outcome.PARTNER_RETURNED }
            bPending -> { returnToLobby(event.playerB, event.matchId); Outcome.PARTNER_RETURNED }
            else -> Outcome.DROPPED
        }
    }

    /**
     * Guarded on the exact WAITING the matcher claimed (same `joinedAt`), not just on "is WAITING": a player who left
     * and re-joined between the claim and this step has a different WAITING and must not be moved.
     */
    private fun moveToPending(player: LobbyPlayer, event: MatchFoundEvent): Boolean =
        states.compareAndSet(
            PlayerStatus(player.id, PlayerState.WAITING, since = player.joinedAt),
            PlayerStatus(player.id, PlayerState.PENDING, event.matchId, event.foundAt),
        )

    private fun returnToLobby(player: LobbyPlayer, matchId: String) {
        val reverted = states.compareAndSet(
            player.id,
            PlayerState.PENDING,
            PlayerStatus(player.id, PlayerState.WAITING, since = player.joinedAt),
        )
        if (reverted) {
            lobby.join(player)
            publisher.publishEvent(
                MatchCancelledEvent(matchId, MatchCancelledEvent.Reason.PARTNER_UNAVAILABLE, listOf(player.id), emptyList()),
            )
        }
    }
}
