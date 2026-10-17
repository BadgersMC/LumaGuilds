package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.config.LevelRewardConfig
import net.lumalyte.lg.config.ProgressionSystemConfig
import net.lumalyte.lg.domain.entities.GuildProgression
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import org.bukkit.Bukkit
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberLimitBatchTest {
    @Test fun `listing reuses one reward snapshot but preserves each guild level`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val unknown = UUID.randomUUID()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(first) } returns GuildProgression(first, currentLevel = 1)
        every { progression.getGuildProgression(second) } returns GuildProgression(second, currentLevel = 5)
        every { progression.getGuildProgression(unknown) } returns null
        val config = mockk<ProgressionSystemConfig>()
        every { config.getActiveLevelRewards() } returns mapOf(1 to LevelRewardConfig(members = 10), 5 to LevelRewardConfig(members = 30))
        val configs = mockk<ProgressionConfigService>()
        every { configs.getProgressionConfig() } returns config
        val service = MemberServiceBukkit(mockk(), mockk(), mockk(), progression, configs, mockk(), mockk())
        assertEquals(mapOf(first to 10, second to 30, unknown to 10), service.getMemberLimits(setOf(first, second, unknown)))
        verify(exactly = 1) { config.getActiveLevelRewards() }
    }

    @Test
    fun `member rank changes invalidate cached claim permissions`() {
        val playerId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val guildId = UUID.randomUUID()
        val oldRank = Rank(UUID.randomUUID(), guildId, "Member", 4)
        val newRank = Rank(UUID.randomUUID(), guildId, "Trusted", 3)
        val member = Member(playerId, guildId, oldRank.id, Instant.now())

        val members = mockk<MemberRepository>()
        val ranks = mockk<RankRepository>()
        val override = mockk<AdminOverrideService>()
        every { override.hasOverride(actorId) } returns true
        every { members.getByPlayerAndGuild(playerId, guildId) } returns member
        every { ranks.getById(oldRank.id) } returns oldRank
        every { ranks.getById(newRank.id) } returns newRank
        every { members.update(any()) } returns true

        val invalidated = mutableListOf<UUID>()
        val service = MemberServiceBukkit(
            members,
            ranks,
            mockk<GuildRepository>(relaxed = true),
            mockk<ProgressionRepository>(relaxed = true),
            mockk<ProgressionConfigService>(relaxed = true),
            mockk<MembershipHistoryRepository>(relaxed = true),
            override,
            invalidateClaimPermissionCache = { invalidated += it },
        )

        assertTrue(service.changeMemberRank(playerId, guildId, newRank.id, actorId))
        assertEquals(listOf(playerId), invalidated)
    }

    @Test
    fun `leaving a guild invalidates cached claim membership`() {
        val playerId = UUID.randomUUID()
        val guildId = UUID.randomUUID()
        val members = mockk<MemberRepository>()
        every { members.isPlayerInGuild(playerId, guildId) } returns true
        every { members.remove(playerId, guildId) } returns true
        val invalidated = mutableListOf<UUID>()

        mockkStatic(Bukkit::class)
        try {
            every { Bukkit.getPluginManager() } returns mockk(relaxed = true)
            val service = MemberServiceBukkit(
                members,
                mockk<RankRepository>(relaxed = true),
                mockk<GuildRepository>(relaxed = true),
                mockk<ProgressionRepository>(relaxed = true),
                mockk<ProgressionConfigService>(relaxed = true),
                mockk<MembershipHistoryRepository>(relaxed = true),
                mockk<AdminOverrideService>(relaxed = true),
                invalidateClaimPermissionCache = { invalidated += it },
            )

            assertTrue(service.removeMember(playerId, guildId, playerId))
            assertEquals(listOf(playerId), invalidated)
        } finally {
            unmockkStatic(Bukkit::class)
        }
    }
}
