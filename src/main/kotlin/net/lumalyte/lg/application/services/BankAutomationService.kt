package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.persistence.BankSettingsRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.domain.entities.BankSettings
import net.lumalyte.lg.domain.entities.Guild
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Scheduled bank automation (REQ-009):
 * - interest accrual per `bank.interest_rate_percent` / `bank.interest_compound_period_hours`
 *   (per-guild [BankSettings.interestRate] overrides the global rate when persisted)
 * - audit-log retention pruning per `bank.audit_log_retention_days`
 *
 * Driven by [net.lumalyte.lg.infrastructure.services.BankInterestScheduler].
 */
class BankAutomationService(
    private val bankRepository: BankRepository,
    private val bankSettingsRepository: BankSettingsRepository,
    private val guildRepository: GuildRepository,
    private val bankService: BankService,
    private val configService: ConfigService
) {

    private val logger = LoggerFactory.getLogger(BankAutomationService::class.java)

    /** Maximum catch-up periods applied in a single run (prevents runaway accrual). */
    private val maxCatchUpPeriods = 30

    /**
     * Accrues interest for every guild whose compound period has elapsed.
     *
     * @return The number of interest credits applied.
     */
    fun accrueInterest(): Int {
        val config = configService.loadConfig()
        val periodHours = config.bank.interestCompoundPeriodHours.toLong()
        val now = Instant.now()
        var credited = 0

        for (guild in guildRepository.getAll()) {
            try {
                credited += accrueForGuild(guild, periodHours, now)
            } catch (e: Exception) {
                logger.error("Failed to accrue interest for guild ${guild.id}", e)
            }
        }
        return credited
    }

    private fun accrueForGuild(guild: Guild, periodHours: Long, now: Instant): Int {
        val settings = bankSettingsRepository.getByGuildId(guild.id)

        // Never accrued (or fresh row): start the clock now — no retroactive interest.
        // Persist the initial timestamp BEFORE the period check so the next run
        // sees an initialized clock instead of re-initializing (which would
        // otherwise reset on every run and never accrue).
        if (settings == null || settings.lastInterestAccrual <= 0L) {
            val initialized = (settings ?: BankSettings(guild.id))
                .copy(lastInterestAccrual = now.toEpochMilli())
            if (!bankSettingsRepository.upsert(initialized)) {
                logger.error("Failed to initialize interest clock for guild ${guild.id}; skipping accrual")
                return 0
            }
            return 0 // clock just started; the period has not elapsed
        }

        val lastAccrual = Instant.ofEpochMilli(settings.lastInterestAccrual)

        if (lastAccrual.plus(periodHours, ChronoUnit.HOURS).isAfter(now)) {
            return 0 // Period not yet elapsed
        }

        val rate = settings.interestRate ?: configService.loadConfig().bank.interestRatePercent
        if (periodHours <= 0 || !rate.isFinite() || rate < 0) return 0
        var cursor = lastAccrual
        var credited = 0
        repeat(maxCatchUpPeriods) {
            val periodEnd = cursor.plus(periodHours, ChronoUnit.HOURS)
            if (periodEnd.isAfter(now)) return credited
            when (val result = bankService.creditInterest(guild.id, periodEnd.toEpochMilli(), rate)) {
                is net.lumalyte.lg.domain.gold.GuildGoldResult.Applied -> {
                    if (result.newBalance > result.oldBalance) credited++
                }
                is net.lumalyte.lg.domain.gold.GuildGoldResult.Rejected -> {
                    // A full bank skips this period explicitly; other failures leave it retryable.
                    if (result.reason != net.lumalyte.lg.domain.gold.GuildGoldRejection.CAPACITY_EXCEEDED) return credited
                }
                is net.lumalyte.lg.domain.gold.GuildGoldResult.Failed -> return credited
            }
            // The period ID is durable, so a failed marker write can safely retry the settled payment.
            if (!bankSettingsRepository.upsert(settings.copy(lastInterestAccrual = periodEnd.toEpochMilli()))) {
                logger.error("Failed to persist interest marker for guild ${guild.id}; settlement will be retried")
                return credited
            }
            cursor = periodEnd
        }
        return credited
    }

    /**
     * Prunes audit entries older than the retention window.
     *
     * @return The total number of audit entries deleted.
     */
    fun pruneAuditLogs(): Int {
        val retentionDays = configService.loadConfig().bank.auditLogRetentionDays.toLong()
        val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
        var pruned = 0

        for (guild in guildRepository.getAll()) {
            try {
                pruned += bankRepository.deleteAuditsOlderThan(guild.id, cutoff)
            } catch (e: Exception) {
                logger.error("Failed to prune audit log for guild ${guild.id}", e)
            }
        }
        return pruned
    }

    /**
     * Computes the next scheduled interest accrual for a guild's menu display.
     *
     * @param guildId The ID of the guild.
     * @return The next run instant, or null when the guild has no accrual history.
     */
    fun getNextInterestRun(guildId: UUID): Instant? {
        val settings = bankSettingsRepository.getByGuildId(guildId) ?: return null
        if (settings.lastInterestAccrual <= 0L) return null
        return Instant.ofEpochMilli(settings.lastInterestAccrual)
            .plus(configService.loadConfig().bank.interestCompoundPeriodHours.toLong(), ChronoUnit.HOURS)
    }
}
