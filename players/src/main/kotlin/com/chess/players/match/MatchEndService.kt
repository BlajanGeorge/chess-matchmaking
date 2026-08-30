package com.chess.players.match

import com.chess.players.event.MatchEndedEvent
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStateRepository
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock

/**
 * Ends a running match: both players leave IN_MATCH and become idle. There is no result/rating yet — leaving is
 * simply how a match ends for now; whoever leaves first ends it for both.
 */
class MatchEndService(
    private val states: PlayerStateRepository,
    private val activeMatches: ActiveMatchRepository,
    private val publisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    enum class Outcome {
        /** The match is over; both players are idle and a [MatchEndedEvent] went out. */
        ENDED,
        /** Player is not IN_MATCH on this match — idle, waiting, pending, or playing a different one. */
        NOT_IN_MATCH,
        /** The partner ended it first; nothing left to do. */
        ALREADY_ENDED,
    }

    fun end(playerId: String, matchId: String): Outcome {
        val status = states.find(playerId)
        if (status?.state != PlayerState.IN_MATCH || status.matchId != matchId) return Outcome.NOT_IN_MATCH

        // removing the active match is the atomic "who ends it" step; the winner clears both players
        val match = activeMatches.remove(matchId) ?: return Outcome.ALREADY_ENDED
        for (id in match.playerIds) states.removeIf(id, PlayerState.IN_MATCH)
        publisher.publishEvent(MatchEndedEvent(matchId, playerId, match.playerIds, clock.instant()))
        return Outcome.ENDED
    }
}
