package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.persistence.BankSettingsRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.config.BankConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.BankSettings
import net.lumalyte.lg.domain.entities.Guild
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * REQ-009: interest accrual per `bank.interest_rate_percent` /
 * `bank.interest_compound_period_hours`, audit-log retention, and next-run
 * computation must come from real configuration + persisted settings.
 */
class BankAutomationServiceTest {

    private val bankRepository = mockk<BankRepository>(relaxed = true)
    private val settingsRepository = mockk<BankSettingsRepository>(relaxed = true)
    private val guildRepository = mockk<GuildRepository>(relaxed = true)
    private val bankService = mockk<BankService>(relaxed = true)
    private val configService = mockk<ConfigService>(relaxed = true)

    private fun service() = BankAutomationService(
        bankRepository, settingsRepository, guildRepository, bankService, configService
    )

    private fun config(
        ratePercent: Double = 0.005,
        periodHours: Int = 24,
        retentionDays: Int = 30
    ) = MainConfig(bank = BankConfig(
        interestRatePercent = ratePercent,
        interestCompoundPeriodHours = periodHours,
        auditLogRetentionDays = retentionDays
    ))

    @Test
    fun `interest accrues once the compound period has elapsed`() {
        val guildId = UUID.randomUUID()
        val guild = Guild(id = guildId, name = "Guild", createdAt = Instant.now().minus(48, ChronoUnit.HOURS))
        val settings = BankSettings(
            guildId = guildId,
            interestRate = 0.005,
            lastInterestAccrual = Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli()
        )
        every { guildRepository.getAll() } returns setOf(guild)
        every { settingsRepository.getByGuildId(guildId) } returns settings
        every { bankService.getBalance(guildId) } returns 1000
        every { configService.loadConfig() } returns config()

        val credited = service().accrueInterest()

        assertEquals(1, credited)
        verify(exactly = 1) { bankService.creditToGuildBank(guildId, 5, "Interest accrual") }
    }

    @Test
    fun `per-guild interest rate overrides config rate`() {
        val guildId = UUID.randomUUID()
        val guild = Guild(id = guildId, name = "Guild", createdAt = Instant.now().minus(48, ChronoUnit.HOURS))
        val settings = BankSettings(
            guildId = guildId,
            interestRate = 0.10,
            lastInterestAccrual = Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli()
        )
        every { guildRepository.getAll() } returns setOf(guild)
        every { settingsRepository.getByGuildId(guildId) } returns settings
        every { bankService.getBalance(guildId) } returns 1000
        every { configService.loadConfig() } returns config()

        service().accrueInterest()

        verify(exactly = 1) { bankService.creditToGuildBank(guildId, 100, "Interest accrual") }
    }

    @Test
    fun `no interest before the compound period elapses`() {
        val guildId = UUID.randomUUID()
        val guild = Guild(id = guildId, name = "Guild", createdAt = Instant.now().minus(2, ChronoUnit.HOURS))
        val settings = BankSettings(
            guildId = guildId,
            lastInterestAccrual = Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()
        )
        every { guildRepository.getAll() } returns setOf(guild)
        every { settingsRepository.getByGuildId(guildId) } returns settings
        every { bankService.getBalance(guildId) } returns 1000
        every { configService.loadConfig() } returns config()

        val credited = service().accrueInterest()

        assertEquals(0, credited)
        verify(exactly = 0) { bankService.creditToGuildBank(any(), any(), any()) }
    }

    @Test
    fun `missed periods are caught up`() {
        val guildId = UUID.randomUUID()
        val guild = Guild(id = guildId, name = "Guild", createdAt = Instant.now().minus(100, ChronoUnit.HOURS))
        val settings = BankSettings(
            guildId = guildId,
            interestRate = 0.005,
            lastInterestAccrual = Instant.now().minus(72, ChronoUnit.HOURS).toEpochMilli()
        )
        every { guildRepository.getAll() } returns setOf(guild)
        every { settingsRepository.getByGuildId(guildId) } returns settings
        every { bankService.getBalance(guildId) } returns 1000
        every { configService.loadConfig() } returns config()

        val credited = service().accrueInterest()

        assertEquals(3, credited)
        verify(exactly = 3) { bankService.creditToGuildBank(guildId, 5, "Interest accrual") }
    }

    @Test
    fun `pruneAuditLogs deletes audits older than retention window`() {
        val guildId = UUID.randomUUID()
        val guild = Guild(id = guildId, name = "Guild", createdAt = Instant.now())
        every { guildRepository.getAll() } returns setOf(guild)
        every { configService.loadConfig() } returns config(retentionDays = 30)
        every { bankRepository.deleteAuditsOlderThan(guildId, any()) } returns 12

        val pruned = service().pruneAuditLogs()

        assertEquals(12, pruned)
        val cutoffSlot = io.mockk.slot<Instant>()
        verify(exactly = 1) { bankRepository.deleteAuditsOlderThan(guildId, capture(cutoffSlot)) }
        val expected = Instant.now().minus(30, ChronoUnit.DAYS)
        assertTrue(java.time.Duration.between(cutoffSlot.captured, expected).abs().toSeconds() < 5,
            "cutoff must be ~30 days back, was ${cutoffSlot.captured}")
    }

    @Test
    fun `next interest run is last accrual plus compound period`() {
        val guildId = UUID.randomUUID()
        val lastAccrual = Instant.now().minus(10, ChronoUnit.HOURS)
        every { settingsRepository.getByGuildId(guildId) } returns
            BankSettings(guildId = guildId, lastInterestAccrual = lastAccrual.toEpochMilli())
        every { configService.loadConfig() } returns config(periodHours = 24)

        val nextRun = service().getNextInterestRun(guildId)

        assertEquals(lastAccrual.plus(24, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS),
            nextRun!!.truncatedTo(ChronoUnit.SECONDS))
    }

    @Test
    fun `next interest run is null when no settings exist`() {
        every { settingsRepository.getByGuildId(any()) } returns null

        assertNull(service().getNextInterestRun(UUID.randomUUID()))
    }
}
