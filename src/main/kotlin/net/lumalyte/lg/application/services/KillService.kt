package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.Position3D
import java.time.Instant
import java.util.UUID

/**
 * Service interface for managing kill tracking and statistics.
 */
interface KillService {

    /**
     * Records a player kill.
     *
     * @param killerId The ID of the killer.
     * @param victimId The ID of the victim.
     * @param weapon The weapon used (optional).
     * @param location The location of the kill (optional).
     * @return The recorded kill if successful, null if blocked by anti-farm or other reasons.
     */
    fun recordKill(killerId: UUID, victimId: UUID, weapon: String? = null, worldId: UUID? = null, location: Position3D? = null): Kill?

    /**
     * Gets kill statistics for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The guild's kill statistics.
     */
    fun getGuildKillStats(guildId: UUID): GuildKillStats

    /**
     * Gets kill statistics for a player.
     *
     * @param playerId The ID of the player.
     * @return The player's kill statistics.
     */
    fun getPlayerKillStats(playerId: UUID): PlayerKillStats

    /**
     * Gets recent kills for a guild.
     *
     * @param guildId The ID of the guild.
     * @param limit The maximum number of kills to return.
     * @return List of recent kills.
     */
    fun getRecentGuildKills(guildId: UUID, limit: Int = 50): List<Kill>

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
     * Checks if a kill would be considered farming.
     *
     * @param killerId The ID of the potential killer.
     * @param victimId The ID of the potential victim.
     * @return true if the kill would be considered farming, false otherwise.
     */
    fun isFarmingKill(killerId: UUID, victimId: UUID): Boolean

    /**
     * Gets anti-farm data for a player.
     *
     * @param playerId The ID of the player.
     * @return The player's anti-farm data.
     */
    fun getAntiFarmData(playerId: UUID): AntiFarmData

    /**
     * Resets a player's farm score.
     *
     * @param playerId The ID of the player.
     * @return true if successful, false otherwise.
     */
    fun resetFarmScore(playerId: UUID): Boolean

    /**
     * Gets the top killers for a guild.
     *
     * @param guildId The ID of the guild.
     * @param limit The maximum number of players to return.
     * @return List of top killers with their stats.
     */
    fun getTopKillers(guildId: UUID, limit: Int = 10): List<Pair<UUID, PlayerKillStats>>

    /**
     * Gets kill statistics over a time period.
     *
     * @param guildId The ID of the guild.
     * @param startTime The start of the period.
     * @param endTime The end of the period.
     * @return Kill statistics for the period.
     */
    fun getKillStatsForPeriod(guildId: UUID, startTime: Instant, endTime: Instant): GuildKillStats

    /**
     * Updates kill statistics after a kill is recorded.
     *
     * @param kill The kill that was recorded.
     * @return true if successful, false otherwise.
     */
    fun updateKillStats(kill: Kill): Boolean

    /**
     * Gets the kill/death ratio between two guilds.
     *
     * @param guildA The first guild.
     * @param guildB The second guild.
     * @return The kill/death ratio (kills by A / deaths by A).
     */
    fun getKillDeathRatio(guildA: UUID, guildB: UUID): Double

    /**
     * Processes expired anti-farm data.
     *
     * @return The number of records processed.
     */
    fun processExpiredAntiFarmData(): Int
}
