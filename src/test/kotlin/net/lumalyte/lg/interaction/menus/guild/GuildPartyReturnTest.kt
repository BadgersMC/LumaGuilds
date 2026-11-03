package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Material
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class GuildPartyReturnTest : GuildParentReturnFixture() {
    private val parties = mockk<PartyService>(relaxed = true)
    private val members: MemberService get() = GlobalContext.get().get()
    private val guilds: GuildService get() = GlobalContext.get().get()

    @Test fun `creation cancellation unwinds to party management without returning to the form`() {
        val navigator = MenuNavigator(player)
        val visits = mutableListOf<String>()
        val management = management(navigator, visits)
        val creation = traced(creation(navigator), "creation", visits)
        navigator.openMenu(dashboard(visits))
        navigator.openMenu(management)
        navigator.openMenu(creation)
        val buttons = paneFor(creation, "addActionButtons")
        repeat(2) { click(buttons, 7, 5) }
        navigator.goBack()
        assertEquals("dashboard", visits.last())
        assertEquals(1, visits.count { it == "creation" })
        verify(exactly = 0) { parties.createParty(any()) }
    }

    @Test fun `successful creation keeps its draft values and returns through the original parent`() {
        every { parties.createParty(any()) } answers { firstArg() }
        val navigator = MenuNavigator(player)
        val visits = mutableListOf<String>()
        val management = management(navigator, visits)
        val creation = traced(creation(navigator), "creation", visits)
        val role = UUID.randomUUID()
        setField(creation, "partyName", "RaidNight")
        setField(creation, "restrictedRoles", mutableSetOf(role))
        setField(creation, "isPrivateParty", true)
        navigator.openMenu(dashboard(visits))
        navigator.openMenu(management)
        navigator.openMenu(creation)
        click(paneFor(creation, "addActionButtons"), 1, 5)
        assertEquals("management", visits.last())
        navigator.goBack()
        assertEquals("dashboard", visits.last())
        verify(exactly = 1) {
            parties.createParty(match {
                it.name == "RaidNight" && it.restrictedRoles == setOf(role) && it.guildIds == setOf(guild.id)
            })
        }
        verify(exactly = 0) { parties.inviteToParty(any(), any(), any(), any()) }
    }

    @Test fun `guild selection returns invited guilds without discarding the name and role drafts`() {
        val navigator = MenuNavigator(player)
        val visits = mutableListOf<String>()
        val management = management(navigator, visits)
        val creation = traced(creation(navigator), "creation", visits)
        val role = UUID.randomUUID()
        val invited = UUID.randomUUID()
        setField(creation, "partyName", "RaidNight")
        setField(creation, "restrictedRoles", mutableSetOf(role))
        val selection = traced(
            GuildSelectionMenu(navigator, player, guild, mutableSetOf(guild.id, invited)), "selection", visits,
        )
        navigator.openMenu(dashboard(visits))
        navigator.openMenu(management)
        navigator.openMenu(creation)
        navigator.openMenu(selection)
        click(paneFor(selection, "addBackButton", 4, 5), 4, 5)
        assertEquals("creation", visits.last())
        assertEquals("RaidNight", field(creation, "partyName"))
        assertEquals(setOf(role), field(creation, "restrictedRoles"))
        assertEquals(setOf(guild.id, invited), field(creation, "selectedGuilds"))
        click(paneFor(creation, "addActionButtons"), 7, 5)
        navigator.goBack()
        assertEquals("dashboard", visits.last())
        assertEquals(1, visits.count { it == "selection" })
        verify(exactly = 0) { factory.createPartyCreationMenu(navigator, player, guild) }
    }

    @Test fun `player moderation returns updated party state to the same browsed page`() {
        val server = requireNotNull(MockBukkit.getMock())
        val participants = (1..37).map { server.addPlayer("Member$it") }
        every { members.getGuildMembers(guild.id) } returns
            participants.map { Member(it.uniqueId, guild.id, rankId, Instant.EPOCH) }.toSet() + member
        val party = Party(UUID.randomUUID(), "RaidNight", setOf(guild.id), player.uniqueId, createdAt = Instant.EPOCH)
        val updated = party.copy(mutedPlayers = mapOf(participants.last().uniqueId to null))
        val navigator = MenuNavigator(player)
        val visits = mutableListOf<String>()
        val management = management(navigator, visits)
        val moderation = traced(PartyModerationMenu(navigator, player, guild, party), "moderation", visits)
        navigator.openMenu(dashboard(visits))
        navigator.openMenu(management)
        navigator.openMenu(moderation)
        click(paneFor(moderation, "addNavigationButtons"), 8, 5)
        assertEquals(1, field(moderation, "currentPage"))
        val detail = traced(
            PlayerModerationMenu(navigator, player, guild, updated, participants.last().uniqueId), "detail", visits,
        )
        navigator.openMenu(detail)
        click(paneFor(detail, "addBackButton"), 4, 2)
        assertEquals("moderation", visits.last())
        assertEquals(1, field(moderation, "currentPage"))
        assertEquals(updated, field(moderation, "party"))
        click(paneFor(moderation, "addNavigationButtons"), 4, 5)
        navigator.goBack()
        assertEquals("dashboard", visits.last())
        assertEquals(1, visits.count { it == "detail" })
    }

    @Test fun `guild selection paging redraws visible entries and clamps when the list becomes empty`() {
        var available = (1..47).map {
            Guild(UUID.randomUUID(), "Guild$it", createdAt = Instant.EPOCH)
        }.toSet()
        every { guilds.getAllGuilds() } answers { available }
        every { members.getGuildMembers(any()) } returns emptySet()
        val selection = GuildSelectionMenu(MenuNavigator(player), player, guild, mutableSetOf(guild.id))
        selection.open()
        assertEquals(45, visibleGuilds())
        click(paneFor(selection, "addNavigationButtons"), 8, 5)
        assertEquals(1, field(selection, "currentPage"))
        assertEquals(2, visibleGuilds())
        assertTrue(pageLabel().contains("2/2"))
        available = emptySet()
        selection.open()
        assertEquals(0, field(selection, "currentPage"))
        assertEquals(0, visibleGuilds())
        assertTrue(pageLabel().contains("1/1"))
    }

    @Test fun `revoked moderation permission skips the denied parent and management Back reaches dashboard`() {
        var allowed = true
        every { members.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS) } answers { allowed }
        val navigator = MenuNavigator(player)
        val visits = mutableListOf<String>()
        val party = Party(UUID.randomUUID(), "RaidNight", setOf(guild.id), player.uniqueId, createdAt = Instant.EPOCH)
        val management = traced(GuildPartyManagementMenu(navigator, player, guild), "management", visits)
        val moderation = traced(PartyModerationMenu(navigator, player, guild, party), "moderation", visits)
        val detail = traced(
            PlayerModerationMenu(navigator, player, guild, party, UUID.randomUUID()), "detail", visits,
        )
        navigator.openMenu(dashboard(visits))
        navigator.openMenu(management)
        navigator.openMenu(moderation)
        navigator.openMenu(detail)
        allowed = false
        every { moderation.open() } answers { callOriginal() }

        click(paneFor(detail, "addBackButton"), 4, 2)

        assertEquals("management", visits.last())
        click(paneFor(management, "addBackButton", 4, 5), 4, 5)
        assertEquals("dashboard", visits.last())
        assertEquals(1, visits.count { it == "detail" })
        verify(exactly = 2) { moderation.open() }
        verify(exactly = 0) { factory.createGuildPartyManagementMenu(navigator, player, guild) }
    }

    @Test fun `denied direct moderation entries replace themselves with one management fallback`() {
        every { members.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS) } returns false
        val party = Party(UUID.randomUUID(), "RaidNight", setOf(guild.id), player.uniqueId, createdAt = Instant.EPOCH)
        val constructors: List<(MenuNavigator) -> Menu> = listOf(
            { PartyModerationMenu(it, player, guild, party) },
            { PlayerModerationMenu(it, player, guild, party, UUID.randomUUID()) },
        )
        constructors.forEach { create ->
            val navigator = MenuNavigator(player)
            val visits = mutableListOf<String>()
            val management = traced(GuildPartyManagementMenu(navigator, player, guild), "management", visits)
            every { factory.createGuildPartyManagementMenu(navigator, player, guild) } returns management
            val denied = spyk(create(navigator))
            navigator.openMenu(denied)
            assertEquals(listOf("management"), visits, denied.javaClass.simpleName)
            click(paneFor(management, "addBackButton", 4, 5), 4, 5)
            navigator.goBack()
            verify(exactly = 1) { denied.open() }
            verify(exactly = 1) { management.open() }
            verify(exactly = 1) { factory.createGuildPartyManagementMenu(navigator, player, guild) }
        }
    }

    private fun visibleGuilds(): Int =
        player.openInventory.topInventory.contents.count { it?.type == Material.WHITE_BANNER }

    private fun pageLabel(): String = PlainTextComponentSerializer.plainText().serialize(
        requireNotNull(player.openInventory.topInventory.getItem(47)).itemMeta.itemName(),
    )

    private fun creation(navigator: MenuNavigator): PartyCreationMenu = PartyCreationMenu(
        navigator, player, guild, guilds, parties, ranks, members, mockk<ChatInputListener>(relaxed = true),
        GlobalContext.get().get<ConfigService>(), factory, GlobalContext.get().get<LangService>(),
    )

    private fun management(navigator: MenuNavigator, visits: MutableList<String>): GuildPartyManagementMenu {
        val management = mockk<GuildPartyManagementMenu>(relaxed = true)
        every { management.open() } answers { visits += "management"; Unit }
        every { factory.createGuildPartyManagementMenu(navigator, player, guild) } returns management
        return management
    }
}
