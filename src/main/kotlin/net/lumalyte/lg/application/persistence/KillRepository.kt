package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.*
import java.time.Instant
import java.util.UUID

/**
 * Repository interface for kill data persistence.
 */
interface KillRepository {

    /**
     * Records a kill.
     *
     * @param kill The kill to record.
     * @return true if successful, false otherwise.
     */
    fun recordKill(kill: Kill): Boolean

    /**
     * Gets a kill by ID.
     *
     * @param killId The ID of the kill.
     * @return The kill if found, null otherwise.
     */
    fun getKillById(killId: UUID): Kill?

    /**
     * Gets kills by killer.
     *
     * @param killerId The ID of the killer.
     * @param limit The maximum number of kills to return.
     * @return List of kills by the killer.
     */
    fun getKillsByKiller(killerId: UUID, limit: Int = 100): List<Kill>

    /**
     * Gets kills by victim.
     *
     * @param victimId The ID of the victim.
     * @param limit The maximum number of kills to return.
     * @return List of kills where the player was the victim.
     */
    fun getKillsByVictim(victimId: UUID, limit: Int = 100): List<Kill>

    /**
     * Gets kills for a guild.
     *
     * @param guildId The ID of the guild.
     * @param limit The maximum number of kills to return.
     * @return List of kills involving the guild.
     */
    fun getKillsForGuild(guildId: UUID, limit: Int = 100): List<Kill>

    /**
     * Gets kills between two guilds.
     *
     * @param guildA The first guild.
     * @param guildB The second guild.
     * @param limit The maximum number of kills to return.
     * @return List of kills between the guilds.
     */
    fun getKillsBetweenGuilds(guildA: UUID, guildB: UUID, limit: Int = 100): List<Kill>

    /**
     * Gets guild kill statistics.
     *
     * @param guildId The ID of the guild.
     * @return The guild's kill statistics.
     */
    fun getGuildKillStats(guildId: UUID): GuildKillStats

    /**
     * Updates guild kill statistics.
     *
     * @param stats The updated statistics.
     * @return true if successful, false otherwise.
     */
    fun updateGuildKillStats(stats: GuildKillStats): Boolean

    /**
     * Gets player kill statistics.
     *
     * @param playerId The ID of the player.
     * @return The player's kill statistics.
     */
    fun getPlayerKillStats(playerId: UUID): PlayerKillStats

    /**
     * Updates player kill statistics.
     *
     * @param stats The updated statistics.
     * @return true if successful, false otherwise.
     */
    fun updatePlayerKillStats(stats: PlayerKillStats): Boolean

    /**
     * Gets anti-farm data for a player.
     *
     * @param playerId The ID of the player.
     * @return The player's anti-farm data.
     */
    fun getAntiFarmData(playerId: UUID): AntiFarmData

    /**
     * Updates anti-farm data for a player.
     *
     * @param data The updated anti-farm data.
     * @return true if successful, false otherwise.
     */
    fun updateAntiFarmData(data: AntiFarmData): Boolean

    /**
     * Gets kills within a time period.
     *
     * @param startTime The start of the period.
     * @param endTime The end of the period.
     * @param limit The maximum number of kills to return.
     * @return List of kills within the period.
     */
    fun getKillsInPeriod(startTime: Instant, endTime: Instant, limit: Int = 1000): List<Kill>

    /**
     * Gets kills for a guild within a time period.
     *
     * @param guildId The ID of the guild.
     * @param startTime The start of the period.
     * @param endTime The end of the period.
     * @param limit The maximum number of kills to return.
     * @return List of kills for the guild within the period.
     */
    fun getGuildKillsInPeriod(guildId: UUID, startTime: Instant, endTime: Instant, limit: Int = 100): List<Kill>

    /**
     * Gets the total number of kills.
     *
     * @return The total number of kills recorded.
     */
    fun getTotalKillCount(): Int

    /**
     * Gets the total number of kills for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The total number of kills for the guild.
     */
    fun getGuildKillCount(guildId: UUID): Int

    /**
     * Deletes old kill records beyond a certain age.
     *
     * @param maxAgeDays The maximum age in days for kill records.
     * @return The number of records deleted.
     */
    fun deleteOldKills(maxAgeDays: Int = 90): Int

    /**
     * Resets all kill statistics for a guild.
     *
     * @param guildId The ID of the guild.
     * @return true if successful, false otherwise.
     */
    fun resetGuildKillStats(guildId: UUID): Boolean

    /**
     * Resets all kill statistics for a player.
     *
     * @param playerId The ID of the player.
     * @return true if successful, false otherwise.
     */
    fun resetPlayerKillStats(playerId: UUID): Boolean
}
