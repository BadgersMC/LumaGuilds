package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.BedrockConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.utils.GuiTheme
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class MenuIconAdapterTest {
    private val guildService = mockk<GuildService>()
    private val platform = mockk<PlatformDetectionService>()
    private var bedrock = BedrockConfig()
    private var packetEventsUp = false
    private var hooked = 0
    private val adapter = MenuIconAdapter(
        mockk<Plugin>(relaxed = true), platform, guildService, { bedrock },
        packetEventsReady = { packetEventsUp },
        hookPacketEvents = { hooked++ },
    )

    private fun player(bedrock: Boolean, theme: GuiTheme?): Player {
        val id = UUID.randomUUID()
        val p = mockk<Player>()
        every { p.uniqueId } returns id
        every { platform.isBedrockPlayer(p) } returns bedrock
        val guilds = if (theme == null) emptySet() else setOf(mockk<Guild> { every { guiTheme } returns theme })
        every { guildService.getPlayerGuilds(id) } returns guilds
        return p
    }

    @Test
    fun `java player in a themed guild keeps custom icons`() {
        val p = player(bedrock = false, theme = GuiTheme.ENTHUSIA)
        adapter.refresh(p)
        assertFalse(adapter.showsVanillaIcons(p.uniqueId))
    }

    @Test
    fun `java player in a vanilla-style guild gets vanilla icons`() {
        val p = player(bedrock = false, theme = GuiTheme.VANILLA)
        adapter.refresh(p)
        assertTrue(adapter.showsVanillaIcons(p.uniqueId))
    }

    @Test
    fun `switching the guild back to a theme restores custom icons`() {
        val p = player(bedrock = false, theme = GuiTheme.VANILLA)
        adapter.refresh(p)
        every { guildService.getPlayerGuilds(p.uniqueId) } returns setOf(mockk<Guild> { every { guiTheme } returns GuiTheme.OBSIDIAN })
        adapter.refresh(p)
        assertFalse(adapter.showsVanillaIcons(p.uniqueId))
    }

    @Test
    fun `bedrock players keep mapped custom icons by default`() {
        // Geyser custom-item mappings already draw lg_ icons for Bedrock; the swap must be opt-in.
        val p = player(bedrock = true, theme = GuiTheme.ENTHUSIA)
        adapter.refresh(p)
        assertFalse(adapter.showsVanillaIcons(p.uniqueId))
        assertFalse(adapter.cleansTitlesFor(p.uniqueId))
    }

    @Test
    fun `bedrock vanilla icons and plain titles are opt-in`() {
        bedrock = BedrockConfig(javaMenuVanillaIcons = true, javaMenuPlainTitles = true)
        val p = player(bedrock = true, theme = GuiTheme.ENTHUSIA)
        adapter.refresh(p)
        assertTrue(adapter.showsVanillaIcons(p.uniqueId))
        assertTrue(adapter.cleansTitlesFor(p.uniqueId))
    }

    @Test
    fun `vanilla-style guild members get vanilla icons regardless of bedrock settings`() {
        val p = player(bedrock = true, theme = GuiTheme.VANILLA)
        adapter.refresh(p)
        assertTrue(adapter.showsVanillaIcons(p.uniqueId))
    }

    @Test
    fun `players without a guild keep custom icons`() {
        val p = player(bedrock = false, theme = null)
        adapter.refresh(p)
        assertFalse(adapter.showsVanillaIcons(p.uniqueId))
    }

    @Test
    fun `forgetting a player clears the decision`() {
        val p = player(bedrock = false, theme = GuiTheme.VANILLA)
        adapter.refresh(p)
        adapter.forget(p.uniqueId)
        assertFalse(adapter.showsVanillaIcons(p.uniqueId))
    }

    @Test
    fun `packet listener hooks in when packetevents enables after lumaguilds`() {
        // Seen on SMP Test: packetevents enabled after LumaGuilds despite the softdepend.
        adapter.register()
        assertTrue(hooked == 0)
        packetEventsUp = true
        adapter.pluginEnabled("packetevents")
        assertTrue(hooked == 1)
        adapter.pluginEnabled("packetevents")
        assertTrue(hooked == 1, "must hook only once")
    }

    @Test
    fun `packet listener hooks in immediately when packetevents is already up`() {
        packetEventsUp = true
        adapter.register()
        assertTrue(hooked == 1)
    }

    @Test
    fun `other plugins enabling do not hook the listener`() {
        adapter.register()
        packetEventsUp = true
        adapter.pluginEnabled("Nexo")
        assertTrue(hooked == 0)
    }
}
