package net.lumalyte.lg.application.services

import org.bukkit.entity.Player

/**
 * Gates guild XP earning on the player's live activity state, so players flagged
 * AFK or suspicious (autoclickers / macros / automated movement) earn no guild XP.
 */
interface PlaytimeActivityService {

    /**
     * True when the player's current activity state must not earn guild XP.
     * Always false (never blocks) when the underlying plugin is unavailable.
     */
    fun isXpBlocked(player: Player): Boolean
}
