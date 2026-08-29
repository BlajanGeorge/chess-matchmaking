package com.chess.players.event

import com.chess.matching.event.MatchFoundEvent
import com.chess.players.lobby.LobbyJoinService
import com.chess.players.lobby.LobbyLeaveService
import com.chess.players.match.MatchAcceptService
import com.chess.players.match.MatchDeclineService
import com.chess.players.match.MatchProposalService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener

class PlayerEventListener(
    private val joinService: LobbyJoinService,
    private val leaveService: LobbyLeaveService,
    private val proposalService: MatchProposalService,
    private val declineService: MatchDeclineService,
    private val acceptService: MatchAcceptService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun on(event: PlayerJoinedLobbyEvent) {
        val outcome = joinService.join(event.playerId)
        log.debug("player {} join -> {}", event.playerId, outcome)
    }

    @EventListener
    fun on(event: PlayerLeftLobbyEvent) {
        val outcome = leaveService.leave(event.playerId)
        log.debug("player {} leave -> {}", event.playerId, outcome)
    }

    @EventListener
    fun on(event: PlayerDeclinedMatchEvent) {
        val outcome = declineService.decline(event.playerId, event.matchId)
        log.debug("player {} decline {} -> {}", event.playerId, event.matchId, outcome)
    }

    @EventListener
    fun on(event: PlayerAcceptedMatchEvent) {
        val outcome = acceptService.accept(event.playerId, event.matchId)
        log.debug("player {} accept {} -> {}", event.playerId, event.matchId, outcome)
    }

    @EventListener
    fun on(event: MatchFoundEvent) {
        val outcome = proposalService.onMatchFound(event)
        log.debug("match {} ({} vs {}) -> {}", event.matchId, event.playerA.id, event.playerB.id, outcome)
    }
}
