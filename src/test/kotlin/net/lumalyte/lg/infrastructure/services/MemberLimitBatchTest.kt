package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.config.LevelRewardConfig
import net.lumalyte.lg.config.ProgressionSystemConfig
import net.lumalyte.lg.domain.entities.GuildProgression
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

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
}
