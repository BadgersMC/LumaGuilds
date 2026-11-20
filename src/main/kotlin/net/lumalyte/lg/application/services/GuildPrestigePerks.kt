package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.rewards.PrestigePerks
import java.util.UUID

/**
 * REQ-180, REQ-181: a guild's prestige perks from its cached standing. Any
 * failure reads as "no perk".
 */
class GuildPrestigePerks(
    private val standings: GuildPrestigeStandings,
    private val perks: () -> PrestigePerks,
) {
    /** The perk table as configured now, for menus that describe a tier. */
    fun forConfig(): PrestigePerks = perks()

    fun allyHomeSlots(guildId: UUID): Int = perks().allyHomeSlots(tier(guildId))

    fun extraGuildStalls(guildId: UUID): Int = perks().extraGuildStalls(tier(guildId))

    /** REQ-242, REQ-245: whether the guild may put items into a second vault. */
    fun secondVault(guildId: UUID): Boolean = perks().secondVault(tier(guildId))

    private fun tier(guildId: UUID): Int? = runCatching { standings.standing(guildId)?.tier }.getOrNull()
}
