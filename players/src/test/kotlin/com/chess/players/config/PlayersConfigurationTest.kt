package com.chess.players.config

import com.chess.matching.config.MatchingConfiguration
import com.chess.matching.lobby.LobbyRepository
import com.chess.matching.matcher.MatcherJob
import com.chess.players.event.MatchStartedEvent
import com.chess.players.event.PlayerAcceptedMatchEvent
import com.chess.players.event.PlayerDeclinedMatchEvent
import com.chess.players.event.PlayerJoinedLobbyEvent
import com.chess.players.event.PlayerLeftLobbyEvent
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStateRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.test.annotation.DirtiesContext
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringJUnitConfig(MatchingConfiguration::class, PlayersConfiguration::class, PlayersConfigurationTest.StartedListener::class)
// matcher only runs when we call it; penalties wide enough that any two random ratings pair up
@TestPropertySource(properties = [
    "matching.interval=1h", "matching.base-penalty=5000", "matching.max-penalty=5000",
    "players.accept-timeout=200ms", "players.timeout-sweep-interval=50ms",
])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PlayersConfigurationTest {

    @Component
    class StartedListener {
        val started = CopyOnWriteArrayList<MatchStartedEvent>()
        @EventListener fun on(e: MatchStartedEvent) { started += e }
    }

    @Autowired lateinit var startedListener: StartedListener

    @Autowired lateinit var publisher: ApplicationEventPublisher
    @Autowired lateinit var states: PlayerStateRepository
    @Autowired lateinit var lobby: LobbyRepository
    @Autowired lateinit var matcher: MatcherJob

    @Test
    fun `given PlayerJoinedLobbyEvent when published then player is WAITING and in the lobby`() {
        publisher.publishEvent(PlayerJoinedLobbyEvent("a"))

        assertEquals(PlayerState.WAITING, states.find("a")?.state)
        assertTrue(lobby.find("a")!!.rating in 1000..3000)
    }

    @Test
    fun `given waiting player when PlayerLeftLobbyEvent published then idle and out of the lobby`() {
        publisher.publishEvent(PlayerJoinedLobbyEvent("a"))
        publisher.publishEvent(PlayerLeftLobbyEvent("a"))

        assertNull(states.find("a"))
        assertNull(lobby.find("a"))
    }

    @Test
    fun `given two waiting players when matcher runs then MatchFoundEvent moves both to PENDING on the same match`() {
        publisher.publishEvent(PlayerJoinedLobbyEvent("x"))
        publisher.publishEvent(PlayerJoinedLobbyEvent("y"))
        val stats = matcher.runOnce()

        assertEquals(1, stats.paired)
        val x = states.find("x")!!; val y = states.find("y")!!
        assertEquals(PlayerState.PENDING, x.state)
        assertEquals(PlayerState.PENDING, y.state)
        assertEquals(x.matchId, y.matchId)
        assertEquals(0, lobby.size())

        publisher.publishEvent(PlayerDeclinedMatchEvent("x", x.matchId!!))

        assertNull(states.find("x"))
        assertEquals(PlayerState.WAITING, states.find("y")?.state)
        assertEquals(1, lobby.size())
    }

    @Test
    fun `given proposed match nobody answers when accept timeout passes then sweeper drops both`() {
        publisher.publishEvent(PlayerJoinedLobbyEvent("x"))
        publisher.publishEvent(PlayerJoinedLobbyEvent("y"))
        matcher.runOnce()
        assertEquals(PlayerState.PENDING, states.find("x")?.state)

        val deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos()
        while (states.find("x") != null && System.nanoTime() < deadline) Thread.sleep(20)

        assertNull(states.find("x"))
        assertNull(states.find("y"))
        assertEquals(0, lobby.size())
    }

    @Test
    fun `given proposed match when both accept then both IN_MATCH and MatchStartedEvent published once`() {
        publisher.publishEvent(PlayerJoinedLobbyEvent("x"))
        publisher.publishEvent(PlayerJoinedLobbyEvent("y"))
        matcher.runOnce()
        val matchId = states.find("x")!!.matchId!!

        publisher.publishEvent(PlayerAcceptedMatchEvent("x", matchId))
        assertEquals(PlayerState.PENDING, states.find("y")?.state)
        assertTrue(startedListener.started.isEmpty())

        publisher.publishEvent(PlayerAcceptedMatchEvent("y", matchId))
        assertEquals(PlayerState.IN_MATCH, states.find("x")?.state)
        assertEquals(PlayerState.IN_MATCH, states.find("y")?.state)
        assertEquals(1, startedListener.started.size)
        assertEquals(matchId, startedListener.started[0].matchId)
    }
}
