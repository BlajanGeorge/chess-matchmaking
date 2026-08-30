package com.chess.players.match

import com.chess.matching.lobby.LobbyPlayer
import com.chess.players.event.MatchEndedEvent
import com.chess.players.match.MatchEndService.Outcome
import com.chess.players.state.InMemoryPlayerStateRepository
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStatus
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchEndServiceTest {

    private val t0 = Instant.parse("2026-08-30T10:00:00Z")
    private val startedAt = t0.plusSeconds(10)
    private val now = t0.plusSeconds(600)
    private val states = InMemoryPlayerStateRepository()
    private val active = InMemoryActiveMatchRepository()
    private val published = java.util.concurrent.CopyOnWriteArrayList<Any>()
    private val service = MatchEndService(states, active, { published += it }, Clock.fixed(now, ZoneOffset.UTC))

    private val a = LobbyPlayer("a", 1500, t0)
    private val b = LobbyPlayer("b", 1510, t0)

    private fun playing(matchId: String = "m1") {
        states.save(PlayerStatus("a", PlayerState.IN_MATCH, matchId, startedAt))
        states.save(PlayerStatus("b", PlayerState.IN_MATCH, matchId, startedAt))
        active.save(ActiveMatch(matchId, a, b, startedAt))
    }

    @Test
    fun `given running match when a leaves then both idle and MatchEndedEvent published`() {
        playing()

        assertEquals(Outcome.ENDED, service.end("a", "m1"))

        assertNull(states.find("a"))
        assertNull(states.find("b"))
        assertNull(active.find("m1"))
        assertEquals(listOf<Any>(MatchEndedEvent("m1", "a", listOf("a", "b"), now)), published.toList())
    }

    @Test
    fun `given match already ended by partner when leave then ALREADY_ENDED and nothing published again`() {
        playing()
        service.end("b", "m1")
        states.save(PlayerStatus("a", PlayerState.IN_MATCH, "m1", startedAt)) // stale, as if a's removal hadn't landed yet

        assertEquals(Outcome.ALREADY_ENDED, service.end("a", "m1"))
        assertEquals(1, published.size)
    }

    @Test
    fun `given not in that match when leave then NOT_IN_MATCH and nothing changes`() {
        playing()
        states.save(PlayerStatus("c", PlayerState.WAITING, since = t0))

        assertEquals(Outcome.NOT_IN_MATCH, service.end("c", "m1"))
        assertEquals(Outcome.NOT_IN_MATCH, service.end("a", "other"))
        assertEquals(Outcome.NOT_IN_MATCH, service.end("nobody", "m1"))

        assertEquals(PlayerState.IN_MATCH, states.find("a")?.state)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `given both leave concurrently then exactly one ENDED and one event`() {
        repeat(50) { round ->
            val id = "m$round"
            playing(id)
            published.clear()

            val pool = Executors.newFixedThreadPool(2)
            val outcomes = java.util.concurrent.CopyOnWriteArrayList<Outcome>()
            pool.submit { outcomes += service.end("a", id) }
            pool.submit { outcomes += service.end("b", id) }
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)

            assertEquals(1, outcomes.count { it == Outcome.ENDED }, "round $round: $outcomes")
            assertEquals(1, published.size, "round $round")
            assertNull(states.find("a")); assertNull(states.find("b"))
        }
    }
}
