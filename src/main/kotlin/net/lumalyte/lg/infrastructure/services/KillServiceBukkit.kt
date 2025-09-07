package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.KillRepository
import net.lumalyte.lg.application.services.KillService
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.Position3D
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

class KillServiceBukkit(private val killRepository: KillRepository) : KillService {

    private val logger = LoggerFactory.getLogger(KillServiceBukkit::class.java)

    override fun recordKill(killerId: UUID, victimId: UUID, weapon: String?, worldId: UUID?, location: Position3D?): Kill? {
        try {
            // Check for farming behavior
            if (isFarmingKill(killerId, victimId)) {
                logger.info("Blocked farming kill: killer=$killerId, victim=$victimId")
                return null
            }

            val kill = Kill(
                id = UUID.randomUUID(),
                killerId = killerId,
                victimId = victimId,
                killerGuildId = null, // Will be updated when we get guild info
                victimGuildId = null, // Will be updated when we get guild info
                timestamp = Instant.now(),
                weapon = weapon,
                worldId = worldId,
                location = location
            )

            if (killRepository.recordKill(kill)) {
                logger.info("Recorded kill: ${kill.id}")
                return kill
            } else {
                logger.error("Failed to record kill: ${kill.id}")
                return null
            }
        } catch (e: Exception) {
            logger.error("Error recording kill", e)
            throw DatabaseOperationException("Failed to record kill", e)
        }
    }

    override fun getGuildKillStats(guildId: UUID): GuildKillStats {
        return try {
            killRepository.getGuildKillStats(guildId)
        } catch (e: Exception) {
            logger.error("Error getting guild kill stats for guild: $guildId", e)
            GuildKillStats(guildId)
        }
    }

    override fun getPlayerKillStats(playerId: UUID): PlayerKillStats {
        return try {
            killRepository.getPlayerKillStats(playerId)
        } catch (e: Exception) {
            logger.error("Error getting player kill stats for player: $playerId", e)
            PlayerKillStats(playerId)
        }
    }

    override fun getRecentGuildKills(guildId: UUID, limit: Int): List<Kill> {
        return try {
            killRepository.getKillsForGuild(guildId, limit)
        } catch (e: Exception) {
            logger.error("Error getting recent kills for guild: $guildId", e)
            emptyList()
        }
    }

    override fun getKillsBetweenGuilds(guildA: UUID, guildB: UUID, limit: Int): List<Kill> {
        return try {
            killRepository.getKillsBetweenGuilds(guildA, guildB, limit)
        } catch (e: Exception) {
            logger.error("Error getting kills between guilds $guildA and $guildB", e)
            emptyList()
        }
    }

    override fun isFarmingKill(killerId: UUID, victimId: UUID): Boolean {
        return try {
            val antiFarmData = killRepository.getAntiFarmData(killerId)
            antiFarmData.wouldBeFarming(Instant.now())
        } catch (e: Exception) {
            logger.error("Error checking farming behavior for killer: $killerId", e)
            false
        }
    }

    override fun getAntiFarmData(playerId: UUID): AntiFarmData {
        return try {
            killRepository.getAntiFarmData(playerId)
        } catch (e: Exception) {
            logger.error("Error getting anti-farm data for player: $playerId", e)
            AntiFarmData(playerId)
        }
    }

    override fun resetFarmScore(playerId: UUID): Boolean {
        return try {
            val data = AntiFarmData(playerId)
            killRepository.updateAntiFarmData(data)
            logger.info("Reset farm score for player: $playerId")
            true
        } catch (e: Exception) {
            logger.error("Error resetting farm score for player: $playerId", e)
            false
        }
    }

    override fun getTopKillers(guildMembers: List<UUID>, limit: Int): List<Pair<UUID, PlayerKillStats>> {
        return try {
            // Get kill stats for all guild members and sort by total kills
            val memberStats = guildMembers.mapNotNull { playerId ->
                try {
                    val stats = killRepository.getPlayerKillStats(playerId)
                    playerId to stats
                } catch (e: Exception) {
                    logger.warn("Failed to get kill stats for player $playerId", e)
                    null
                }
            }

            // Sort by total kills (descending) and take the top limit
            memberStats.sortedByDescending { (_, stats) -> stats.totalKills }
                .take(limit)
        } catch (e: Exception) {
            logger.error("Error getting top killers for guild members", e)
            emptyList()
        }
    }

