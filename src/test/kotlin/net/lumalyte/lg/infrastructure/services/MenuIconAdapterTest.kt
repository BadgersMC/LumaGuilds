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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** Which players are sent vanilla icons or plain titles, and when the packet listener hooks in. */
@Suppress("TooManyFunctions")
internal class MenuIconAdapterTest {
    private val guildService = mockk<GuildService>()
    private val platform = mockk<PlatformDetectionService>()
    private var bedrock = BedrockConfig()
    private var packetEventsUp = false
    private var hooked = 0
    private val adapter =
        MenuIconAdapter(
            plugin = mockk<Plugin>(relaxed = true),
            platform = platform,
            guildService = guildService,
            bedrockConfig = { bedrock },
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

    /** Java player in a themed guild keeps custom icons. */
    @Test
    fun themedJavaKeepsIcons() {
        val p = player(bedrock = false, theme = GuiTheme.ENTHUSIA)
        adapter.refresh(p)
        assertFalse(adapter.showsVanillaIcons(p.uniqueId))
    }

    /** Java player in a vanilla-style guild gets vanilla icons. */
    @Test
    fun vanillaStyleJavaGetsVanilla() {
        val p = player(bedrock = false, theme = GuiTheme.VANILLA)
        adapter.refresh(p)
        assertTrue(adapter.showsVanillaIcons(p.uniqueId))
    }

    /** Switching the guild back to a theme restores custom icons. */
    @Test
    fun themeSwitchRestoresIcons() {
        val p = player(bedrock = false, theme = GuiTheme.VANILLA)
        adapter.refresh(p)
        val themed = mockk<Guild> { every { guiTheme } returns GuiTheme.OBSIDIAN }
        every { guildService.getPlayerGuilds(p.uniqueId) } returns setOf(themed)
        adapter.refresh(p)
        assertFalse(adapter.showsVanillaIcons(p.uniqueId))
    }

    /** Bedrock players keep mapped custom icons by default. */
    @Test
    fun bedrockKeepsIconsByDefault() {
        // Geyser custom-item mappings already draw lg_ icons for Bedrock; the swap must be opt-in.
        val p = player(bedrock = true, theme = GuiTheme.ENTHUSIA)
        adapter.refresh(p)
        assertFalse(adapter.showsVanillaIcons(p.uniqueId))
        assertFalse(adapter.cleansTitlesFor(p.uniqueId))
    }

    /** Bedrock vanilla icons and plain titles are opt-in. */
    @Test
    fun bedrockFallbacksAreOptIn() {
        bedrock = BedrockConfig(javaMenuVanillaIcons = true, javaMenuPlainTitles = true)
        val p = player(bedrock = true, theme = GuiTheme.ENTHUSIA)
        adapter.refresh(p)
        assertTrue(adapter.showsVanillaIcons(p.uniqueId))
        assertTrue(adapter.cleansTitlesFor(p.uniqueId))
    }

    /** Vanilla-style guild members get vanilla icons regardless of bedrock settings. */
    @Test
    fun vanillaStyleBedrockGetsVanilla() {
        val p = player(bedrock = true, theme = GuiTheme.VANILLA)
        adapter.refresh(p)
        assertTrue(adapter.showsVanillaIcons(p.uniqueId))
    }

    /** Players without a guild keep custom icons. */
    @Test
    fun guildlessPlayerKeepsIcons() {
        val p = player(bedrock = false, theme = null)
        adapter.refresh(p)
        assertFalse(adapter.showsVanillaIcons(p.uniqueId))
    }

    /** Members of a holiday-style guild are sent that style's icons (REQ-121). */
    @Test
    fun holidayStyleGetsSeasonalIcons() {
        val p = player(bedrock = false, theme = GuiTheme.HALLOWEEN)
        adapter.refresh(p)
        assertFalse(adapter.showsVanillaIcons(p.uniqueId))
        assertEquals(GuiTheme.HALLOWEEN, adapter.seasonalStyleFor(p.uniqueId))
    }

    /** Other styles keep the normal icons. */
    @Test
    fun otherStylesGetNoSeasonalIcons() {
        val p = player(bedrock = false, theme = GuiTheme.FROSTBOUND)
        adapter.refresh(p)
        assertNull(adapter.seasonalStyleFor(p.uniqueId))
    }

    /** Vanilla icons win over seasonal ones (Bedrock opt-in). */
    @Test
    fun vanillaIconsWinOverSeasonal() {
        bedrock = BedrockConfig(javaMenuVanillaIcons = true)
        val p = player(bedrock = true, theme = GuiTheme.CHRISTMAS)
        adapter.refresh(p)
        assertTrue(adapter.showsVanillaIcons(p.uniqueId))
        assertNull(adapter.seasonalStyleFor(p.uniqueId))
    }

    /** Switching away from a holiday style, or leaving, restores the normal icons. */
    @Test
    fun seasonalStyleClears() {
        val p = player(bedrock = false, theme = GuiTheme.CHRISTMAS)
        adapter.refresh(p)
        val themed = mockk<Guild> { every { guiTheme } returns GuiTheme.ENTHUSIA }
        every { guildService.getPlayerGuilds(p.uniqueId) } returns setOf(themed)
        adapter.refresh(p)
        assertNull(adapter.seasonalStyleFor(p.uniqueId))
        val festive = mockk<Guild> { every { guiTheme } returns GuiTheme.CHRISTMAS }
        every { guildService.getPlayerGuilds(p.uniqueId) } returns setOf(festive)
        adapter.refresh(p)
        adapter.forget(p.uniqueId)
        assertNull(adapter.seasonalStyleFor(p.uniqueId))
    }

    /** Forgetting a player clears the decision. */
    @Test
    fun forgetClearsDecision() {
        val p = player(bedrock = false, theme = GuiTheme.VANILLA)
        adapter.refresh(p)
        adapter.forget(p.uniqueId)
        assertFalse(adapter.showsVanillaIcons(p.uniqueId))
    }

    /** Packet listener hooks in when packetevents enables after lumaguilds. */
    @Test
    fun hooksWhenPacketEventsEnables() {
        // Seen on SMP Test: packetevents enabled after LumaGuilds despite the softdepend.
        adapter.register()
        assertTrue(hooked == 0)
        packetEventsUp = true
        adapter.pluginEnabled("packetevents")
        assertTrue(hooked == 1)
        adapter.pluginEnabled("packetevents")
        assertTrue(hooked == 1, "must hook only once")
    }

    /** Packet listener hooks in immediately when packetevents is already up. */
    @Test
    fun hooksImmediatelyWhenUp() {
        packetEventsUp = true
        adapter.register()
        assertTrue(hooked == 1)
    }

    /** Other plugins enabling do not hook the listener. */
    @Test
    fun otherPluginsDoNotHook() {
        adapter.register()
        packetEventsUp = true
        adapter.pluginEnabled("Nexo")
        assertTrue(hooked == 0)
    }
}
