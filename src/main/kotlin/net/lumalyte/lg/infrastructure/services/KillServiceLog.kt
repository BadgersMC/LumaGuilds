package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildKillLog
import net.lumalyte.lg.application.services.KillService
import net.lumalyte.lg.domain.entities.AntiFarmData
import net.lumalyte.lg.domain.entities.GuildKillStats
import net.lumalyte.lg.domain.entities.Kill
import net.lumalyte.lg.domain.entities.PlayerKillStats
import net.lumalyte.lg.domain.values.CombatSummary
import net.lumalyte.lg.domain.values.KillDecision
import net.lumalyte.lg.domain.values.KillRecording
import net.lumalyte.lg.domain.values.Position3D
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * REQ-199/REQ-200: [KillService] backed by the kill log, replacing the never-written kill tables.
 * [guildOf] is a player's single guild, or null when they have none (or several). Reads fail soft
 * (zeros and empty lists) so a database hiccup never breaks a menu or placeholder.
 */
class KillServiceLog(
    private val log: GuildKillLog,
    private val guildOf: (UUID) -> UUID?,
    private val clock: Clock = Clock.systemUTC(),
) : KillService {
    private val logger = LoggerFactory.getLogger(KillServiceLog::class.java)

    override fun recordKill(killerId: UUID, victimId: UUID, weapon: String?, worldId: UUID?, location: Position3D?): Kill? {
        if (killerId == victimId) return null
        val now = clock.instant()
        val killerGuild = guildOf(killerId)
        val victimGuild = guildOf(victimId)
        val last = read(null) { log.lastKill(killerId, victimId) }
        if (KillRecording.decide(killerId, victimId, killerGuild, victimGuild, last, now) != KillDecision.RECORD) return null
        val kill = Kill(UUID.randomUUID(), killerId, victimId, killerGuild, victimGuild, now, weapon, worldId, location)
        return runCatching { log.record(kill) }.map { kill }
            .onFailure { logger.error("Unable to save kill of $victimId by $killerId", it) }
            .getOrNull()
    }

    override fun getGuildKillStats(guildId: UUID): GuildKillStats =
        stats(guildId, read(CombatSummary(0, 0)) { log.guildSummary(guildId) })

    override fun getPlayerKillStats(playerId: UUID): PlayerKillStats {
        val summary = read(CombatSummary(0, 0)) { log.playerSummary(playerId) }
        return PlayerKillStats(playerId, guildOf(playerId), summary.kills, summary.deaths)
    }

    override fun getRecentGuildKills(guildId: UUID, limit: Int): List<Kill> = read(emptyList()) { log.recent(guildId, limit) }

    override fun getKillsBetweenGuilds(guildA: UUID, guildB: UUID, limit: Int): List<Kill> =
        read(emptyList()) { log.between(guildA, guildB, limit) }

    override fun isFarmingKill(killerId: UUID, victimId: UUID): Boolean {
        val now = clock.instant()
        val last = read(null) { log.lastKill(killerId, victimId) } ?: return false
        return KillRecording.decide(killerId, victimId, UUID(0, 1), UUID(0, 2), last, now) == KillDecision.FARMING
    }

    override fun getAntiFarmData(playerId: UUID): AntiFarmData = AntiFarmData(playerId)

    override fun resetFarmScore(playerId: UUID): Boolean = true

    override fun getTopKillers(guildMembers: List<UUID>, limit: Int): List<Pair<UUID, PlayerKillStats>> =
        read(emptyMap()) { log.killCounts(guildMembers) }.entries
            .sortedWith(compareByDescending<Map.Entry<UUID, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { (player, kills) -> player to PlayerKillStats(player, guildOf(player), totalKills = kills) }

    override fun getKillStatsForPeriod(guildId: UUID, startTime: Instant, endTime: Instant): GuildKillStats =
        stats(guildId, read(CombatSummary(0, 0)) { log.guildSummary(guildId, startTime, endTime) })

    /** The log is written by [recordKill]; there are no separate stats to update. */
    override fun updateKillStats(kill: Kill): Boolean = true

    override fun getKillDeathRatio(guildA: UUID, guildB: UUID): Double {
        val kills = getKillsBetweenGuilds(guildA, guildB, MAX_PAIR_KILLS)
        return CombatSummary(kills.count { it.killerGuildId == guildA }, kills.count { it.killerGuildId == guildB }).ratio
    }

    override fun processExpiredAntiFarmData(): Int = 0

    private fun stats(guildId: UUID, s: CombatSummary) =
        GuildKillStats(guildId, s.kills, s.deaths, s.kills - s.deaths, s.ratio, clock.instant())

    private fun <T> read(fallback: T, block: () -> T): T = try {
        block()
    } catch (error: Exception) {
        logger.error("Unable to read the kill log", error)
        fallback
    }

    private companion object {
        const val MAX_PAIR_KILLS = 500
    }
}
