package com.chess.players.match

import java.util.concurrent.ConcurrentHashMap

class InMemoryActiveMatchRepository : ActiveMatchRepository {
    private val matches = ConcurrentHashMap<String, ActiveMatch>()
    override fun find(matchId: String): ActiveMatch? = matches[matchId]
    override fun save(match: ActiveMatch) { matches[match.matchId] = match }
    override fun remove(matchId: String): ActiveMatch? = matches.remove(matchId)
}
