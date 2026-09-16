package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.config.LevelRewardConfig
import net.lumalyte.lg.config.ProgressionSystemConfig
import net.lumalyte.lg.domain.entities.GuildProgression
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class ConfiguredGuildGoldSettingsTest {
    @Test fun `fee and capacity share one fresh config and progression snapshot`() {
        val guild = UUID.randomUUID()
        val main = MainConfig()
        main.bank.maxBankBalance = 2_000
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns main
        val progression = mockk<ProgressionRepository>()
        var level = 5
        every { progression.getGuildProgression(guild) } answers { GuildProgression(guild, currentLevel = level) }
        val rewards = mockk<ProgressionSystemConfig>()
        every { rewards.getActiveLevelRewards() } returns mapOf(
            1 to LevelRewardConfig(bankLimit = 500),
            5 to LevelRewardConfig(bankLimit = 3_000, withdrawalFeeMultiplier = 0.5))
        val rewardConfig = mockk<ProgressionConfigService>()
        every { rewardConfig.getProgressionConfig() } returns rewards
        val provider = ConfiguredGuildGoldSettings(config, progression, rewardConfig)

        val first = provider.settingsFor(guild)
        assertEquals(2_000, first.effectiveCapacity)
        assertEquals(0.01, first.policy.withdrawalFeePercent)
        verify(exactly = 1) { config.loadConfig(); progression.getGuildProgression(guild); rewards.getActiveLevelRewards() }

        level = 1
        main.bank.withdrawalFeePercent = 0.04
        val next = provider.settingsFor(guild)
        assertEquals(500, next.effectiveCapacity)
        assertEquals(0.04, next.policy.withdrawalFeePercent)
        assertEquals(0.01, first.policy.withdrawalFeePercent)
        verify(exactly = 2) { config.loadConfig(); progression.getGuildProgression(guild); rewards.getActiveLevelRewards() }
    }
}
