package net.lumalyte.lg.infrastructure.listeners

import dev.rosewood.rosechat.chat.channel.Channel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildChatReconnectSettingsService
import net.lumalyte.lg.infrastructure.services.RoseChatAdapter
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/** Exercises the actual scheduled join callback and live RoseChat adapter boundary. */
internal class GuildChatReconnectListenerTest {
    private val plugin = mockk<Plugin>()
    private val settings = mockk<GuildChatReconnectSettingsService>()
    private val lang = mockk<LangService>(relaxed = true)
    private val chat = mockk<RoseChatAdapter>(relaxed = true)
    private val player = mockk<Player>(relaxed = true)
    private val scheduler = mockk<BukkitScheduler>()
    private val callback = slot<Runnable>()
    private val listener = GuildChatReconnectListener(plugin, settings, lang, chat)
    private val id = UUID.randomUUID()
    private val global = channel("server-default")

    /** Installs only a fake Bukkit scheduler/player lookup; production join code is exercised. */
    @BeforeEach
    fun setup() {
        mockkStatic(Bukkit::class)
        every { Bukkit.getScheduler() } returns scheduler
        every { scheduler.runTask(plugin, capture(callback)) } returns mockk()
        every { player.uniqueId } returns id
        every { player.isOnline } returns true
        every { Bukkit.getPlayer(id) } returns player
        every { settings.shouldReset(id) } returns true
        every { chat.getDefaultChannel() } returns global
    }

    /** Restores static Bukkit state between tests. */
    @AfterEach
    fun cleanup() = unmockkStatic(Bukkit::class)

    /** Both private channel IDs reset to the configured default after the join event finishes. */
    @Test
    fun privateChannelsReset() {
        for (channelId in listOf("guild", "guild-ally")) {
            every { chat.getCurrentChannel(player) } returns channel(channelId)
            listener.onPlayerJoin(PlayerJoinEvent(player, null as net.kyori.adventure.text.Component?))
            callback.captured.run()
        }
        verify(exactly = 2) { chat.switchChannel(player, global) }
    }

    /** Other channels retain their routing, including party and staff chat. */
    @Test
    fun otherChannelsStay() {
        for (channelId in listOf("global", "local", "guild-modchat", UUID.randomUUID().toString())) {
            every { chat.getCurrentChannel(player) } returns channel(channelId)
            listener.reset(player)
        }
        verify(exactly = 0) { chat.switchChannel(any(), any()) }
    }

    /** The callback reads the preference at execution time rather than capturing it at join. */
    @Test
    fun disabledPreference() {
        every { chat.getCurrentChannel(player) } returns channel("guild")
        listener.onPlayerJoin(PlayerJoinEvent(player, null as net.kyori.adventure.text.Component?))
        verify(exactly = 0) { settings.shouldReset(any()) }
        every { settings.shouldReset(id) } returns false
        callback.captured.run()
        verify(exactly = 0) { chat.switchChannel(any(), any()) }
    }

    /** A disconnected or replaced login cannot alter the new session's channel. */
    @Test
    fun staleSession() {
        listener.onPlayerJoin(PlayerJoinEvent(player, null as net.kyori.adventure.text.Component?))
        every { player.isOnline } returns false
        callback.captured.run()
        every { player.isOnline } returns true
        every { Bukkit.getPlayer(id) } returns mockk<Player>()
        callback.captured.run()
        verify(exactly = 0) { chat.getCurrentChannel(any()) }
    }

    /** Missing channel configuration does not switch or emit a success notification. */
    @Test
    fun missingDefault() {
        every { chat.getCurrentChannel(player) } returns channel("guild-ally")
        every { chat.getDefaultChannel() } returns null
        listener.reset(player)
        verify(exactly = 0) { chat.switchChannel(any(), any()) }
    }

    private fun channel(id: String): Channel = mockk { every { this@mockk.id } returns id }
}
