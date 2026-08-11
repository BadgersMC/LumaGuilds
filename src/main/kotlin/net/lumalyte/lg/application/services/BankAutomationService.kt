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
        val balance = bankService.getBalance(guild.id)
        val interestPerPeriod = (balance * rate).toInt()

        var cursor = lastAccrual
        var periods = 0
        while (periods < maxCatchUpPeriods && !cursor.plus(periodHours, ChronoUnit.HOURS).isAfter(now)) {
            cursor = cursor.plus(periodHours, ChronoUnit.HOURS)
            periods++
        }

        // Persist the advanced marker BEFORE crediting. If the marker write fails,
        // skip crediting entirely — the next run retries from the old marker, so no
        // period can be double-credited (money safety). If a credit fails partway,
        // the marker has already advanced, so at worst one period is skipped —
        // never duplicated.
        val updated = settings.copy(lastInterestAccrual = cursor.toEpochMilli())
        if (!bankSettingsRepository.upsert(updated)) {
            logger.error("Failed to persist interest accrual marker for guild ${guild.id}; skipping accrual")
            return 0
        }

        var credited = 0
        repeat(periods) {
            if (interestPerPeriod > 0) {
                bankService.creditToGuildBank(guild.id, interestPerPeriod, "Interest accrual")
                credited++
            }
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
