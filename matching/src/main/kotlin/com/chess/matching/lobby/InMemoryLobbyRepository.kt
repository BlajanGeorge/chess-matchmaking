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
    override fun claim(playerIdA: String, playerIdB: String): Boolean {
        if (playerIdA == playerIdB) return false
        if (!players.containsKey(playerIdA) || !players.containsKey(playerIdB)) return false
        players.remove(playerIdA)
        players.remove(playerIdB)
        return true
    }

    override fun size(): Int = players.size
}