    override fun getKillStatsForPeriod(guildId: UUID, startTime: Instant, endTime: Instant): GuildKillStats {
        return try {
            val kills = killRepository.getGuildKillsInPeriod(guildId, startTime, endTime, 1000)

            val killsByGuild = kills.count { it.killerGuildId == guildId }
            val deathsByGuild = kills.count { it.victimGuildId == guildId }

            GuildKillStats(
                guildId = guildId,
                totalKills = killsByGuild,
                totalDeaths = deathsByGuild,
                netKills = killsByGuild - deathsByGuild,
                killDeathRatio = if (deathsByGuild > 0) killsByGuild.toDouble() / deathsByGuild.toDouble() else killsByGuild.toDouble(),
                lastUpdated = Instant.now()
            )
        } catch (e: Exception) {
            logger.error("Error getting kill stats for period for guild: $guildId", e)
            GuildKillStats(guildId)
        }
    }

    override fun updateKillStats(kill: Kill): Boolean {
        return try {
            // Update killer guild stats if applicable
            if (kill.killerGuildId != null) {
                val currentStats = killRepository.getGuildKillStats(kill.killerGuildId)
                val updatedStats = GuildKillStats(
                    guildId = kill.killerGuildId,
                    totalKills = currentStats.totalKills + 1,
                    totalDeaths = currentStats.totalDeaths,
                    netKills = currentStats.netKills + 1,
                    killDeathRatio = GuildKillStats.calculateKillDeathRatio(currentStats.totalKills + 1, currentStats.totalDeaths),
                    lastUpdated = Instant.now()
                )
                killRepository.updateGuildKillStats(updatedStats)
            }

            // Update victim guild stats if applicable
            if (kill.victimGuildId != null) {
                val currentStats = killRepository.getGuildKillStats(kill.victimGuildId)
                val updatedStats = GuildKillStats(
                    guildId = kill.victimGuildId,
                    totalKills = currentStats.totalKills,
                    totalDeaths = currentStats.totalDeaths + 1,
                    netKills = currentStats.netKills - 1,
                    killDeathRatio = GuildKillStats.calculateKillDeathRatio(currentStats.totalKills, currentStats.totalDeaths + 1),
                    lastUpdated = Instant.now()
                )
                killRepository.updateGuildKillStats(updatedStats)
            }

            // Update player stats
            val killerStats = killRepository.getPlayerKillStats(kill.killerId)
            val updatedKillerStats = PlayerKillStats(
                playerId = kill.killerId,
                guildId = kill.killerGuildId,
                totalKills = killerStats.totalKills + 1,
                totalDeaths = killerStats.totalDeaths,
                streak = killerStats.streak + 1,
                bestStreak = maxOf(killerStats.bestStreak, killerStats.streak + 1),
                lastKillTime = Instant.now(),
                lastDeathTime = killerStats.lastDeathTime
            )
            killRepository.updatePlayerKillStats(updatedKillerStats)

            val victimStats = killRepository.getPlayerKillStats(kill.victimId)
            val updatedVictimStats = PlayerKillStats(
                playerId = kill.victimId,
                guildId = kill.victimGuildId,
                totalKills = victimStats.totalKills,
                totalDeaths = victimStats.totalDeaths + 1,
                streak = 0, // Reset streak on death
                bestStreak = victimStats.bestStreak,
                lastKillTime = victimStats.lastKillTime,
                lastDeathTime = Instant.now()
            )
            killRepository.updatePlayerKillStats(updatedVictimStats)

            logger.info("Updated kill stats for kill: ${kill.id}")
            true
        } catch (e: Exception) {
            logger.error("Error updating kill stats for kill: ${kill.id}", e)
            false
        }
    }

    override fun getKillDeathRatio(guildA: UUID, guildB: UUID): Double {
        return try {
            val kills = killRepository.getKillsBetweenGuilds(guildA, guildB, 1000)

            val killsByA = kills.count { it.killerGuildId == guildA }
            val deathsByA = kills.count { it.victimGuildId == guildA }

            if (deathsByA == 0) {
                if (killsByA > 0) Double.MAX_VALUE else 0.0
            } else {
                killsByA.toDouble() / deathsByA.toDouble()
            }
        } catch (e: Exception) {
            logger.error("Error getting kill/death ratio between guilds $guildA and $guildB", e)
            0.0
        }
    }

    override fun processExpiredAntiFarmData(): Int {
        // This would need additional repository methods to find and clean up expired data
        // For now, return 0
        return 0
    }
}
