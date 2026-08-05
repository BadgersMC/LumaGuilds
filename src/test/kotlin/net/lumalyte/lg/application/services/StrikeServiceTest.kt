package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.StrikeRepository
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.domain.entities.GuildStrike
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class StrikeServiceTest {

    private val guildId = UUID.randomUUID()
    private val playerUuid = UUID.randomUUID()
    private val now = Instant.parse("2026-08-04T12:00:00Z")

    private fun serviceWith(config: StrikesConfig, repo: StrikeRepository = mockk(relaxed = true)) =
        StrikeService(repository = repo, configProvider = { config })

    @Test
    fun `recordStrike delegates to repository when enabled`() {
        val repo = mockk<StrikeRepository>(relaxed = true)
        val service = serviceWith(StrikesConfig(enabled = true), repo)

        service.recordStrike(guildId, playerUuid, "Steve", "BAN", "griefing", "Mod", now, 42L)

        verify(exactly = 1) {
            repo.recordStrike(
                match {
                    it.guildId == guildId &&
                        it.playerUuid == playerUuid &&
                        it.punishmentType == "BAN" &&
                        it.reason == "griefing" &&
                        it.executorName == "Mod" &&
                        it.litebansEntryId == 42L &&
                        it.active
                }
            )
        }
    }

    @Test
    fun `recordStrike is a no-op when disabled`() {
        val repo = mockk<StrikeRepository>(relaxed = true)
        val service = serviceWith(StrikesConfig(enabled = false), repo)

        service.recordStrike(guildId, playerUuid, "Steve", "WARN", "spam", null, now, 1L)

        verify(exactly = 0) { repo.recordStrike(any()) }
    }

    @Test
    fun `isUpForPenalty respects threshold on ACTIVE strikes only`() {
        val repo = mockk<StrikeRepository>()
        every { repo.countActiveByGuild(guildId) } returns 5

        val service = serviceWith(StrikesConfig(enabled = true, threshold = 5), repo)
        assertTrue(service.isUpForPenalty(guildId))

        every { repo.countActiveByGuild(guildId) } returns 4
        assertFalse(service.isUpForPenalty(guildId))
    }

    @Test
    fun `threshold of zero never flags a guild`() {
        val repo = mockk<StrikeRepository>()
        every { repo.countActiveByGuild(guildId) } returns 99

        val service = serviceWith(StrikesConfig(enabled = true, threshold = 0), repo)
        assertFalse(service.isUpForPenalty(guildId))
    }

    @Test
    fun `deactivateStrike delegates with type and respects enabled flag`() {
        val repo = mockk<StrikeRepository>(relaxed = true)
        val service = serviceWith(StrikesConfig(enabled = true), repo)

        service.deactivateStrike("BAN", 77L)
        verify(exactly = 1) { repo.deactivateStrike("BAN", 77L) }

        val disabled = serviceWith(StrikesConfig(enabled = false), repo)
        disabled.deactivateStrike("BAN", 77L)
        verify(exactly = 1) { repo.deactivateStrike("BAN", 77L) } // unchanged — disabled = no-op
    }

    @Test
    fun `getAllCounts and countAll delegate to repository`() {
        val repo = mockk<StrikeRepository>()
        every { repo.getAllCounts() } returns mapOf(guildId to 3)
        every { repo.countAll() } returns 3

        val service = serviceWith(StrikesConfig(enabled = true), repo)
        assertEquals(mapOf(guildId to 3), service.getAllCounts())
        assertEquals(3, service.countAll())
    }

    @Test
    fun `getAllActiveCounts and countActiveByGuild delegate to repository`() {
        val repo = mockk<StrikeRepository>()
        every { repo.getAllActiveCounts() } returns mapOf(guildId to 2)
        every { repo.countActiveByGuild(guildId) } returns 2

        val service = serviceWith(StrikesConfig(enabled = true), repo)
        assertEquals(mapOf(guildId to 2), service.getAllActiveCounts())
        assertEquals(2, service.countActiveByGuild(guildId))
    }

    @Test
    fun `GuildStrike defaults active to true`() {
        val strike = GuildStrike(
            guildId = guildId,
            playerUuid = playerUuid,
            punishmentType = "KICK",
            issuedAt = now
        )
        assertTrue(strike.active)
    }
}
