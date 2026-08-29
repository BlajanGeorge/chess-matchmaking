package com.chess.matching.matcher

import com.chess.matching.lobby.LobbyPlayer

/**
 * Exact minimum-cost matching for a one-dimensional cost (rating gap), with a per-player penalty for staying unmatched.
 *
 * Sort by rating, then DP over the sorted list: the last player either stays alone, or pairs with an earlier player
 * `j` with everyone strictly between them left alone. Crossing pairs are never optimal on a line, so this covers every
 * candidate optimum. The inner loop stops once the rating gap exceeds `2 * maxPenalty`, beyond which pairing can never
 * beat leaving both alone — with capped penalties the whole thing is effectively O(n log n).
 */
class RatingMatcher(private val alonePenalty: (LobbyPlayer) -> Double) {

    data class Result(val pairs: List<Pair<LobbyPlayer, LobbyPlayer>>, val unmatched: List<LobbyPlayer>, val cost: Double)

    fun match(players: List<LobbyPlayer>): Result {
        if (players.isEmpty()) return Result(emptyList(), emptyList(), 0.0)

        val s = players.sortedBy { it.rating }
        val n = s.size
        val penalty = DoubleArray(n) { alonePenalty(s[it]).also { p -> require(p >= 0) { "penalty must be >= 0" } } }
        val maxGap = 2 * penalty.max()

        val prefixPenalty = DoubleArray(n + 1)
        for (i in 0 until n) prefixPenalty[i + 1] = prefixPenalty[i] + penalty[i]

        // dp[i] = min cost for s[0 until i]; pairedWith[i] = index paired with s[i-1], or -1 if s[i-1] stays alone
        val dp = DoubleArray(n + 1)
        val pairedWith = IntArray(n + 1) { -1 }

        for (i in 1..n) {
            val last = i - 1
            dp[i] = dp[i - 1] + penalty[last]
            var j = last - 1
            while (j >= 0 && s[last].rating - s[j].rating <= maxGap) {
                val between = prefixPenalty[last] - prefixPenalty[j + 1]
                val candidate = dp[j] + (s[last].rating - s[j].rating) + between
                if (candidate < dp[i]) {
                    dp[i] = candidate
                    pairedWith[i] = j
                }
                j--
            }
        }

        val pairs = ArrayList<Pair<LobbyPlayer, LobbyPlayer>>(n / 2)
        val unmatched = ArrayList<LobbyPlayer>()
        var i = n
        while (i > 0) {
            val j = pairedWith[i]
            if (j < 0) {
                unmatched += s[i - 1]
                i--
            } else {
                pairs += s[j] to s[i - 1]
                for (k in i - 2 downTo j + 1) unmatched += s[k]
                i = j
            }
        }
        return Result(pairs, unmatched, dp[n])
    }
}
