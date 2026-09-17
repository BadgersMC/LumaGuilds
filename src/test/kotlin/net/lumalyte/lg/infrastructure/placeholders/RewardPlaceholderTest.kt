package net.lumalyte.lg.infrastructure.placeholders

import net.lumalyte.lg.domain.rewards.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RewardPlaceholderTest {
    @Test fun `placeholder distinguishes unavailable data from zero or unowned rewards`() {
        assertEquals("unavailable", RewardPlaceholder.value("guild_reward_state", GuildRewardRead.Unavailable))
        assertEquals("disabled", RewardPlaceholder.value("guild_reward_state", GuildRewardRead.Disabled))
        assertEquals("", RewardPlaceholder.value("guild_reward_bank_capacity", GuildRewardRead.Unavailable))
        val state = GuildRewardRead.Available(100, 0,
            RewardEntitlementResolver(RewardCatalog.chapterTwo()).resolve(100, RewardOwnership()))
        assertEquals("47500", RewardPlaceholder.value("guild_reward_bank_capacity", state))
        assertEquals("1", RewardPlaceholder.value("guild_reward_home_capacity", state))
        assertEquals("50", RewardPlaceholder.value("guild_reward_member_capacity", state))
        assertEquals("AVAILABLE", RewardPlaceholder.value("guild_reward_offer_home-1", state))
        assertEquals("false", RewardPlaceholder.value("guild_reward_ally_homes", state))
        assertEquals("", RewardPlaceholder.value("guild_reward_offer_unknown", state))
    }
}
