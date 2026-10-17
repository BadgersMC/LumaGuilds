package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.WarBannerService
import net.lumalyte.lg.common.PluginKeys
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.config.WarBannerConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.WarBannerState
import net.lumalyte.lg.utils.serializeToString
import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.block.Banner
import org.bukkit.block.banner.Pattern
import org.bukkit.block.banner.PatternType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarBannerServiceBukkitTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: Plugin

    @BeforeEach
    fun setup() {
        server = MockBukkit.mock()
        plugin = MockBukkit.createMockPlugin("LumaGuilds")
        PluginKeys.initialize(plugin)
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `guild without configured banner receives white deployable banner`() {
        val guild = Guild(
            id = UUID.randomUUID(),
            name = "White Flag",
            createdAt = Instant.EPOCH,
        )

        val item = subject().createDeployableItem(guild)

        assertEquals(Material.WHITE_BANNER, item.type)
        assertEquals(guild.id, subject().itemGuildId(item))
    }

    @Test
    fun `deployable item preserves exact guild base color and ordered patterns`() {
        val patterns = listOf(
            Pattern(DyeColor.WHITE, PatternType.STRIPE_TOP),
            Pattern(DyeColor.RED, PatternType.CROSS),
            Pattern(DyeColor.BLACK, PatternType.BORDER),
        )
        val stored = banner(Material.BLUE_BANNER, patterns)
        val guild = Guild(
            id = UUID.randomUUID(),
            name = "Blue Legion",
            banner = stored.serializeToString(),
            createdAt = Instant.EPOCH,
        )

        val item = subject().createDeployableItem(guild)

        assertEquals(Material.BLUE_BANNER, item.type)
        assertEquals(patterns, (item.itemMeta as BannerMeta).patterns)
        assertEquals(guild.id, subject().itemGuildId(item))
    }

    @Test
    fun `placed tactical banner is recolored and patterned from current guild banner`() {
        val patterns = listOf(
            Pattern(DyeColor.YELLOW, PatternType.CROSS),
            Pattern(DyeColor.BLACK, PatternType.BORDER),
        )
        val guild = Guild(
            id = UUID.randomUUID(),
            name = "Sun Guard",
            banner = banner(Material.RED_BANNER, patterns).serializeToString(),
            createdAt = Instant.EPOCH,
        )
        val world = server.addSimpleWorld("war")
        val block = world.getBlockAt(4, 70, 4)
        block.type = Material.WHITE_BANNER
        val state = WarBannerState(
            guildId = guild.id,
            bannerId = UUID.randomUUID(),
            worldId = world.uid,
            x = block.x,
            y = block.y,
            z = block.z,
            placedBy = UUID.randomUUID(),
            transactionId = UUID.randomUUID(),
            placedAt = 1L,
            expiresAt = 901_000L,
            cooldownUntil = 901_000L,
            active = true,
        )

        assertTrue(subject().renderPlaced(block, guild, state))
        assertEquals(Material.RED_BANNER, block.type)
        val placed = block.state as Banner
        assertEquals(patterns, placed.patterns)
        assertEquals(
            guild.id.toString(),
            placed.persistentDataContainer.get(
                PluginKeys.WAR_BANNER_GUILD_ID,
                PersistentDataType.STRING,
            ),
        )
        assertEquals(
            state.bannerId.toString(),
            placed.persistentDataContainer.get(
                PluginKeys.WAR_BANNER_ID,
                PersistentDataType.STRING,
            ),
        )
    }

    @Test
    fun `member tactical teleport uses combat aware teleport service directly`() {
        val guildId = UUID.randomUUID()
        val world = server.addSimpleWorld("teleport-war")
        val block = world.getBlockAt(12, 72, -3)
        block.type = Material.WHITE_BANNER
        val bannerId = UUID.randomUUID()
        val placed = block.state as Banner
        placed.persistentDataContainer.set(
            PluginKeys.WAR_BANNER_ID,
            PersistentDataType.STRING,
            bannerId.toString(),
        )
        placed.update(true, false)

        val player = server.addPlayer()
        val state = WarBannerState(
            guildId = guildId,
            bannerId = bannerId,
            worldId = world.uid,
            x = block.x,
            y = block.y,
            z = block.z,
            placedBy = UUID.randomUUID(),
            transactionId = UUID.randomUUID(),
            placedAt = 1L,
            expiresAt = 901_000L,
            cooldownUntil = 901_000L,
            active = true,
        )
        val core = mockk<WarBannerService>(relaxed = true) {
            every { activeForMember(player.uniqueId, guildId, 10L) } returns state
        }
        val teleports = mockk<TeleportationService>(relaxed = true)

        assertEquals(
            WarBannerTeleportResult.STARTED,
            subject(core, teleports).teleport(player, guildId, 10L),
        )
        verify(exactly = 1) {
            teleports.startTeleport(
                player,
                match {
                    it.world?.uid == world.uid &&
                        it.blockX == block.x &&
                        it.blockY == block.y &&
                        it.blockZ == block.z
                },
            )
        }
    }

    private fun subject(
        core: WarBannerService = mockk(relaxed = true),
        teleportationService: TeleportationService = mockk(relaxed = true),
    ): WarBannerServiceBukkit {
        val config = mockk<ConfigService> {
            every { loadConfig() } returns MainConfig(
                warBanner = WarBannerConfig(rawGoldCost = 50, cooldownMinutes = 15),
            )
        }
        val lang = mockk<LangService> {
            every { msg(any(), *anyVararg()) } returns Component.text("localized")
            every { raw(any()) } returns "localized"
        }
        return WarBannerServiceBukkit(
            plugin = plugin,
            core = core,
            configService = config,
            teleportationService = teleportationService,
            lang = lang,
        )
    }

    private fun banner(material: Material, patterns: List<Pattern>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta as BannerMeta
        meta.patterns = patterns
        item.itemMeta = meta
        return item
    }
}
