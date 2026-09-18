package net.lumalyte.lg.infrastructure.services

import io.mockk.*
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.application.persistence.RewardStateRepository
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.rewards.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

class ChapterTwoRewardConsumersTest {
    @Test fun `chapter two level ups never advertise legacy perks as granted`() {
        val main = MainConfig(chapterTwoRewardsEnabled = true)
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns main
        val progression = ProgressionServiceBukkit(mockk(), mockk(), mockk(), config, mockk(), mockk(),
            mockk(), mockk(), mockk())
        assertTrue(progression.getPerksForLevel(12).isEmpty())
        assertTrue(progression.getPerksForLevel(5).isEmpty())
        main.chapterTwoRewardsEnabled = false
        assertTrue(progression.getPerksForLevel(12).isNotEmpty())
    }

    @Test fun `gold home membership and progression consumers agree on purchased effects`() {
        val guild = UUID.randomUUID()
        val main = MainConfig()
        main.bank.maxBankBalance = 100_000
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns main
        val states = mockk<RewardStateRepository>()
        every { states.read(guild) } returns RewardStateRead.Found(100, RewardOwnershipSnapshot(2,
            RewardOwnership(currentRun = setOf("bank-1", "fee-1", "cooldown-1"), permanent = setOf("home-1"))))
        val rewards = GuildRewardService(states, RewardCatalog.chapterTwo(),
            { RewardReadSettings(true, main.bank.maxBankBalance.toLong(), main.guild.maxMembersPerGuild) })
        val progression = ProgressionServiceBukkit(mockk(), mockk(), mockk(), config, mockk(), mockk(),
            mockk(), mockk(), mockk(), rewards)
        val members = MemberServiceBukkit(mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), rewards)
        val gold = ConfiguredGuildGoldSettings(config, mockk(), mockk(), rewards).settingsFor(guild)
        assertEquals(48_500L, gold.effectiveCapacity)
        assertEquals(gold.effectiveCapacity, progression.getMaxBankBalance(guild).toLong())
        assertEquals(2, progression.getMaxHomes(guild))
        assertEquals(50, members.getMemberLimit(guild))
        assertEquals(members.getMemberLimit(guild), progression.getMaxMembers(guild))
        assertEquals(0.9, progression.getWithdrawalFeeMultiplier(guild))
        assertEquals(main.bank.withdrawalFeePercent * 0.9, gold.policy.withdrawalFeePercent)
        assertEquals(0.9, progression.getHomeCooldownMultiplier(guild))
        assertFalse(progression.hasPerkUnlocked(guild, net.lumalyte.lg.domain.values.PerkType.ALLY_HOME_ACCESS))
        every { states.read(guild) } returns RewardStateRead.Failed
        assertFailsWith<RewardStateUnavailableException> { progression.getMaxHomes(guild) }
        assertFailsWith<RewardStateUnavailableException> { members.getMemberLimit(guild) }
        assertFailsWith<RewardStateUnavailableException> { ConfiguredGuildGoldSettings(config, mockk(), mockk(), rewards).settingsFor(guild) }
    }
}
