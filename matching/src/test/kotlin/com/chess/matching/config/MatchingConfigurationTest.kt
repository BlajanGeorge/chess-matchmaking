package com.chess.matching.config

import com.chess.matching.event.MatchFoundEvent
import com.chess.matching.lobby.LobbyPlayer
import com.chess.matching.lobby.LobbyRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.context.event.EventListener
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals

@SpringJUnitConfig(MatchingConfiguration::class, MatchingConfigurationTest.Listener::class)
@TestPropertySource(properties = ["matching.interval=50ms", "matching.base-penalty=25"])
class MatchingConfigurationTest {

    @Component
    class Listener {
        val received = CopyOnWriteArrayList<MatchFoundEvent>()

        @EventListener
        fun on(event: MatchFoundEvent) { received += event }
    }

    @Autowired lateinit var lobby: LobbyRepository
    @Autowired lateinit var listener: Listener

    @Test
    fun `given running context when two close players join then a MatchFoundEvent reaches a listener in another bean`() {
        lobby.join(LobbyPlayer("a", 1500, Instant.now()))
        lobby.join(LobbyPlayer("b", 1505, Instant.now()))

        val deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos()
        while (listener.received.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10)

        assertEquals(1, listener.received.size)
        assertEquals(setOf("a", "b"), listener.received[0].let { setOf(it.playerA.id, it.playerB.id) })
        assertEquals(0, lobby.size())
    }
}
