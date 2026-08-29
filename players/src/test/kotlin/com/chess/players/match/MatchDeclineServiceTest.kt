package com.chess.players.match

import com.chess.matching.lobby.InMemoryLobbyRepository
import com.chess.matching.lobby.LobbyPlayer
import com.chess.players.event.MatchCancelledEvent
import com.chess.players.match.MatchDeclineService.Outcome
import com.chess.players.state.InMemoryPlayerStateRepository
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchDeclineServiceTest {

    private val t0 = Instant.parse("2026-08-29T10:00:00Z")
    private val proposedAt = t0.plusSeconds(5)
    private val states = InMemoryPlayerStateRepository()
    private val pending = InMemoryPendingMatchRepository()
    private val lobby = InMemoryLobbyRepository()
    private val published = mutableListOf<Any>()
    private val service = MatchDeclineService(states, pending, lobby, { published += it })

    private val a = LobbyPlayer("a", 1500, t0)
    private val b = LobbyPlayer("b", 1510, t0.plusSeconds(2))

    private fun proposed() {
        states.save(PlayerStatus("a", PlayerState.PENDING, "m1", proposedAt))
        states.save(PlayerStatus("b", PlayerState.PENDING, "m1", proposedAt))
        pending.save(PendingMatch("m1", a, b, proposedAt))
    }

    @Test
    fun `given proposed match when a declines then a idle and b back in lobby with original joinedAt`() {
        proposed()

        assertEquals(Outcome.DECLINED, service.decline("a", "m1"))

        assertNull(states.find("a"))
        assertNull(lobby.find("a"))
        assertEquals(PlayerStatus("b", PlayerState.WAITING, since = b.joinedAt), states.find("b"))
        assertEquals(b, lobby.find("b"))
        assertNull(pending.find("m1"))
        assertEquals(
            listOf<Any>(MatchCancelledEvent("m1", MatchCancelledEvent.Reason.DECLINED, listOf("b"), listOf("a"))),
            published,
        )
    }

    @Test
    fun `given proposed match when b declines then a is the one returned`() {
        proposed()

        assertEquals(Outcome.DECLINED, service.decline("b", "m1"))

        assertNull(states.find("b"))
        assertEquals(a, lobby.find("a"))
    }

    @Test
    fun `given player waiting or idle when decline then NOT_PENDING_ON_MATCH and nothing changes`() {
        states.save(PlayerStatus("a", PlayerState.WAITING, since = t0))
        lobby.join(a)

        assertEquals(Outcome.NOT_PENDING_ON_MATCH, service.decline("a", "m1"))
        assertEquals(Outcome.NOT_PENDING_ON_MATCH, service.decline("nobody", "m1"))

        assertEquals(PlayerState.WAITING, states.find("a")?.state)
        assertEquals(a, lobby.find("a"))
    }

    @Test
    fun `given pending on a different match when decline stale matchId then NOT_PENDING_ON_MATCH`() {
        proposed()

        assertEquals(Outcome.NOT_PENDING_ON_MATCH, service.decline("a", "old-match"))

        assertEquals(PlayerState.PENDING, states.find("a")?.state)
        assertEquals(PlayerState.PENDING, states.find("b")?.state)
    }

    @Test
    fun `given match already resolved when decline then ALREADY_RESOLVED and state left alone`() {
        // the match is gone (second accept is mid-flight) while a still reads PENDING: decline must not touch a
        states.save(PlayerStatus("a", PlayerState.PENDING, "m1", proposedAt))

        assertEquals(Outcome.ALREADY_RESOLVED, service.decline("a", "m1"))

        assertEquals(PlayerState.PENDING, states.find("a")?.state)
    }

    @Test
    fun `given player already accepted when decline then ALREADY_ACCEPTED and nothing changes`() {
        proposed()
        pending.markAccepted("m1", "a")

        assertEquals(Outcome.ALREADY_ACCEPTED, service.decline("a", "m1"))

        assertEquals(PlayerState.PENDING, states.find("a")?.state)
        assertEquals(PlayerState.PENDING, states.find("b")?.state)
        assertEquals(setOf("a"), pending.find("m1")?.accepted)
        assertEquals(0, lobby.size())
    }

    @Test
    fun `given a accepted when b declines then b idle and a returned to lobby`() {
        proposed()
        pending.markAccepted("m1", "a")

        assertEquals(Outcome.DECLINED, service.decline("b", "m1"))

        assertNull(states.find("b"))
        assertEquals(PlayerState.WAITING, states.find("a")?.state)
        assertEquals(a, lobby.find("a"))
    }
}
