package com.chess.players.match

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryPendingMatchRepository : PendingMatchRepository {
    private val matches = ConcurrentHashMap<String, PendingMatch>()
    override fun find(matchId: String): PendingMatch? = matches[matchId]
    override fun findProposedBefore(before: Instant): List<PendingMatch> =
        matches.values.filter { it.proposedAt < before }.sortedBy { it.proposedAt }
    override fun save(match: PendingMatch) { matches[match.matchId] = match }
    override fun remove(matchId: String): PendingMatch? = matches.remove(matchId)
    override fun markAccepted(matchId: String, playerId: String): PendingMatch? =
        matches.computeIfPresent(matchId) { _, m -> m.copy(accepted = m.accepted + playerId) }
}
