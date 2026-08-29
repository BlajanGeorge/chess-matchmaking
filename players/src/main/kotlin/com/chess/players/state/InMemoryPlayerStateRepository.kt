package com.chess.players.state

import java.util.concurrent.ConcurrentHashMap

/** Thread-safe in-memory state. Stand-in for Redis. */
class InMemoryPlayerStateRepository : PlayerStateRepository {

    private val statuses = ConcurrentHashMap<String, PlayerStatus>()

    override fun find(playerId: String): PlayerStatus? = statuses[playerId]

    override fun save(status: PlayerStatus) {
        statuses[status.playerId] = status
    }

    override fun saveIfAbsent(status: PlayerStatus): Boolean = statuses.putIfAbsent(status.playerId, status) == null

    override fun compareAndSet(playerId: String, expected: PlayerState, next: PlayerStatus): Boolean {
        require(next.playerId == playerId) { "status belongs to ${next.playerId}, not $playerId" }
        var swapped = false
        statuses.computeIfPresent(playerId) { _, current ->
            if (current.state == expected) { swapped = true; next } else current
        }
        return swapped
    }

    override fun compareAndSet(expected: PlayerStatus, next: PlayerStatus): Boolean {
        require(next.playerId == expected.playerId) { "status belongs to ${next.playerId}, not ${expected.playerId}" }
        return statuses.replace(expected.playerId, expected, next)
    }

    override fun removeIf(playerId: String, expected: PlayerState): Boolean {
        var removed = false
        statuses.computeIfPresent(playerId) { _, current ->
            if (current.state == expected) { removed = true; null } else current
        }
        return removed
    }

    override fun remove(playerId: String): Boolean = statuses.remove(playerId) != null
}
