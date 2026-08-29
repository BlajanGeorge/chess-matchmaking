package com.chess.matching.event

import com.chess.matching.lobby.LobbyPlayer
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SpringEventMatchHandlerTest {

    private val now = Instant.parse("2026-08-29T10:00:00Z")
    private val published = mutableListOf<Any>()
    private val handler = SpringEventMatchHandler(ApplicationEventPublisher { published += it }, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `given a claimed pair when handled then one MatchFoundEvent with both players`() {
        val a = LobbyPlayer("a", 1500, now)
        val b = LobbyPlayer("b", 1510, now)

        handler.onMatched(a, b)
        handler.onMatched(a, b)

        val events = published.map { it as MatchFoundEvent }
        assertEquals(2, events.size)
        assertEquals(a, events[0].playerA)
        assertEquals(b, events[0].playerB)
        assertEquals(now, events[0].foundAt)
        assertNotEquals(events[0].matchId, events[1].matchId)
    }
}
