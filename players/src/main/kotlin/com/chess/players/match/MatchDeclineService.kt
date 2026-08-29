package com.chess.players.match

import com.chess.matching.lobby.LobbyPlayer
import com.chess.matching.lobby.LobbyRepository
import com.chess.players.event.MatchCancelledEvent
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStateRepository
import com.chess.players.state.PlayerStatus
import org.springframework.context.ApplicationEventPublisher

class MatchDeclineService(
    private val states: PlayerStateRepository,
    private val pendingMatches: PendingMatchRepository,
    private val lobby: LobbyRepository,
    private val publisher: ApplicationEventPublisher,
) {
    enum class Outcome {
        /** Decliner is idle; partner is back in the lobby with their original wait time. */
        DECLINED,
        /** Player is not PENDING on this match — idle, waiting, playing, or pending on a different one. */
        NOT_PENDING_ON_MATCH,
        /** Someone else resolved the match first (partner declined, timeout, or the match is starting). Nothing changed. */
        ALREADY_RESOLVED,
        /** An accept is final: once given, the player can no longer decline (or leave) this match. */
        ALREADY_ACCEPTED,
    }

    fun decline(playerId: String, matchId: String): Outcome {
        val status = states.find(playerId)
        if (status?.state != PlayerState.PENDING || status.matchId != matchId) return Outcome.NOT_PENDING_ON_MATCH
        if (pendingMatches.find(matchId)?.accepted?.contains(playerId) == true) return Outcome.ALREADY_ACCEPTED

        // removing the pending match is the atomic "who resolves it" step; losing that race means someone else
        // (partner's decline, timeout, or the second accept) owns this player's state now — leave it alone
        val match = pendingMatches.remove(matchId) ?: return Outcome.ALREADY_RESOLVED
        if (playerId in match.accepted) {
            // accepted between our check and the remove: put the match back untouched and refuse
            pendingMatches.save(match)
            return Outcome.ALREADY_ACCEPTED
        }
        states.removeIf(playerId, PlayerState.PENDING)
        lobby.leave(playerId) // belt and braces: a PENDING player should already be out

        val returned = match.partnerOf(playerId)?.takeIf(::returnToLobby)?.let { listOf(it.id) } ?: emptyList()
        publisher.publishEvent(MatchCancelledEvent(matchId, MatchCancelledEvent.Reason.DECLINED, returned, listOf(playerId)))
        return Outcome.DECLINED
    }

    private fun returnToLobby(partner: LobbyPlayer): Boolean {
        val reverted = states.compareAndSet(
            partner.id,
            PlayerState.PENDING,
            PlayerStatus(partner.id, PlayerState.WAITING, since = partner.joinedAt),
        )
        if (reverted) lobby.join(partner)
        return reverted
    }
}
