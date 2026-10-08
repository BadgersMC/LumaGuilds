package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.values.GuildShopXpPolicy
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GuildShopXpConfigurationTest {
    @Test fun `missing settings use approved defaults and explicit settings override each limit`() {
        val yaml = YamlConfiguration()
        val service = ConfigServiceBukkit { yaml }
        assertEquals(GuildShopXpPolicy(), service.loadConfig().progression.shopXp)
        yaml.set("progression.shop_xp.enabled", false)
        yaml.set("progression.shop_xp.xp_per_sale", 3)
        yaml.set("progression.shop_xp.guild_daily_cap", 90)
        yaml.set("progression.shop_xp.buyer_daily_cap", 9)
        yaml.set("progression.shop_xp.pair_cooldown_seconds", 60)
        assertEquals(GuildShopXpPolicy(false, 3, 90, 9, 60), service.loadConfig().progression.shopXp)
    }

    @Test fun `invalid limits fail explicitly rather than becoming unlimited`() {
        assertFailsWith<IllegalArgumentException> { GuildShopXpPolicy(buyerDailyCap = -1) }
        assertFailsWith<IllegalArgumentException> { GuildShopXpPolicy(pairCooldownSeconds = Long.MAX_VALUE) }
    }
}
