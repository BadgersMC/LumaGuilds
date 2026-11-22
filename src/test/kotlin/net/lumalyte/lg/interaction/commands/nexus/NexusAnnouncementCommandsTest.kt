package net.lumalyte.lg.interaction.commands.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NexusAnnouncementCommandsTest {
    private val lang = mockk<LangService>()
    private val chat = mockk<ChatService>()
    private val guilds = mockk<GuildService>()
    private val members = mockk<MemberService>()
    private val penalties = mockk<PenaltyService>()
    private val player = mockk<Player>(relaxed = true)
    private val playerId = UUID.randomUUID()
    private val guild = Guild(UUID.randomUUID(), "Test", createdAt = Instant.now())

    init {
        every { player.uniqueId } returns playerId
        every { lang.msg(any<String>()) } answers { Component.text(firstArg<String>()) }
    }

    @Test
    fun `ga and gfa each expose one optional greedy player-only default route`() {
        val defs = scanGuildCommandDefinitions()
        for ((clazz, name) in listOf(
            NexusQuickAnnounceCommand::class to "ga",
            NexusFullscreenAnnounceCommand::class to "gfa",
        )) {
            val def = assertNotNull(defs.singleOrNull { it.commandClass == clazz })
            assertEquals(name, def.annotation.name)
            assertEquals(1, def.subcommands.size)
            val route = def.subcommands.single()
            assertTrue(route.path.isEmpty())
            assertTrue(route.isPlayerOnly)
            assertEquals("lumaguilds.guild.chat", route.permission)
            val message = route.parameters.single { it.isArg }
            assertEquals("message", message.name)
            assertTrue(message.isOptional)
        }
    }

    @Test
    fun `ga without a message displays legacy usage after announcement permission gate`() {
        every { guilds.getPlayerGuilds(playerId) } returns setOf(guild)
        every { members.hasPermission(playerId, guild.id, RankPermission.SEND_ANNOUNCEMENTS) } returns true
        NexusQuickAnnounceCommand(lang, chat, guilds, members).announce(player, null)
        verifyOrder {
            player.sendMessage(Component.text("command.migrated.quick_announce.default.guild_announcements"))
            player.sendMessage(Component.text("command.migrated.quick_announce.default.use_ga_message_to_announce_to_all"))
            player.sendMessage(Component.text("command.migrated.quick_announce.default.add_a_color_code_ga_4_message"))
            player.sendMessage(Component.text("command.migrated.quick_announce.default.colors_0_1_2_3_4_5"))
            player.sendMessage(Component.text("command.migrated.quick_announce.default.default_is_6_gold_cooldown_5_minutes"))
        }
        verify(exactly = 0) { chat.sendGuildAnnouncement(any(), any(), any(), any()) }
    }

    @Test
    fun `ga preserves color override and sends the remaining announcement text`() {
        every { guilds.getPlayerGuilds(playerId) } returns setOf(guild)
        every { members.hasPermission(playerId, guild.id, RankPermission.SEND_ANNOUNCEMENTS) } returns true
        every { chat.sendGuildAnnouncement(guild.id, playerId, "Alert everyone", '4') } returns true
        NexusQuickAnnounceCommand(lang, chat, guilds, members).announce(player, "&4 Alert everyone")
        verify(exactly = 1) { chat.sendGuildAnnouncement(guild.id, playerId, "Alert everyone", '4') }
    }

    @Test
    fun `gfa without a guild shows fullscreen usage`() {
        every { guilds.getPlayerGuilds(playerId) } returns emptySet()
        NexusFullscreenAnnounceCommand(guilds, members, chat, penalties, lang).announce(player, null)
        verify(exactly = 1) { player.sendMessage(Component.text("community.chat.fullscreen_usage")) }
        verify(exactly = 0) { chat.getOnlineGuildMembers(any()) }
    }

    @Test
    fun `gfa preserves mute gate before the announcement is sent`() {
        every { guilds.getPlayerGuilds(playerId) } returns setOf(guild)
        every { members.hasPermission(playerId, guild.id, RankPermission.SEND_ANNOUNCEMENTS) } returns true
        every { penalties.isGuildMuted(guild.id) } returns true
        NexusFullscreenAnnounceCommand(guilds, members, chat, penalties, lang).announce(player, "Hello")
        verify(exactly = 1) { player.sendMessage(Component.text("notification.guild_chat.guild_muted")) }
        verify(exactly = 0) { chat.getOnlineGuildMembers(any()) }
    }
}
