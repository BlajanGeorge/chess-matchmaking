package com.chess.gateway.ws

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import java.util.concurrent.ConcurrentHashMap

/**
 * `playerId → session`. One live connection per player: a new one replaces (and closes) the old.
 * Stand-in for the `playerId → gatewayId` registry once there are many gateways.
 */
class PlayerSessionRegistry(private val mapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    fun register(playerId: String, session: WebSocketSession): WebSocketSession {
        val wrapped = ConcurrentWebSocketSessionDecorator(session, SEND_TIMEOUT_MS, SEND_BUFFER_BYTES)
        sessions.put(playerId, wrapped)?.let { old ->
            runCatching { old.close(CloseStatus.POLICY_VIOLATION.withReason("replaced by a newer connection")) }
        }
        return wrapped
    }

    /** Removes only if [session] is still the registered one — a replaced connection closing must not evict its successor. */
    fun unregister(playerId: String, session: WebSocketSession) {
        sessions.computeIfPresent(playerId) { _, current -> if (current.id == session.id) null else current }
    }

    fun isConnected(playerId: String) = sessions.containsKey(playerId)

    /** Best effort: a player who is not connected simply misses the push and catches up via STATUS on reconnect. */
    fun send(playerId: String, message: ServerMessage): Boolean {
        val session = sessions[playerId] ?: return false
        return try {
            session.sendMessage(TextMessage(mapper.writeValueAsString(message)))
            true
        } catch (e: Exception) {
            log.warn("send to {} failed: {}", playerId, e.toString())
            false
        }
    }

    private companion object {
        const val SEND_TIMEOUT_MS = 2_000
        const val SEND_BUFFER_BYTES = 512 * 1024
    }
}
