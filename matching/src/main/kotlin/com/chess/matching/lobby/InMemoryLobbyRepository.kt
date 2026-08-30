package com.chess.matching.lobby

import java.util.concurrent.ConcurrentHashMap

/** Thread-safe in-memory lobby. Stand-in for the Redis sorted set. */
class InMemoryLobbyRepository : LobbyRepository {

    private val players = ConcurrentHashMap<String, LobbyPlayer>()

    override fun join(player: LobbyPlayer) {
        players[player.id] = player
    }

    override fun leave(playerId: String): Boolean = players.remove(playerId) != null

    override fun find(playerId: String): LobbyPlayer? = players[playerId]

    override fun snapshot(): List<LobbyPlayer> = players.values.sortedBy { it.rating }

    @Synchronized
    override fun claim(a: LobbyPlayer, b: LobbyPlayer): Boolean {
        if (a.id == b.id) return false
        if (players[a.id] != a || players[b.id] != b) return false
        players.remove(a.id)
        players.remove(b.id)
        return true
    }

    override fun size(): Int = players.size
}
