package net.lumalyte.lg.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.WarBannerService
import net.lumalyte.lg.common.PluginKeys
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.WarBannerState
import net.lumalyte.lg.infrastructure.services.WarBannerServiceBukkit
import org.bukkit.Material
import org.bukkit.block.Banner
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse

class WarBannerListenerTest {
    private lateinit var server: ServerMock

    @BeforeEach
    fun setup() {
        server = MockBukkit.mock()
        PluginKeys.initialize(MockBukkit.createMockPlugin("LumaGuilds"))
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `war banner break overrides prior protection cancellation for any player`() {
        val guild = Guild(UUID.randomUUID(), "Frontline", createdAt = Instant.EPOCH)
        val world = server.addSimpleWorld("war")
        val block = world.getBlockAt(8, 70, 8)
        block.type = Material.WHITE_BANNER
        val bannerId = UUID.randomUUID()
        val banner = block.state as Banner
        banner.persistentDataContainer.set(
            PluginKeys.WAR_BANNER_ID,
            PersistentDataType.STRING,
            bannerId.toString(),
        )
        banner.update(true, false)

        val state = WarBannerState(
            guildId = guild.id,
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
        val core = mockk<WarBannerService> {
            every { destroyAt(world.uid, block.x, block.y, block.z) } returns state
        }

        val guilds = mockk<GuildService> {
            every { getGuild(guild.id) } returns guild
        }
        val lang = mockk<LangService> {
            every { msg(any(), *anyVararg()) } returns Component.text("localized")
        }
        val listener = WarBannerListener(
            core = core,
            bukkit = mockk<WarBannerServiceBukkit>(relaxed = true),
            guildService = guilds,
            lang = lang,
        )
        val attacker = server.addPlayer()
        val event = BlockBreakEvent(block, attacker)
        event.isCancelled = true

        listener.onBreak(event)

        assertFalse(event.isCancelled)
        assertFalse(event.isDropItems)
        verify(exactly = 1) {
            core.destroyAt(world.uid, block.x, block.y, block.z)
        }
    }
}
