package com.chess.players.match

import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStateRepository

class MatchAcceptService(
    private val states: PlayerStateRepository,
    private val pendingMatches: PendingMatchRepository,
    private val starter: MatchStartService,
) {
    enum class Outcome {
        /** Recorded; the match starts once the partner accepts too. Also returned for a repeated accept. */
        ACCEPTED_WAITING_FOR_PARTNER,
        /** This accept was the second one: both players are IN_MATCH and a MatchStartedEvent went out. */
        MATCH_STARTED,
        /** Player is not PENDING on this match — idle, waiting, playing, or pending on a different one. */
        NOT_PENDING_ON_MATCH,
        /** Someone else resolved the match first (partner declined, timeout, or a racing start). */
        ALREADY_RESOLVED,
    }

    fun accept(playerId: String, matchId: String): Outcome {
        val status = states.find(playerId)
        if (status?.state != PlayerState.PENDING || status.matchId != matchId) return Outcome.NOT_PENDING_ON_MATCH

        // one accept per player; whoever completes the pair is the single caller that goes on to start the match
        val match = pendingMatches.markAccepted(matchId, playerId) ?: return Outcome.ALREADY_RESOLVED
        if (!match.bothAccepted) return Outcome.ACCEPTED_WAITING_FOR_PARTNER

        // remove is the exclusivity guard: a racing second caller (or the timeout sweeper) finds nothing to remove
        val owned = pendingMatches.remove(matchId) ?: return Outcome.ALREADY_RESOLVED
        return if (starter.start(owned)) Outcome.MATCH_STARTED else Outcome.ALREADY_RESOLVED
    }
}
