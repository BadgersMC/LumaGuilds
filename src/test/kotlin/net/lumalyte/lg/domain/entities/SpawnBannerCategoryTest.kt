package net.lumalyte.lg.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpawnBannerCategoryTest {
    @Test
    fun aliasesResolveToStableCategories() {
        assertEquals(SpawnBannerCategory.LEVEL, SpawnBannerCategory.parse("level"))
        assertEquals(SpawnBannerCategory.WEALTH, SpawnBannerCategory.parse("bank"))
        assertEquals(SpawnBannerCategory.MEMBERS, SpawnBannerCategory.parse("member_count"))
        assertEquals(SpawnBannerCategory.WEEKLY_ACTIVITY, SpawnBannerCategory.parse("weekly"))
        assertNull(SpawnBannerCategory.parse("not-a-board"))
    }

    @Test
    fun categoriesMapToGuildLeaderboardTypes() {
        assertEquals(ExtendedLeaderboardType.GUILD_LEVEL, SpawnBannerCategory.LEVEL.leaderboardType)
        assertEquals(ExtendedLeaderboardType.GUILD_KILLS, SpawnBannerCategory.KILLS.leaderboardType)
        assertEquals(ExtendedLeaderboardType.GUILD_BANK_BALANCE, SpawnBannerCategory.WEALTH.leaderboardType)
        assertEquals(LeaderboardPeriod.WEEKLY, SpawnBannerCategory.WEEKLY_ACTIVITY.period)
    }
}
