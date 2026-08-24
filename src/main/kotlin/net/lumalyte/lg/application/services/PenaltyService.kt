package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.PenaltyRepository
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildPenalty
import net.lumalyte.lg.domain.entities.PenaltyType
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Applies and records admin-triggered guild penalties (Level Reduction, EXP
 * reduction, Guild Mute, Disband) once a guild reaches the strike threshold.
 */
class PenaltyService(
    private val penaltyRepository: PenaltyRepository,
    private val progressionService: ProgressionService,
    private val guildService: GuildService,
    private val configProvider: () -> StrikesConfig,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /** Outcome of applying a penalty. */
    enum class PenaltyFeedback {
        LEVEL_DISABLED, LEVEL_REDUCED, EXP_DISABLED, EXP_REDUCED,
        MUTE_DISABLED, MUTED, DISBAND_FAILED, DISBANDED,
    }

    sealed interface PenaltyResult {
        val feedback: PenaltyFeedback
        val parameters: Map<String, Any?>

        data class Success(
            val penalty: GuildPenalty,
            override val feedback: PenaltyFeedback,
            override val parameters: Map<String, Any?> = emptyMap(),
        ) : PenaltyResult

        data class Failure(
            override val feedback: PenaltyFeedback,
            override val parameters: Map<String, Any?> = emptyMap(),
        ) : PenaltyResult
    }

    fun applyLevelReduction(guild: Guild, actorUuid: UUID, actorName: String): PenaltyResult {
        val levels = configProvider().penalties.levelReductionLevels
        if (levels <= 0) return PenaltyResult.Failure(PenaltyFeedback.LEVEL_DISABLED)
        val newLevel = progressionService.reduceLevel(guild.id, levels, ExperienceSource.ADMIN_BONUS)
        val penalty = GuildPenalty(
            guildId = guild.id,
            type = PenaltyType.LEVEL_REDUCTION,
            amount = levels.toLong(),
            reason = "Strike penalty",
            actorUuid = actorUuid,
            actorName = actorName,
            createdAt = Instant.now()
        )
        penaltyRepository.recordPenalty(penalty)
        logger.info("Level reduction penalty applied to guild {} ({} -> level {}) by {}", guild.name, guild.level, newLevel, actorName)
        return PenaltyResult.Success(penalty, PenaltyFeedback.LEVEL_REDUCED, mapOf("level" to newLevel, "guild" to guild.name))
    }

    fun applyExpReduction(guild: Guild, actorUuid: UUID, actorName: String): PenaltyResult {
        val amount = configProvider().penalties.expReductionAmount
        if (amount <= 0) return PenaltyResult.Failure(PenaltyFeedback.EXP_DISABLED)
        val newLevel = progressionService.removeExperience(guild.id, amount, ExperienceSource.ADMIN_BONUS)
        val penalty = GuildPenalty(
            guildId = guild.id,
            type = PenaltyType.EXP_REDUCTION,
            amount = amount.toLong(),
            reason = "Strike penalty",
            actorUuid = actorUuid,
            actorName = actorName,
            createdAt = Instant.now()
        )
        penaltyRepository.recordPenalty(penalty)
        logger.info("EXP reduction penalty applied to guild {} (removed {}, now level {}) by {}", guild.name, amount, newLevel, actorName)
        return PenaltyResult.Success(penalty, PenaltyFeedback.EXP_REDUCED, mapOf("amount" to amount, "guild" to guild.name))
    }

    fun applyGuildMute(guild: Guild, actorUuid: UUID, actorName: String): PenaltyResult {
        val durationMs = configProvider().penalties.guildMuteDurationMillis
        if (durationMs <= 0) return PenaltyResult.Failure(PenaltyFeedback.MUTE_DISABLED)
        val penalty = GuildPenalty(
            guildId = guild.id,
            type = PenaltyType.GUILD_MUTE,
            amount = durationMs,
            reason = "Strike penalty",
            actorUuid = actorUuid,
            actorName = actorName,
            createdAt = Instant.now()
        )
        penaltyRepository.recordPenalty(penalty)
        val hours = durationMs / 3_600_000.0
        logger.info("Guild mute penalty applied to guild {} ({}h) by {}", guild.name, formatHours(hours), actorName)
        return PenaltyResult.Success(penalty, PenaltyFeedback.MUTED, mapOf("guild" to guild.name, "hours" to formatHours(hours)))
    }

    fun applyDisband(guild: Guild, actorUuid: UUID, actorName: String): PenaltyResult {
        // Admin-triggered disband: use the system UUID so the permission check in
        // GuildService.disbandGuild (which requires MANAGE_RANKS in the target guild)
        // doesn't reject the admin who is opening the penalty menu.
        val systemUuid = UUID.fromString("00000000-0000-0000-0000-000000000000")
        val success = guildService.disbandGuild(guild.id, systemUuid)
        if (!success) return PenaltyResult.Failure(PenaltyFeedback.DISBAND_FAILED, mapOf("guild" to guild.name))

        // Only record the penalty AFTER the disband actually succeeded — otherwise the
        // audit trail would show a disband that never happened.
        val penalty = GuildPenalty(
            guildId = guild.id,
            type = PenaltyType.DISBAND,
            amount = null,
            reason = "Strike penalty",
            actorUuid = actorUuid,
            actorName = actorName,
            createdAt = Instant.now()
        )
        penaltyRepository.recordPenalty(penalty)
        logger.info("Disband penalty applied to guild {} by {}", guild.name, actorName)
        return PenaltyResult.Success(penalty, PenaltyFeedback.DISBANDED, mapOf("guild" to guild.name))
    }

    private fun formatHours(hours: Double): String = "%.1f".format(hours)

    /** True while the guild has an in-force guild-mute penalty. */
    fun isGuildMuted(guildId: UUID): Boolean = penaltyRepository.hasActiveMute(guildId, Instant.now())

    fun getByGuild(guildId: UUID): List<GuildPenalty> = penaltyRepository.getByGuild(guildId)

    fun getLatest(guildId: UUID): GuildPenalty? = penaltyRepository.getLatest(guildId)
}
