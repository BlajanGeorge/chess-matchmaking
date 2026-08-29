package com.chess.matching.event

import com.chess.matching.lobby.LobbyPlayer
import com.chess.matching.matcher.MatchHandler
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.util.UUID

/** Turns each claimed pair into an in-process [MatchFoundEvent]. Stand-in for the Kafka publisher. */
class SpringEventMatchHandler(
    private val publisher: ApplicationEventPublisher,
    private val clock: Clock = Clock.systemUTC(),
) : MatchHandler {

    override fun onMatched(a: LobbyPlayer, b: LobbyPlayer) {
        publisher.publishEvent(MatchFoundEvent(UUID.randomUUID().toString(), a, b, clock.instant()))
    }
}
