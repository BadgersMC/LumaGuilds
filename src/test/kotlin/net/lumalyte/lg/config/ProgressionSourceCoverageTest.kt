package net.lumalyte.lg.config

import net.lumalyte.lg.domain.values.ExperienceSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProgressionSourceCoverageTest {
    @Test
    fun `every enabled chapter two source has an award producer`() {
        val playerEventSources = setOf(
            ExperienceSource.PLAYER_KILL,
            ExperienceSource.MOB_KILL,
            ExperienceSource.CROP_BREAK,
            ExperienceSource.BLOCK_BREAK,
            ExperienceSource.BLOCK_PLACE,
            ExperienceSource.SMELTING,
            ExperienceSource.BREWING,
            ExperienceSource.FISHING,
            ExperienceSource.ENCHANTING,
            ExperienceSource.EXPLORATION_MILESTONE,
            ExperienceSource.COAL_ORE,
            ExperienceSource.COPPER_ORE,
            ExperienceSource.IRON_ORE,
            ExperienceSource.LAPIS_ORE,
            ExperienceSource.REDSTONE_ORE,
            ExperienceSource.GOLD_ORE,
            ExperienceSource.NETHER_QUARTZ_ORE,
            ExperienceSource.DIAMOND_ORE,
            ExperienceSource.EMERALD_ORE,
            ExperienceSource.ANCIENT_DEBRIS,
            ExperienceSource.CRAFT_COMMON,
            ExperienceSource.CRAFT_UTILITY,
            ExperienceSource.CRAFT_EQUIPMENT,
            ExperienceSource.CRAFT_RARE,
            ExperienceSource.ENDER_DRAGON_KILL,
            ExperienceSource.WITHER_KILL,
            ExperienceSource.ELDER_GUARDIAN_KILL,
            ExperienceSource.WARDEN_KILL,
        )
        val serviceSources = setOf(
            ExperienceSource.BANK_DEPOSIT,
            ExperienceSource.QUALIFIED_RECRUIT,
            ExperienceSource.PRE_CAP_WAR_WIN,
            ExperienceSource.WEEKLY_ACTIVITY,
            ExperienceSource.ADMIN_BONUS,
        )
        val enabled = ChapterTwoExperiencePolicies.defaults().values
            .filter { it.enabled }
            .map { it.source }
            .toSet()

        assertEquals(enabled, playerEventSources + serviceSources)
    }
}
