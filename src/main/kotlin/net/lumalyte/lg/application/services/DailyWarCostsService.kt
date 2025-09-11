package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Guild
import java.time.Instant
import java.util.UUID

/**
 * Service for managing daily war costs that are applied to guilds in active wars.
 */
interface DailyWarCostsService {

    /**
     * Applies daily war costs to all guilds currently in active wars.
     *
     * @return The number of guilds that had costs applied.
     */
    fun applyDailyWarCosts(): Int

    /**
     * Checks if daily war costs were already applied today for a guild.
     *
     * @param guildId The ID of the guild.
     * @return true if costs were already applied today, false otherwise.
     */
    fun wereCostsAppliedToday(guildId: UUID): Boolean

    /**
     * Records that daily war costs were applied to a guild.
     *
     * @param guildId The ID of the guild.
     * @param appliedAt The timestamp when costs were applied.
     * @return true if successful, false otherwise.
     */
    fun recordCostsApplied(guildId: UUID, appliedAt: Instant): Boolean

    /**
     * Gets the last time daily war costs were applied to a guild.
     *
     * @param guildId The ID of the guild.
     * @return The timestamp of the last application, or null if never applied.
     */
    fun getLastCostsAppliedTime(guildId: UUID): Instant?

    /**
     * Calculates the total daily war costs for a guild.
     *
     * @param guild The guild to calculate costs for.
     * @return A pair of (expCost, moneyCost) for the guild.
     */
    fun calculateDailyCosts(guild: Guild): Pair<Int, Int>

    /**
     * Gets all guilds that should have daily war costs applied.
     *
     * @return List of guild IDs that are in active wars.
     */
    fun getGuildsInActiveWars(): List<UUID>
}
