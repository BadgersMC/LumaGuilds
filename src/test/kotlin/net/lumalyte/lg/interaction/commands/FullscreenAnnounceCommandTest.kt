package net.lumalyte.lg.interaction.commands

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.domain.entities.Guild
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
    @AfterEach fun close() {
        stopKoin()
        unmockkAll()
        if (MockBukkit.isMocked()) MockBukkit.unmock()
    }

    @Test fun announcementLimits() {
        val fixture = Fixture()
        val command = FullscreenAnnounceCommand()
        command.announce(fixture.player, "Hello")
        verify(exactly = 0) { fixture.chat.sendGuildAnnouncement(any(), any(), any()) }
        fixture.muted = false
        command.announce(fixture.player, "Hello")
        verify(exactly = 0) { fixture.player.showTitle(any<Title>()) }
        fixture.allowed = true
        command.announce(fixture.player, "Hello")
        verify(exactly = 1) { fixture.player.showTitle(any<Title>()) }
    }

    private class Fixture {
        val player = mockk<Player>(relaxed = true)
        val id = UUID.randomUUID()
        val guild = Guild(UUID.randomUUID(), "Guild", createdAt = Instant.EPOCH)
        val guilds = mockk<GuildService>()
        val members = mockk<MemberService>()
        val penalties = mockk<PenaltyService>()
        val chat = mockk<ChatService>()
        val lang = mockk<LangService>(relaxed = true)
        var muted = true
        var allowed = false

        init {
            MockBukkit.mock()
            every { player.uniqueId } returns id
            every { guilds.getPlayerGuilds(id) } returns setOf(guild)
            every { members.hasPermission(id, guild.id, any()) } returns true
            every { penalties.isGuildMuted(guild.id) } answers { muted }
            every { chat.sendGuildAnnouncement(guild.id, id, "Hello") } answers { allowed }
            every { chat.getOnlineGuildMembers(guild.id) } returns setOf(id)
            every { lang.msg(any()) } returns Component.text("Announcement")
            configureServices()
            mockkStatic(Bukkit::class)
            every { Bukkit.getPlayer(id) } returns player
        }

        private fun configureServices() {
            startKoin {
                modules(module {
                    single { guilds }
                    single { members }
                    single { penalties }
                    single { chat }
                    single { lang }
                })
            }
        }
    }
}
