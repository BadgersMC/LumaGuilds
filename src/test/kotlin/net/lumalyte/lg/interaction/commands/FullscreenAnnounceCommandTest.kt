package net.lumalyte.lg.interaction.commands

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID

internal class FullscreenAnnounceCommandTest {
    @AfterEach fun close() { stopKoin(); unmockkAll(); if (MockBukkit.isMocked()) MockBukkit.unmock() }

    @Test fun mutedAndRateLimitedAnnouncementsNeverShowTitles() {
        MockBukkit.mock()
        val player = mockk<Player>(relaxed = true)
        val id = UUID.randomUUID()
        val guild = Guild(UUID.randomUUID(), "Guild", createdAt = Instant.EPOCH)
        every { player.uniqueId } returns id
        val guilds = mockk<GuildService>()
        every { guilds.getPlayerGuilds(id) } returns setOf(guild)
        val members = mockk<MemberService>()
        every { members.hasPermission(id, guild.id, any()) } returns true
        val penalties = mockk<PenaltyService>()
        var muted = true
        every { penalties.isGuildMuted(guild.id) } answers { muted }
        val chat = mockk<ChatService>()
        var allowed = false
        every { chat.sendGuildAnnouncement(guild.id, id, "Hello") } answers { allowed }
        every { chat.getOnlineGuildMembers(guild.id) } returns setOf(id)
        val lang = mockk<LangService>(relaxed = true)
        every { lang.msg(any(), *anyVararg()) } returns Component.text("Announcement")
        startKoin { modules(module { single { guilds }; single { members }; single { penalties }; single { chat }; single { lang } }) }
        mockkStatic(Bukkit::class)
        every { Bukkit.getPlayer(id) } returns player
        val command = FullscreenAnnounceCommand()
        command.announce(player, "Hello")
        verify(exactly = 0) { chat.sendGuildAnnouncement(any(), any(), any()) }
        muted = false
        command.announce(player, "Hello")
        verify(exactly = 0) { player.showTitle(any<Title>()) }
        allowed = true
        command.announce(player, "Hello")
        verify(exactly = 1) { player.showTitle(any<Title>()) }
    }
}
