package net.lumalyte.lg.infrastructure.services

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Test
import kotlin.test.*

class RewardConfigGateTest {
    @Test fun `chapter two rollout defaults off and follows reload`() {
        var config = YamlConfiguration()
        val service = ConfigServiceBukkit { config }
        assertFalse(service.loadConfig().chapterTwoRewardsEnabled)
        config = YamlConfiguration().apply { set("progression.chapter_two_rewards_enabled", true) }
        assertTrue(service.loadConfig().chapterTwoRewardsEnabled)
        config = YamlConfiguration()
        assertFalse(service.loadConfig().chapterTwoRewardsEnabled)
    }
}
