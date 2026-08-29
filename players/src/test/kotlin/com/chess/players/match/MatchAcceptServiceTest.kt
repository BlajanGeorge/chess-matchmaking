package com.chess.players.match

import com.chess.matching.lobby.InMemoryLobbyRepository
import com.chess.matching.lobby.LobbyPlayer
import com.chess.players.event.MatchCancelledEvent
import com.chess.players.event.MatchStartedEvent
import com.chess.players.match.MatchAcceptService.Outcome
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

class MatchAcceptServiceTest {

    private val t0 = Instant.parse("2026-08-29T10:00:00Z")
    private val proposedAt = t0.plusSeconds(5)
    private val now = t0.plusSeconds(9)
    private val states = InMemoryPlayerStateRepository()
    private val pending = InMemoryPendingMatchRepository()
    private val published = java.util.concurrent.CopyOnWriteArrayList<Any>()
    private val lobby = InMemoryLobbyRepository()
    private val starter = MatchStartService(states, lobby, { published += it }, Clock.fixed(now, ZoneOffset.UTC))
    private val service = MatchAcceptService(states, pending, starter)

    private val a = LobbyPlayer("a", 1500, t0)
    private val b = LobbyPlayer("b", 1510, t0.plusSeconds(2))

    private fun proposed() {
        states.save(PlayerStatus("a", PlayerState.PENDING, "m1", proposedAt))
        states.save(PlayerStatus("b", PlayerState.PENDING, "m1", proposedAt))
        pending.save(PendingMatch("m1", a, b, proposedAt))
    }

    @Test
    fun `given proposed match when first accepts then recorded and still pending`() {
        proposed()

        assertEquals(Outcome.ACCEPTED_WAITING_FOR_PARTNER, service.accept("a", "m1"))

        assertEquals(setOf("a"), pending.find("m1")?.accepted)
        assertEquals(PlayerState.PENDING, states.find("a")?.state)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `given same player accepts twice then still waiting for partner`() {
        proposed()
        service.accept("a", "m1")

        assertEquals(Outcome.ACCEPTED_WAITING_FOR_PARTNER, service.accept("a", "m1"))
        assertEquals(setOf("a"), pending.find("m1")?.accepted)
    }

    @Test
    fun `given first accepted when second accepts then match starts for both`() {
        proposed()
        service.accept("a", "m1")

        assertEquals(Outcome.MATCH_STARTED, service.accept("b", "m1"))

        assertEquals(PlayerStatus("a", PlayerState.IN_MATCH, "m1", now), states.find("a"))
        assertEquals(PlayerStatus("b", PlayerState.IN_MATCH, "m1", now), states.find("b"))
        assertNull(pending.find("m1"))
        assertEquals(listOf<Any>(MatchStartedEvent("m1", a, b, now)), published.toList())
    }

    @Test
    fun `given player not pending on the match when accept then NOT_PENDING_ON_MATCH`() {
        proposed()
        states.save(PlayerStatus("c", PlayerState.WAITING, since = t0))

        assertEquals(Outcome.NOT_PENDING_ON_MATCH, service.accept("c", "m1"))
        assertEquals(Outcome.NOT_PENDING_ON_MATCH, service.accept("a", "other"))
        assertEquals(Outcome.NOT_PENDING_ON_MATCH, service.accept("nobody", "m1"))
        assertTrue(pending.find("m1")!!.accepted.isEmpty())
    }

    @Test
    fun `given match already resolved when accept then ALREADY_RESOLVED`() {
        states.save(PlayerStatus("a", PlayerState.PENDING, "m1", proposedAt))

        assertEquals(Outcome.ALREADY_RESOLVED, service.accept("a", "m1"))
        assertTrue(published.isEmpty())
    }

    @Test
    fun `given partner state vanished before start when second accepts then no match starts and acceptor returns to lobby`() {
        proposed()
        service.accept("a", "m1")
        states.remove("a") // a's state gone in the window before the start (should be unreachable now; safety net)

        assertEquals(Outcome.ALREADY_RESOLVED, service.accept("b", "m1"))

        assertNull(states.find("a"))
        assertEquals(PlayerStatus("b", PlayerState.WAITING, since = b.joinedAt), states.find("b"))
        assertEquals(b, lobby.find("b"))
        assertNull(pending.find("m1"))
        assertEquals(
            listOf<Any>(MatchCancelledEvent("m1", MatchCancelledEvent.Reason.PARTNER_UNAVAILABLE, listOf("b"), emptyList())),
            published.toList(),
        )
    }

    @Test
    fun `given both accept concurrently then exactly one MATCH_STARTED and one event`() {
        repeat(50) { round ->
            val id = "m$round"
            states.save(PlayerStatus("a", PlayerState.PENDING, id, proposedAt))
            states.save(PlayerStatus("b", PlayerState.PENDING, id, proposedAt))
            pending.save(PendingMatch(id, a, b, proposedAt))
            published.clear()

            val pool = Executors.newFixedThreadPool(2)
            val outcomes = java.util.concurrent.CopyOnWriteArrayList<Outcome>()
            pool.submit { outcomes += service.accept("a", id) }
            pool.submit { outcomes += service.accept("b", id) }
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)

            assertEquals(1, outcomes.count { it == Outcome.MATCH_STARTED }, "round $round: $outcomes")
            assertEquals(1, published.size, "round $round")
            assertEquals(PlayerState.IN_MATCH, states.find("a")?.state)
            assertEquals(PlayerState.IN_MATCH, states.find("b")?.state)
        }
    }
}
