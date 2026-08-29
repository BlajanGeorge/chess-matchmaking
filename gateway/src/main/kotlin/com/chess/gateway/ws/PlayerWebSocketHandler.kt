package com.chess.gateway.ws

import com.chess.players.event.PlayerAcceptedMatchEvent
import com.chess.players.event.PlayerDeclinedMatchEvent
import com.chess.players.event.PlayerJoinedLobbyEvent
import com.chess.players.event.PlayerLeftLobbyEvent
import com.chess.players.state.PlayerStateRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.util.UriComponentsBuilder

/**
 * `/ws?playerId=...`. Commands become player events; after every command (and on connect) the player gets a STATUS
 * so the client never depends on a push it might have missed.
 */
class PlayerWebSocketHandler(
    private val registry: PlayerSessionRegistry,
    private val states: PlayerStateRepository,
    private val publisher: ApplicationEventPublisher,
    private val mapper: ObjectMapper,
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val playerId = playerIdOf(session)
        if (playerId == null) {
            session.close(CloseStatus.BAD_DATA.withReason("playerId query parameter is required"))
            return
        }
        session.attributes[PLAYER_ID] = playerId
        registry.register(playerId, session)
        log.debug("player {} connected ({})", playerId, session.id)
        sendStatus(playerId)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val playerId = session.attributes[PLAYER_ID] as? String ?: return
        val command = try {
            mapper.readValue<Command>(message.payload)
        } catch (e: Exception) {
            registry.send(playerId, Error("unreadable command: ${e.message}"))
            return
        }
        when (command.type) {
            Command.Type.JOIN_LOBBY -> publisher.publishEvent(PlayerJoinedLobbyEvent(playerId))
            Command.Type.LEAVE_LOBBY -> publisher.publishEvent(PlayerLeftLobbyEvent(playerId))
            Command.Type.ACCEPT_MATCH -> withMatchId(playerId, command) { publisher.publishEvent(PlayerAcceptedMatchEvent(playerId, it)) }
            Command.Type.DECLINE_MATCH -> withMatchId(playerId, command) { publisher.publishEvent(PlayerDeclinedMatchEvent(playerId, it)) }
            Command.Type.STATUS -> Unit
        }
        sendStatus(playerId)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val playerId = session.attributes[PLAYER_ID] as? String ?: return
        registry.unregister(playerId, session)
        log.debug("player {} disconnected ({}): {}", playerId, session.id, status)
    }

    private fun withMatchId(playerId: String, command: Command, action: (String) -> Unit) {
        val matchId = command.matchId
        if (matchId.isNullOrBlank()) registry.send(playerId, Error("${command.type} requires matchId")) else action(matchId)
    }

    private fun sendStatus(playerId: String) {
        val status = states.find(playerId)
        registry.send(playerId, Status(status?.state, status?.matchId))
    }

    private fun playerIdOf(session: WebSocketSession): String? =
        session.uri?.let { UriComponentsBuilder.fromUri(it).build().queryParams.getFirst("playerId") }?.takeIf { it.isNotBlank() }

    private companion object {
        const val PLAYER_ID = "playerId"
    }
}
