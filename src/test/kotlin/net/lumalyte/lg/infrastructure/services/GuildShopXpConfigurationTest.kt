package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.values.GuildShopXpPolicy
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Explicit fixture overrides prove configurable amounts, independent of approved default values.
@Suppress("MagicNumber")
internal class GuildShopXpConfigurationTest {
    @DisplayName("missing settings use approved defaults and explicit settings override each limit")
    @Test
    fun scenario1() {
        val yaml = YamlConfiguration()
        val service = ConfigServiceBukkit { yaml }
        assertEquals(GuildShopXpPolicy(), service.loadConfig().progression.shopXp)
        yaml.set("progression.shop_xp.enabled", true)
        yaml.set("progression.shop_xp.xp_per_sale", 3)
        yaml.set("progression.shop_xp.guild_daily_cap", 90)
        yaml.set("progression.shop_xp.buyer_daily_cap", 9)
        yaml.set("progression.shop_xp.pair_cooldown_seconds", 60)
        assertEquals(GuildShopXpPolicy(true, 3, 90, 9, 60), service.loadConfig().progression.shopXp)
    }

    @DisplayName("invalid limits fail explicitly rather than becoming unlimited")
    @Test
    fun scenario2() {
        assertFailsWith<IllegalArgumentException> { GuildShopXpPolicy(buyerDailyCap = -1) }
        assertFailsWith<IllegalArgumentException> { GuildShopXpPolicy(pairCooldownSeconds = Long.MAX_VALUE) }
    }
}
