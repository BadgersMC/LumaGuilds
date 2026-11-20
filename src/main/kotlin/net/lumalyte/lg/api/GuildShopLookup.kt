package net.lumalyte.lg.api

import java.util.UUID

/**
 * REQ-195: registered in Bukkit's ServicesManager by a market plugin (EnthusiaMarket) so the guild
 * page can say whether a guild owns a shop stall. When nothing is registered the line is left out.
 */
fun interface GuildShopLookup {
    /** Whether [guildId] owns at least one shop stall (owned or in its grace period). */
    fun ownsShop(guildId: UUID): Boolean
}
