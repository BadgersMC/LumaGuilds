package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.PenaltyRepository
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.config.StrikesPenaltiesConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.PenaltyType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PenaltyServiceTest {

    private val penaltyRepository = mockk<PenaltyRepository>(relaxed = true)
    private val progressionService = mockk<ProgressionService>()
    private val guildService = mockk<GuildService>(relaxed = true)

    private fun config(block: StrikesPenaltiesConfig.() -> Unit = {}): StrikesConfig {
        val penalties = StrikesPenaltiesConfig().apply(block)
        return StrikesConfig(enabled = true, threshold = 5, penalties = penalties)
    }

    private val guild = Guild(
        id = UUID.randomUUID(),
        name = "Testers",
        tag = "TEST",
        level = 7,
        createdAt = Instant.now(),
    )

    @Test
    fun `level reduction delegates to progression and records penalty`() {
        every { progressionService.reduceLevel(guild.id, 1, ExperienceSource.ADMIN_BONUS) } returns 6
        val service = PenaltyService(penaltyRepository, progressionService, guildService) { config() }

        val result = service.applyLevelReduction(guild, UUID.randomUUID(), "Admin")

        assertTrue(result is PenaltyService.PenaltyResult.Success)
        val penalty = (result as PenaltyService.PenaltyResult.Success).penalty
        assertEquals(PenaltyType.LEVEL_REDUCTION, penalty.type)
        assertEquals(1L, penalty.amount)
        verify { penaltyRepository.recordPenalty(penalty) }
    }

    @Test
    fun `level reduction disabled when levels is zero`() {
        val service = PenaltyService(penaltyRepository, progressionService, guildService) {
            config { levelReductionLevels = 0 }
        }

        val result = service.applyLevelReduction(guild, UUID.randomUUID(), "Admin")

        assertTrue(result is PenaltyService.PenaltyResult.Failure)
        verify(exactly = 0) { penaltyRepository.recordPenalty(any()) }
    }

    @Test
    fun `exp reduction delegates to progression and records penalty`() {
        every { progressionService.removeExperience(guild.id, 1000, ExperienceSource.ADMIN_BONUS) } returns 5
        val service = PenaltyService(penaltyRepository, progressionService, guildService) { config() }

        val result = service.applyExpReduction(guild, UUID.randomUUID(), "Admin")

        assertTrue(result is PenaltyService.PenaltyResult.Success)
        assertEquals(PenaltyType.EXP_REDUCTION, (result as PenaltyService.PenaltyResult.Success).penalty.type)
        verify { penaltyRepository.recordPenalty(any()) }
    }

    @Test
    fun `guild mute records duration and is reported as active`() {
        every { penaltyRepository.hasActiveMute(guild.id, any()) } returns true
        val service = PenaltyService(penaltyRepository, progressionService, guildService) { config() }

        val result = service.applyGuildMute(guild, UUID.randomUUID(), "Admin")

        assertTrue(result is PenaltyService.PenaltyResult.Success)
        assertEquals(PenaltyType.GUILD_MUTE, (result as PenaltyService.PenaltyResult.Success).penalty.type)
        assertEquals(24L * 3_600_000L, (result as PenaltyService.PenaltyResult.Success).penalty.amount)
        assertTrue(service.isGuildMuted(guild.id))
    }

    @Test
    fun `disband records penalty and disbands guild`() {
        every { guildService.disbandGuild(guild.id, any()) } returns true
        val service = PenaltyService(penaltyRepository, progressionService, guildService) { config() }

        val result = service.applyDisband(guild, UUID.randomUUID(), "Admin")

        assertTrue(result is PenaltyService.PenaltyResult.Success)
        assertEquals(PenaltyType.DISBAND, (result as PenaltyService.PenaltyResult.Success).penalty.type)
        // The disband must run as the system UUID so the guild's own permission
        // check (MANAGE_RANKS in the target guild) can't reject an admin penalty.
        verify {
            guildService.disbandGuild(guild.id, UUID.fromString("00000000-0000-0000-0000-000000000000"))
        }
        verify { penaltyRepository.recordPenalty(any()) }
    }

    @Test
    fun `disband failure surfaces as failure result and records no penalty`() {
        every { guildService.disbandGuild(guild.id, any()) } returns false
        val service = PenaltyService(penaltyRepository, progressionService, guildService) { config() }

        val result = service.applyDisband(guild, UUID.randomUUID(), "Admin")

        assertTrue(result is PenaltyService.PenaltyResult.Failure)
        // The audit trail must NOT show a disband that never happened.
        verify(exactly = 0) { penaltyRepository.recordPenalty(any()) }
    }
}
