// Public, cross-plugin integration API (REQ-181), shared through Paper's join-classpath.
@file:Suppress("LibraryEntitiesShouldNotBePublic")

package net.lumalyte.lg.api

import java.util.UUID

/** Prestige perks other plugins honour, such as EnthusiaMarket's guild stall cap. */
interface GuildPrestigePerkLookup {
    /** Guild-owned stalls the guild may own beyond the market's base cap; 0 when unknown. */
    fun extraGuildStalls(guildId: UUID): Int
}

/** LumaGuilds-owned implementation backed by the cached prestige standing. */
class GuildPrestigePerkLookupImpl(
    private val perks: net.lumalyte.lg.application.services.GuildPrestigePerks,
) : GuildPrestigePerkLookup {
    override fun extraGuildStalls(guildId: UUID): Int = runCatching { perks.extraGuildStalls(guildId) }.getOrDefault(0)
}
