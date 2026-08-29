package com.chess.app

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.net.URI
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["matching.interval=100ms", "matching.base-penalty=5000", "matching.max-penalty=5000", "players.accept-timeout=30s"],
)
class EndToEndTest {

    @LocalServerPort var port: Int = 0
    @Autowired lateinit var mapper: ObjectMapper
    private val clients = mutableListOf<Client>()

    inner class Client(val playerId: String) {
        val inbox = LinkedBlockingQueue<JsonNode>()
        val session: WebSocketSession = StandardWebSocketClient().execute(
            object : TextWebSocketHandler() {
                override fun handleTextMessage(session: WebSocketSession, message: TextMessage) { inbox.add(mapper.readTree(message.payload)) }
            },
            null,
            URI("ws://localhost:$port/ws?playerId=$playerId"),
        ).get(5, TimeUnit.SECONDS)

        fun send(type: String, matchId: String? = null) =
            session.sendMessage(TextMessage(mapper.writeValueAsString(mapOf("type" to type, "matchId" to matchId))))

        private val unread = mutableListOf<JsonNode>()

        /** Waits for a STATUS, then keeps draining briefly and returns the newest one seen (older ones are stale). */
        fun latestStatus(): JsonNode {
            unread.add(0, expect("STATUS")) // ensure at least one; put it back at its (oldest) position
            while (true) {
                val next = inbox.poll(300, TimeUnit.MILLISECONDS) ?: break
                unread.add(next)
            }
            val statuses = unread.filter { it["type"].asText() == "STATUS" }
            unread.removeAll(statuses)
            return statuses.last()
        }

        /**
         * First unread message of the given type. Other types are kept, not dropped: STATUS echoes (sync, on the
         * request thread) and pushes (async, on the notification executor) arrive in no fixed order.
         */
        fun expect(type: String): JsonNode {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (true) {
                val i = unread.indexOfFirst { it["type"].asText() == type }
                if (i >= 0) return unread.removeAt(i)
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) error("$playerId: no $type message within 5s; unread=${unread.map { it["type"].asText() }}")
                inbox.poll(remaining, TimeUnit.NANOSECONDS)?.let { unread.add(it) }
            }
        }
    }

    private fun connect(playerId: String) = Client(playerId).also { clients += it }

    @AfterEach
    fun closeAll() = clients.forEach { runCatching { it.session.close() } }

    @Test
    fun `two players connect, join, get proposed, both accept, match starts`() {
        val a = connect("e2e-a"); val b = connect("e2e-b")
        assertNull(a.latestStatus()["state"].takeUnless { it.isNull })

        a.send("JOIN_LOBBY"); b.send("JOIN_LOBBY")
        assertTrue(a.latestStatus()["state"].asText() in setOf("WAITING", "PENDING"))

        val proposedA = a.expect("MATCH_PROPOSED"); val proposedB = b.expect("MATCH_PROPOSED")
        val matchId = proposedA["matchId"].asText()
        assertEquals(matchId, proposedB["matchId"].asText())
        assertEquals("e2e-b", proposedA["opponent"]["id"].asText())
        assertEquals("e2e-a", proposedB["opponent"]["id"].asText())

        a.send("ACCEPT_MATCH", matchId)
        assertEquals("PENDING", a.latestStatus()["state"].asText())

        b.send("ACCEPT_MATCH", matchId)
        assertEquals(matchId, a.expect("MATCH_STARTED")["matchId"].asText())
        assertEquals(matchId, b.expect("MATCH_STARTED")["matchId"].asText())
        assertEquals("IN_MATCH", b.latestStatus()["state"].asText())
    }

    @Test
    fun `one declines, the other is told and is back in the lobby`() {
        val a = connect("e2e-c"); val b = connect("e2e-d")
        a.send("JOIN_LOBBY"); b.send("JOIN_LOBBY")
        val matchId = a.expect("MATCH_PROPOSED")["matchId"].asText()
        b.expect("MATCH_PROPOSED")

        a.send("DECLINE_MATCH", matchId)

        val cancelledA = a.expect("MATCH_CANCELLED"); val cancelledB = b.expect("MATCH_CANCELLED")
        assertEquals("DECLINED", cancelledA["reason"].asText())
        assertTrue(!cancelledA["backInLobby"].asBoolean())
        assertTrue(cancelledB["backInLobby"].asBoolean())
        assertNull(a.latestStatus()["state"].takeUnless { it.isNull })

        b.send("STATUS")
        assertEquals("WAITING", b.latestStatus()["state"].asText())
        b.send("LEAVE_LOBBY")
        assertNull(b.latestStatus()["state"].takeUnless { it.isNull })
    }

    @Test
    fun `accept without matchId is an error`() {
        val a = connect("e2e-e")
        a.send("ACCEPT_MATCH")
        assertTrue(a.expect("ERROR")["message"].asText().contains("matchId"))
    }
}
