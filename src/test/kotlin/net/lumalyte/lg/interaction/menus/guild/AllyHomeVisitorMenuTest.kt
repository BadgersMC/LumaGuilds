package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.AllyHomeAccess
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID

/** REQ-182: the host picks an ally's ranks and blocks its players from one page. */
class AllyHomeVisitorMenuTest {
    @BeforeEach fun setup() = stopKoin()

    @AfterEach fun cleanup() {
        stopKoin()
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    private val server = MockBukkit.mock()
    private val player = server.addPlayer()
    private val visitor = server.addPlayer()
    private val host = Guild(UUID.randomUUID(), "Host", createdAt = Instant.now())
    private val allyId = UUID.randomUUID()
    private val officer = Rank(UUID.randomUUID(), allyId, "Officer", 1, emptySet())
    private val recruit = Rank(UUID.randomUUID(), allyId, "Recruit", 5, emptySet())
    private val hostOwner = Rank(UUID.randomUUID(), host.id, "Leader", 0, emptySet())
    private val hostOfficer = Rank(UUID.randomUUID(), host.id, "HostOfficer", 1, setOf(RankPermission.USE_ALLY_HOMES))
    private val hostRecruit = Rank(UUID.randomUUID(), host.id, "HostRecruit", 5, emptySet())
    private val guilds = mockk<GuildService>(relaxed = true)
    private var access = AllyHomeAccess()

    private lateinit var pane: net.lumalyte.lg.utils.inventoryframework.StaticPane

    private fun open() {
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        val lang = mockk<LangService>(relaxed = true)
        every { lang.msg(any()) } returns Component.text("msg")
        every { lang.msg(any(), any()) } returns Component.text("msg")
        val ranks = mockk<RankService>()
        every { ranks.hasPermission(player.uniqueId, host.id, RankPermission.MANAGE_HOME) } returns true
        every { ranks.listRanks(allyId) } returns setOf(recruit, officer)
        every { ranks.listRanks(host.id) } returns setOf(hostOwner, hostOfficer, hostRecruit)
        every { ranks.getHighestRank(host.id) } returns hostOwner
        val members = mockk<MemberService>()
        every { members.getGuildMembers(allyId) } returns setOf(Member(visitor.uniqueId, allyId, recruit.id, Instant.now()))
        every { guilds.allyHomeAccess(host.id) } answers { access }
        every { guilds.setAllyHomeVisitorRanks(any(), any(), any(), any()) } returns true
        every { guilds.setAllyHomeBlocked(any(), any(), any(), any()) } returns true
        every { guilds.setAllyHomeOutboundRanks(any(), any(), any(), any()) } returns true
        every { guilds.allyHomeOutboundRanks(host.id) } returns emptyMap()
        startKoin { modules(module { single { guilds }; single { ranks }; single { members }; single { lang } }) }
        rebuild()
    }

    private fun rebuild() {
        pane = AllyHomeVisitorMenu(mockk<MenuNavigator>(relaxed = true), player, host, allyId, "Ally").buildPane()
    }

    private fun click(slot: Int) {
        requireNotNull(pane.getItem(slot % 9, slot / 9)).callAction(mockk(relaxed = true))
        rebuild() // the menu re-reads the rules after each change
    }

    @Test
    fun `turning a rank off stores every other rank`() {
        open()
        click(9) // row 1, first rank by priority: Officer
        verify { guilds.setAllyHomeVisitorRanks(host.id, allyId, setOf(recruit.id), player.uniqueId) }
    }

    @Test
    fun `allow every rank clears the pick`() {
        access = AllyHomeAccess(visitorRanks = mapOf(allyId to setOf(officer.id)))
        open()
        click(8)
        verify { guilds.setAllyHomeVisitorRanks(host.id, allyId, null, player.uniqueId) }
    }

    @Test
    fun `clicking a member's head blocks them, and again unblocks`() {
        open()
        click(36)
        verify { guilds.setAllyHomeBlocked(host.id, visitor.uniqueId, true, player.uniqueId) }
        access = AllyHomeAccess(blockedPlayers = setOf(visitor.uniqueId))
        rebuild()
        click(36)
        verify { guilds.setAllyHomeBlocked(host.id, visitor.uniqueId, false, player.uniqueId) }
    }

    // REQ-183: row 3 holds the host's own ranks for visiting this ally.
    @Test
    fun `turning one of our ranks off stores the ranks that keep access`() {
        open()
        click(27) // our first rank by priority: HostOfficer (has Use ally homes)
        verify { guilds.setAllyHomeOutboundRanks(host.id, allyId, emptySet(), player.uniqueId) }
    }

    @Test
    fun `use rank permissions clears our pick`() {
        open()
        click(26)
        verify { guilds.setAllyHomeOutboundRanks(host.id, allyId, null, player.uniqueId) }
    }
}
