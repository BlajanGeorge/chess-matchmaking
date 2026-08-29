package com.chess.players.lobby

import com.chess.matching.lobby.InMemoryLobbyRepository
import com.chess.matching.lobby.LobbyPlayer
import com.chess.players.match.InMemoryPendingMatchRepository
import com.chess.players.match.MatchDeclineService
import com.chess.players.match.PendingMatch
import com.chess.players.lobby.LobbyLeaveService.Outcome
import com.chess.players.state.InMemoryPlayerStateRepository
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LobbyLeaveServiceTest {

    private val t0 = Instant.parse("2026-08-29T10:00:00Z")
    private val states = InMemoryPlayerStateRepository()
    private val lobby = InMemoryLobbyRepository()
    private val pending = InMemoryPendingMatchRepository()
    private val service = LobbyLeaveService(states, lobby, MatchDeclineService(states, pending, lobby, {}))

    @Test
    fun `given waiting player when leave then removed from lobby and state`() {
        states.save(PlayerStatus("a", PlayerState.WAITING, since = t0))
        lobby.join(LobbyPlayer("a", 1500, t0))

        assertEquals(Outcome.LEFT, service.leave("a"))

        assertNull(states.find("a"))
        assertNull(lobby.find("a"))
    }

    @Test
    fun `given idle player when leave then NOT_WAITING and nothing changes`() {
        assertEquals(Outcome.NOT_WAITING, service.leave("a"))
    }

    @Test
    fun `given pending player when leave then it counts as a decline and partner is returned`() {
        val a = LobbyPlayer("a", 1500, t0)
        val b = LobbyPlayer("b", 1510, t0)
        states.save(PlayerStatus("a", PlayerState.PENDING, "m1", t0))
        states.save(PlayerStatus("b", PlayerState.PENDING, "m1", t0))
        pending.save(PendingMatch("m1", a, b, t0))

        assertEquals(Outcome.DECLINED_PENDING_MATCH, service.leave("a"))

        assertNull(states.find("a"))
        assertEquals(PlayerState.WAITING, states.find("b")?.state)
        assertEquals(b, lobby.find("b"))
        assertNull(pending.find("m1"))
    }

    @Test
    fun `given player in match when leave then IN_MATCH and state kept`() {
        val inMatch = PlayerStatus("a", PlayerState.IN_MATCH, "m1", t0)
        states.save(inMatch)

        assertEquals(Outcome.IN_MATCH, service.leave("a"))
        assertEquals(inMatch, states.find("a"))
    }
}
