package com.chess.gateway.ws

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerSessionRegistryTest {

    private val registry = PlayerSessionRegistry(jacksonObjectMapper())

    private fun session(id: String): WebSocketSession = mock {
        on { this.id } doReturn id
        on { isOpen } doReturn true
    }

    @Test
    fun `given registered player when send then json with type reaches the session`() {
        val s = session("s1")
        registry.register("a", s)

        assertTrue(registry.send("a", MatchProposed("m1", Opponent("b", 1500), 15)))

        val captor = argumentCaptor<TextMessage>()
        verify(s).sendMessage(captor.capture())
        assertEquals("""{"matchId":"m1","opponent":{"id":"b","rating":1500},"expiresInSeconds":15,"type":"MATCH_PROPOSED"}""", captor.firstValue.payload)
    }

    @Test
    fun `given player not connected when send then false`() {
        assertFalse(registry.send("nobody", Status(null, null)))
    }

    @Test
    fun `given second connection for same player when register then old one is closed and no longer receives`() {
        val old = session("s1"); val new = session("s2")
        registry.register("a", old)
        registry.register("a", new)

        verify(old).close(any())
        registry.send("a", Status(null, null))
        verify(old, never()).sendMessage(any())
        verify(new).sendMessage(any())
    }

    @Test
    fun `given replaced connection closes when unregister then successor stays registered`() {
        val old = session("s1"); val new = session("s2")
        registry.register("a", old)
        registry.register("a", new)

        registry.unregister("a", old)

        assertTrue(registry.isConnected("a"))
        registry.unregister("a", new)
        assertFalse(registry.isConnected("a"))
    }

    @Test
    fun `given send throws when send then false and registry survives`() {
        val s = session("s1")
        whenever(s.sendMessage(any())).thenThrow(RuntimeException("boom"))
        registry.register("a", s)

        assertFalse(registry.send("a", Status(null, null)))
        assertTrue(registry.isConnected("a"))
    }
}
