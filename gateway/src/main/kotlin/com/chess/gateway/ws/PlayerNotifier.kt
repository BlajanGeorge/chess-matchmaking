package com.chess.gateway.ws

import com.chess.players.event.MatchCancelledEvent
import com.chess.players.event.MatchEndedEvent
import com.chess.players.event.MatchProposedEvent
import com.chess.players.event.MatchStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import java.time.Duration

/**
 * Pushes match events to the players involved, if they are connected to this gateway.
 *
 * Runs on [NOTIFICATION_EXECUTOR], off the thread that changed the state (the matcher round, a Tomcat request):
 * a slow client's send must never stall matchmaking for everyone else.
 */
@Async(PlayerNotifier.NOTIFICATION_EXECUTOR)
class PlayerNotifier(
    private val registry: PlayerSessionRegistry,
    private val acceptTimeout: Duration,
) {

    @EventListener
    fun on(event: MatchProposedEvent) {
        val expires = acceptTimeout.seconds
        registry.send(event.playerA.id, MatchProposed(event.matchId, Opponent.of(event.playerB), expires))
        registry.send(event.playerB.id, MatchProposed(event.matchId, Opponent.of(event.playerA), expires))
    }

    @EventListener
    fun on(event: MatchStartedEvent) {
        registry.send(event.playerA.id, MatchStarted(event.matchId, Opponent.of(event.playerB)))
        registry.send(event.playerB.id, MatchStarted(event.matchId, Opponent.of(event.playerA)))
    }

    @EventListener
    fun on(event: MatchCancelledEvent) {
        for (id in event.returnedToLobby) registry.send(id, MatchCancelled(event.matchId, event.reason.name, backInLobby = true))
        for (id in event.dropped) registry.send(id, MatchCancelled(event.matchId, event.reason.name, backInLobby = false))
    }

    @EventListener
    fun on(event: MatchEndedEvent) {
        for (id in event.playerIds) registry.send(id, MatchEnded(event.matchId, event.endedBy))
    }

    companion object {
        const val NOTIFICATION_EXECUTOR = "notificationExecutor"
    }
}
