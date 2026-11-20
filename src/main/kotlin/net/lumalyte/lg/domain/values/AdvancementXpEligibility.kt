package net.lumalyte.lg.domain.values

import java.util.UUID

/**
 * REQ-302: whether a member's completion of a guild advancement may pay their guild's XP. A member on
 * the same connection as another guild member who is online at that moment is most likely an alt, so
 * that completion pays nothing and the advancement stays open for the next member to pay it. Only the
 * addresses of players online right now are compared; nothing is stored. Pure.
 */
object AdvancementXpEligibility {
    enum class Decision {
        /** Pays as usual. */
        ELIGIBLE,
        /** Another online member of the guild shares this connection; the completion pays nothing. */
        SHARED,
        /**
         * This member's connection can't be read (a proxy that doesn't forward addresses). Callers pay
         * as usual: a player can't hide their own address, so this is a server setup issue, not a dodge.
         */
        UNKNOWN,
    }

    /**
     * @param actor the member who completed the advancement.
     * @param onlineMembers the guild's members online now (may include [actor]).
     * @param address each online player's connection, or null when it can't be read.
     * @param playersOn how many online players server-wide are on a connection.
     * @param gatewayPlayers a connection shared by at least this many online players counts as a
     *   shared gateway (a proxy, Bedrock bridge or school network) rather than one person, so it never
     *   blocks; 0 turns that exception off.
     */
    fun decide(
        actor: UUID,
        onlineMembers: Set<UUID>,
        address: (UUID) -> String?,
        playersOn: (String) -> Int,
        gatewayPlayers: Int,
    ): Decision {
        val own = address(actor)?.takeIf { it.isNotBlank() } ?: return Decision.UNKNOWN
        if (gatewayPlayers > 0 && playersOn(own) >= gatewayPlayers) return Decision.ELIGIBLE
        val peers = onlineMembers - actor
        if (peers.any { address(it) == own }) return Decision.SHARED
        return Decision.ELIGIBLE
    }
}
