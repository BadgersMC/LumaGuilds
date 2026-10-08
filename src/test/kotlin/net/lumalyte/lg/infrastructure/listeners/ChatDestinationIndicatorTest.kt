package net.lumalyte.lg.infrastructure.listeners

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import net.lumalyte.lg.infrastructure.services.RoseChatAdapter
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID

internal class ChatDestinationIndicatorTest {
    @AfterEach fun close() { unmockkAll(); if (MockBukkit.isMocked()) MockBukkit.unmock() }

    @Test fun optInUnknownChannelToggleAndQuitCleanup() {
        MockBukkit.mock()
        mockkStatic(Bukkit::class)
        val player = mockk<Player>(relaxed = true)
        val id = UUID.randomUUID()
        every { player.uniqueId } returns id
        every { Bukkit.getOnlinePlayers() } returns listOf(player)
        every { Bukkit.getPlayer(id) } returns player
        val repository = mockk<ChatSettingsRepository>()
        var preferences = ChatVisibilitySettings(id)
        every { repository.getVisibilitySettings(id) } answers { preferences }
        val chat = mockk<RoseChatAdapter>()
        every { chat.getCurrentChannel(player) } returns null
        every { chat.getDefaultChannel() } returns null
        val lang = mockk<LangService>(relaxed = true)
        every { lang.raw("community.chat.unknown") } returns "Unavailable"
        every { lang.msg(any(), *anyVararg()) } returns Component.text("Destination")
        val indicator = ChatDestinationIndicator(repository, lang, chat)
        indicator.run()
        verify(exactly = 0) { player.showBossBar(any<BossBar>()) }
        preferences = preferences.copy(destinationIndicator = true)
        indicator.run(); indicator.run()
        verify(exactly = 1) { player.showBossBar(any<BossBar>()) }
        verify { lang.raw("community.chat.unknown") }
        verify(exactly = 0) { lang.raw("community.chat.global") }
        preferences = preferences.copy(destinationIndicator = false)
        indicator.run()
        verify(exactly = 1) { player.hideBossBar(any<BossBar>()) }
        preferences = preferences.copy(destinationIndicator = true)
        indicator.run(); indicator.onQuit(PlayerQuitEvent(player, Component.empty()))
        indicator.close()
        verify(exactly = 2) { player.hideBossBar(any<BossBar>()) }
    }
}
