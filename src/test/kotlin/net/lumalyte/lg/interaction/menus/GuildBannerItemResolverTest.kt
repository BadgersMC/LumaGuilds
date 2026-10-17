package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.utils.serializeToString
import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.block.banner.Pattern
import org.bukkit.block.banner.PatternType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID

class GuildBannerItemResolverTest {
    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `valid physical banner preserves material and patterns`() {
        val source = ItemStack(Material.RED_BANNER)
        val meta = source.itemMeta as BannerMeta
        meta.addPattern(Pattern(DyeColor.WHITE, PatternType.BORDER))
        source.itemMeta = meta

        val resolved = GuildBannerItemResolver.resolve(
            guild(source.serializeToString())
        )

        assertEquals(Material.RED_BANNER, resolved.type)
        assertEquals(
            (source.itemMeta as BannerMeta).patterns,
            (resolved.itemMeta as BannerMeta).patterns,
        )
    }

    @Test
    fun `missing corrupt and non banner payloads fall back to white`() {
        assertEquals(
            Material.WHITE_BANNER,
            GuildBannerItemResolver.resolve(guild(null)).type,
        )
        assertEquals(
            Material.WHITE_BANNER,
            GuildBannerItemResolver.resolve(guild("not-valid-banner-data")).type,
        )
        assertEquals(
            Material.WHITE_BANNER,
            GuildBannerItemResolver.resolve(
                guild(ItemStack(Material.STONE).serializeToString())
            ).type,
        )
    }

    @Test
    fun `resolver explicitly restricts display to standing banner materials`() {
        val source = java.io.File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/GuildBannerItemResolver.kt"
        ).readText()
        org.junit.jupiter.api.Assertions.assertTrue(
            source.contains("!item.type.name.endsWith(\"_WALL_BANNER\")")
        )
    }

    private fun guild(banner: String?) = Guild(
        id = UUID.randomUUID(),
        name = "Banner Guild",
        banner = banner,
        createdAt = Instant.EPOCH,
    )
}
