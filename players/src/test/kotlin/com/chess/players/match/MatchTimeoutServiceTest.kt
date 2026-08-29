package com.chess.players.match

import com.chess.matching.lobby.InMemoryLobbyRepository
import com.chess.matching.lobby.LobbyPlayer
import com.chess.players.event.MatchCancelledEvent
import com.chess.players.event.MatchStartedEvent
import org.springframework.context.ApplicationEventPublisher
import com.chess.players.state.InMemoryPlayerStateRepository
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStatus
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchTimeoutServiceTest {

    private val t0 = Instant.parse("2026-08-29T10:00:00Z")
    private val proposedAt = t0.plusSeconds(5)
    private val timeout = Duration.ofSeconds(15)
    private val states = InMemoryPlayerStateRepository()
    private val pending = InMemoryPendingMatchRepository()
    private val lobby = InMemoryLobbyRepository()

    private val a = LobbyPlayer("a", 1500, t0)
    private val b = LobbyPlayer("b", 1510, t0.plusSeconds(2))

    private val published = mutableListOf<Any>()
    private fun serviceAt(now: Instant): MatchTimeoutService {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val publisher = ApplicationEventPublisher { published += it }
        return MatchTimeoutService(states, pending, lobby, timeout, clock, publisher, MatchStartService(states, lobby, publisher, clock))
    }

    private fun proposed(accepted: Set<String> = emptySet()) {
        states.save(PlayerStatus("a", PlayerState.PENDING, "m1", proposedAt))
        states.save(PlayerStatus("b", PlayerState.PENDING, "m1", proposedAt))
        pending.save(PendingMatch("m1", a, b, proposedAt, accepted))
    }

    @Test
    fun `given match within timeout when sweep then untouched`() {
        proposed(accepted = setOf("a"))

        assertTrue(serviceAt(proposedAt.plus(timeout)).expireOverdue().isEmpty())

        assertEquals(PlayerState.PENDING, states.find("a")?.state)
        assertEquals("m1", pending.find("m1")?.matchId)
    }

    @Test
    fun `given overdue match with one accept when sweep then acceptor back in lobby and other dropped`() {
        proposed(accepted = setOf("a"))

        val expired = serviceAt(proposedAt.plus(timeout).plusMillis(1)).expireOverdue()

        assertEquals(listOf(MatchTimeoutService.Expired("m1", returnedToLobby = listOf("a"), dropped = listOf("b"))), expired)
        assertEquals(PlayerStatus("a", PlayerState.WAITING, since = a.joinedAt), states.find("a"))
        assertEquals(a, lobby.find("a"))
        assertNull(states.find("b"))
        assertNull(lobby.find("b"))
        assertNull(pending.find("m1"))
        assertEquals(
            listOf<Any>(MatchCancelledEvent("m1", MatchCancelledEvent.Reason.TIMEOUT, listOf("a"), listOf("b"))),
            published,
        )
    }

    @Test
    fun `given overdue match nobody accepted when sweep then both dropped`() {
        proposed()

        val expired = serviceAt(proposedAt.plus(timeout).plusSeconds(1)).expireOverdue()

        assertEquals(listOf("a", "b"), expired.single().dropped)
        assertNull(states.find("a")); assertNull(states.find("b"))
        assertEquals(0, lobby.size())
    }

    @Test
    fun `given overdue match both accepted when sweep then match is started not cancelled`() {
        proposed(accepted = setOf("a", "b"))
        val now = proposedAt.plus(timeout).plusMillis(1)

        val expired = serviceAt(now).expireOverdue()

        assertEquals(listOf(MatchTimeoutService.Expired("m1", started = true)), expired)
        assertEquals(PlayerStatus("a", PlayerState.IN_MATCH, "m1", now), states.find("a"))
        assertEquals(PlayerStatus("b", PlayerState.IN_MATCH, "m1", now), states.find("b"))
        assertEquals(0, lobby.size())
        assertNull(pending.find("m1"))
        assertEquals(listOf<Any>(MatchStartedEvent("m1", a, b, now)), published)
    }

    @Test
    fun `given player already moved on when sweep then that state is not clobbered`() {
        proposed(accepted = setOf("a"))
        // b somehow got a new state (e.g. stale pending after a race); must not be touched
        val bElsewhere = PlayerStatus("b", PlayerState.IN_MATCH, "other", t0)
        states.save(bElsewhere)

        serviceAt(proposedAt.plus(timeout).plusSeconds(1)).expireOverdue()

        assertEquals(bElsewhere, states.find("b"))
        assertEquals(a, lobby.find("a"))
    }
}
