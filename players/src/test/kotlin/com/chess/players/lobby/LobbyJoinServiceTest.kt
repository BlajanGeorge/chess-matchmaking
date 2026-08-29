package com.chess.players.lobby

import com.chess.matching.lobby.InMemoryLobbyRepository
import com.chess.players.lobby.LobbyJoinService.Outcome
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

class LobbyJoinServiceTest {

    private val t0 = Instant.parse("2026-08-29T10:00:00Z")
    private var clock = Clock.fixed(t0, ZoneOffset.UTC)
    private val states = InMemoryPlayerStateRepository()
    private val lobby = InMemoryLobbyRepository()
    private val ratings = mutableMapOf("a" to 1500)
    private val service = LobbyJoinService(states, lobby, { ratings.getValue(it) }, object : Clock() {
        override fun instant() = clock.instant()
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
    })

    @Test
    fun `given idle player when join then WAITING with server time and provider rating in lobby`() {
        assertEquals(Outcome.JOINED, service.join("a"))

        assertEquals(PlayerStatus("a", PlayerState.WAITING, since = t0), states.find("a"))
        assertEquals(1500, lobby.find("a")?.rating)
        assertEquals(t0, lobby.find("a")?.joinedAt)
    }

    @Test
    fun `given already waiting when join again later then no-op and original joinedAt kept`() {
        service.join("a")
        clock = Clock.offset(clock, Duration.ofSeconds(30))
        ratings["a"] = 1600

        assertEquals(Outcome.ALREADY_WAITING, service.join("a"))

        assertEquals(t0, states.find("a")?.since)
        assertEquals(t0, lobby.find("a")?.joinedAt)
        assertEquals(1500, lobby.find("a")?.rating)
    }

    @Test
    fun `given WAITING but missing from lobby when join then lobby entry restored with original joinedAt`() {
        states.save(PlayerStatus("a", PlayerState.WAITING, since = t0))
        clock = Clock.offset(clock, Duration.ofSeconds(40))

        assertEquals(Outcome.ALREADY_WAITING, service.join("a"))

        assertEquals(t0, lobby.find("a")?.joinedAt)
        assertEquals(1500, lobby.find("a")?.rating)
        assertEquals(t0, states.find("a")?.since)
    }

    @Test
    fun `given pending or in match when join then rejected and not put in lobby`() {
        states.save(PlayerStatus("a", PlayerState.PENDING, "m1", t0))
        states.save(PlayerStatus("b", PlayerState.IN_MATCH, "m2", t0))

        assertEquals(Outcome.REJECTED_BUSY, service.join("a"))
        assertEquals(Outcome.REJECTED_BUSY, service.join("b"))

        assertNull(lobby.find("a"))
        assertNull(lobby.find("b"))
        assertEquals(PlayerState.PENDING, states.find("a")?.state)
    }
}
