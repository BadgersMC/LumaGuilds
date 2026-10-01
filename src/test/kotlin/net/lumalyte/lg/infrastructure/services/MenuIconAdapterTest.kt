package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PlatformDetectionService
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
    private val adapter = MenuIconAdapter(mockk<Plugin>(relaxed = true), platform, guildService)

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
    fun `bedrock players always get vanilla icons`() {
        val p = player(bedrock = true, theme = GuiTheme.ENTHUSIA)
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
        val p = player(bedrock = true, theme = null)
        adapter.refresh(p)
        adapter.forget(p.uniqueId)
        assertFalse(adapter.showsVanillaIcons(p.uniqueId))
    }
}
