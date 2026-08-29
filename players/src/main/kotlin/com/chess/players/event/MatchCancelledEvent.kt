package com.chess.players.event

/**
 * A proposed match did not happen.
 *
 * @property returnedToLobby players put back into the lobby with their original wait time.
 * @property dropped players taken out of the flow entirely (they declined or never answered).
 */
data class MatchCancelledEvent(
    val matchId: String,
    val reason: Reason,
    val returnedToLobby: List<String>,
    val dropped: List<String>,
) {
    enum class Reason { DECLINED, TIMEOUT, PARTNER_UNAVAILABLE }
}
