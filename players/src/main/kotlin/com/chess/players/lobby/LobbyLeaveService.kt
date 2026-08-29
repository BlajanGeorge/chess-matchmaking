package com.chess.players.lobby

import com.chess.matching.lobby.LobbyRepository
import com.chess.players.match.MatchDeclineService
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStateRepository

class LobbyLeaveService(
    private val states: PlayerStateRepository,
    private val lobby: LobbyRepository,
    private val declineService: MatchDeclineService,
) {
    enum class Outcome {
        /** Was waiting; removed from the lobby and now idle. */
        LEFT,
        /** Was not in the flow at all; nothing to do. */
        NOT_WAITING,
        /** A match had been proposed; leaving now counts as declining it. */
        DECLINED_PENDING_MATCH,
        /** Already accepted the proposed match; an accept is final, so leaving is refused. */
        ACCEPTED_MATCH_CANNOT_LEAVE,
        /** Playing; leaving the lobby makes no sense — that would be a resign. */
        IN_MATCH,
    }

    /**
     * Lobby first, then state — the reverse of join. Once out of the lobby the matcher can't claim the player;
     * if it already had (claim + event run synchronously, so the state is PENDING by now) the guarded remove
     * fails and we report that instead of clobbering the proposal.
     */
    fun leave(playerId: String): Outcome {
        lobby.leave(playerId)
        if (states.removeIf(playerId, PlayerState.WAITING)) return Outcome.LEFT
        val status = states.find(playerId)
        return when (status?.state) {
            null, PlayerState.WAITING -> Outcome.NOT_WAITING
            PlayerState.PENDING -> when (declineService.decline(playerId, status.matchId!!)) {
                MatchDeclineService.Outcome.ALREADY_ACCEPTED -> Outcome.ACCEPTED_MATCH_CANNOT_LEAVE
                else -> Outcome.DECLINED_PENDING_MATCH
            }
            PlayerState.IN_MATCH -> Outcome.IN_MATCH
        }
    }
}
