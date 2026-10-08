package net.lumalyte.lg.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import net.lumalyte.lg.infrastructure.services.RoseChatAdapter
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID

internal class ChatDestinationIndicatorTest {
    @AfterEach fun close() {
        unmockkAll()
        if (MockBukkit.isMocked()) MockBukkit.unmock()
    }

    @Test fun destinationLifecycle() {
        val fixture = Fixture()
        fixture.indicator.run()
        verify(exactly = 0) { fixture.player.showBossBar(any<BossBar>()) }
        fixture.enable(true)
        fixture.indicator.run()
        fixture.indicator.run()
        verify(exactly = 1) { fixture.player.showBossBar(any<BossBar>()) }
        verify { fixture.lang.raw("community.chat.unknown") }
        verify(exactly = 0) { fixture.lang.raw("community.chat.global") }
        fixture.enable(false)
        fixture.indicator.run()
        verify(exactly = 1) { fixture.player.hideBossBar(any<BossBar>()) }
        fixture.enable(true)
        fixture.indicator.run()
        fixture.indicator.onQuit(PlayerQuitEvent(fixture.player, Component.empty()))
        fixture.indicator.close()
        verify(exactly = 2) { fixture.player.hideBossBar(any<BossBar>()) }
    }

    private class Fixture {
        val player = mockk<Player>(relaxed = true)
        val id = UUID.randomUUID()
        val repository = mockk<ChatSettingsRepository>()
        val chat = mockk<RoseChatAdapter>()
        val lang = mockk<LangService>(relaxed = true)
        private var preferences = ChatVisibilitySettings(id)
        val indicator = ChatDestinationIndicator(repository, lang, chat)

        init {
            MockBukkit.mock()
            mockkStatic(Bukkit::class)
            every { player.uniqueId } returns id
            every { Bukkit.getOnlinePlayers() } returns listOf(player)
            every { Bukkit.getPlayer(id) } returns player
            every { repository.getVisibilitySettings(id) } answers { preferences }
            every { chat.getCurrentChannel(player) } returns null
            every { chat.getDefaultChannel() } returns null
            every { lang.raw("community.chat.unknown") } returns "Unavailable"
            every { lang.msg("community.chat.destination", any()) } returns Component.text("Destination")
        }

        fun enable(enabled: Boolean) {
            preferences = preferences.copy(destinationIndicator = enabled)
        }
    }
}
