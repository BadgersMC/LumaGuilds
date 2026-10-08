package net.lumalyte.lg.infrastructure.listeners

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.api.events.GuildMemberJoinEvent
import net.lumalyte.lg.application.persistence.GuildOnboardingRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GuildOnboardingListenerTest {
    private val player = mockk<Player>(relaxed = true)
    private val plugin = mockk<Plugin>()
    private val repository = mockk<GuildOnboardingRepository>()
    private val guilds = mockk<GuildService>()
    private val members = mockk<MemberService>()
    private val lang = mockk<LangService>()
    private val playerId = UUID.randomUUID()
    private val guild = Guild(UUID.randomUUID(), "Guide", createdAt = Instant.now())
    private val listener = GuildOnboardingListener(plugin, repository, guilds, members, lang)

    @BeforeEach fun setup() {
        mockkStatic(Bukkit::class)
        every { Bukkit.getPlayer(playerId) } returns player
        every { player.isOnline } returns true
        every { player.hasPermission("lumaguilds.guild.help") } returns true
        every { guilds.getGuild(guild.id) } returns guild
        every { members.getMember(playerId, guild.id) } returns mockk()
        every { repository.register(playerId, guild.id) } returns true
        every { repository.consume(playerId, guild.id) } returns true
        every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        val scheduler = mockk<BukkitScheduler>()
        every { Bukkit.getScheduler() } returns scheduler
        every { scheduler.runTask(plugin, any<Runnable>()) } answers {
            secondArg<Runnable>().run(); mockk(relaxed = true)
        }
    }
    @AfterEach fun cleanup() = unmockkStatic(Bukkit::class)

    @Test fun `offline enrollment remains pending for later delivery`() {
        every { Bukkit.getPlayer(playerId) } returns null
        listener.onMemberJoin(GuildMemberJoinEvent(guild.id, playerId))
        verify { repository.register(playerId, guild.id) }
        verify(exactly = 0) { repository.consume(any(), any()) }
        every { Bukkit.getPlayer(playerId) } returns player
        listener.deliver(playerId, guild.id)
        verify(exactly = 1) { player.sendMessage(any<Component>()) }
    }
    @Test fun `lost membership does not consume pending prompt`() {
        every { members.getMember(playerId, guild.id) } returns null
        listener.deliver(playerId, guild.id)
        verify(exactly = 0) { repository.consume(any(), any()) }
        verify(exactly = 0) { player.sendMessage(any<Component>()) }
    }
    @Test fun `failed registration never schedules delivery`() {
        every { repository.register(playerId, guild.id) } returns false
        listener.onMemberJoin(GuildMemberJoinEvent(guild.id, playerId))
        verify(exactly = 0) { repository.consume(any(), any()) }
    }
    @Test fun `consumed or absent row cannot deliver duplicate prompt`() {
        every { repository.consume(playerId, guild.id) } returnsMany listOf(true, false)
        listener.deliver(playerId, guild.id)
        listener.deliver(playerId, guild.id)
        verify(exactly = 1) { player.sendMessage(any<Component>()) }
        verify(exactly = 0) { player.openInventory(any<org.bukkit.inventory.Inventory>()) }
    }
    @Test fun `missing help permission leaves pending prompt intact`() {
        every { player.hasPermission("lumaguilds.guild.help") } returns false
        listener.deliver(playerId, guild.id)
        verify(exactly = 0) { repository.consume(any(), any()) }
    }
}
