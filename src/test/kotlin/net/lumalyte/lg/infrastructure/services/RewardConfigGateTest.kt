package net.lumalyte.lg.infrastructure.services

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Test
import kotlin.test.*

class RewardConfigGateTest {
    @Test fun `chapter two rollout defaults on and explicit false follows reload`() {
        var config = YamlConfiguration()
        val service = ConfigServiceBukkit { config }
        assertTrue(service.loadConfig().chapterTwoRewardsEnabled)
        config = YamlConfiguration().apply { set("progression.chapter_two_rewards_enabled", false) }
        assertFalse(service.loadConfig().chapterTwoRewardsEnabled)
        config = YamlConfiguration()
        assertTrue(service.loadConfig().chapterTwoRewardsEnabled)
    }
}
