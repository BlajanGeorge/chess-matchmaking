package com.chess.players.match

import com.chess.matching.event.MatchFoundEvent
import com.chess.matching.lobby.InMemoryLobbyRepository
import com.chess.matching.lobby.LobbyPlayer
import com.chess.players.event.MatchCancelledEvent
import com.chess.players.event.MatchProposedEvent
import com.chess.players.match.MatchProposalService.Outcome
import com.chess.players.state.InMemoryPlayerStateRepository
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchProposalServiceTest {

    private val t0 = Instant.parse("2026-08-29T10:00:00Z")
    private val found = t0.plusSeconds(5)
    private val states = InMemoryPlayerStateRepository()
    private val lobby = InMemoryLobbyRepository()
    private val pending = InMemoryPendingMatchRepository()
    private val published = mutableListOf<Any>()
    private val service = MatchProposalService(states, pending, lobby, { published += it })

    private val a = LobbyPlayer("a", 1500, t0)
    private val b = LobbyPlayer("b", 1510, t0.plusSeconds(2))
    private val event = MatchFoundEvent("m1", a, b, found)

    private fun waiting(p: LobbyPlayer) = states.save(PlayerStatus(p.id, PlayerState.WAITING, since = p.joinedAt))

    @Test
    fun `given both waiting when match found then both PENDING on the match and lobby untouched`() {
        waiting(a); waiting(b)

        assertEquals(Outcome.PROPOSED, service.onMatchFound(event))

        assertEquals(PlayerStatus("a", PlayerState.PENDING, "m1", found), states.find("a"))
        assertEquals(PlayerStatus("b", PlayerState.PENDING, "m1", found), states.find("b"))
        assertEquals(PendingMatch("m1", a, b, found), pending.find("m1"))
        assertEquals(0, lobby.size())
        assertEquals(listOf<Any>(MatchProposedEvent("m1", a, b, found)), published)
    }

    @Test
    fun `given one player left when match found then other returned to lobby with original joinedAt`() {
        waiting(a) // b has no state: left

        assertEquals(Outcome.PARTNER_RETURNED, service.onMatchFound(event))

        assertEquals(PlayerStatus("a", PlayerState.WAITING, since = t0), states.find("a"))
        assertEquals(a, lobby.find("a"))
        assertNull(states.find("b"))
        assertNull(lobby.find("b"))
        assertNull(pending.find("m1"))
        assertEquals(
            listOf<Any>(MatchCancelledEvent("m1", MatchCancelledEvent.Reason.PARTNER_UNAVAILABLE, listOf("a"), emptyList())),
            published,
        )
    }

    @Test
    fun `given one player in unexpected state when match found then other returned and that state untouched`() {
        waiting(a)
        val bInMatch = PlayerStatus("b", PlayerState.IN_MATCH, "other", t0)
        states.save(bInMatch)

        assertEquals(Outcome.PARTNER_RETURNED, service.onMatchFound(event))

        assertEquals(PlayerState.WAITING, states.find("a")?.state)
        assertEquals(a, lobby.find("a"))
        assertEquals(bInMatch, states.find("b"))
    }

    @Test
    fun `given second player is the unavailable one when match found then first is the one returned`() {
        waiting(b)

        assertEquals(Outcome.PARTNER_RETURNED, service.onMatchFound(event))

        assertEquals(b, lobby.find("b"))
        assertNull(lobby.find("a"))
    }

    @Test
    fun `given player left and re-joined after the claim when match found then not moved and partner returned`() {
        waiting(a)
        // b left and re-joined after the claim: WAITING again, but with a newer joinedAt than the claimed entry
        val rejoinedAt = b.joinedAt.plusSeconds(1)
        states.save(PlayerStatus("b", PlayerState.WAITING, since = rejoinedAt))
        lobby.join(b.copy(joinedAt = rejoinedAt))

        assertEquals(Outcome.PARTNER_RETURNED, service.onMatchFound(event))

        // b keeps the new WAITING and stays in the lobby; a goes back with the original joinedAt
        assertEquals(PlayerStatus("b", PlayerState.WAITING, since = rejoinedAt), states.find("b"))
        assertEquals(rejoinedAt, lobby.find("b")?.joinedAt)
        assertEquals(PlayerStatus("a", PlayerState.WAITING, since = t0), states.find("a"))
        assertEquals(a, lobby.find("a"))
        assertNull(pending.find("m1"))
    }

    @Test
    fun `given neither waiting when match found then dropped and nothing changes`() {
        assertEquals(Outcome.DROPPED, service.onMatchFound(event))

        assertNull(states.find("a")); assertNull(states.find("b"))
        assertEquals(0, lobby.size())
    }
}
