package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.values.QuestAction
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildMenuDialog
import net.lumalyte.lg.infrastructure.services.RecruitmentPresentation
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class GuildQuestDialogFlowTest {
    private val pages = mutableListOf<GuildDialogPage>()
    private val guards = mutableListOf<() -> Boolean>()
    private var member = true
    private val members = mockk<MemberService>()
    private val quests = mockk<QuestService>(relaxed = true)
    private val guilds = mockk<GuildService>(relaxed = true)
    private val navigator = mockk<MenuNavigator>(relaxed = true)
    private val lang = mockk<LangService>()
    private lateinit var menu: GuildQuestsMenu
    private lateinit var player: org.bukkit.entity.Player
    private lateinit var week: WeeklyQuestSet
    private lateinit var permission: org.bukkit.permissions.PermissionAttachment

    @BeforeEach
    fun setup() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any()) } returns plugin
        player = server.addPlayer()
        permission = player.addAttachment(plugin, "lumaguilds.guild.quests", true)
        val guild = Guild(UUID.randomUUID(), "Guild", createdAt = Instant.EPOCH)
        val target =
            mockk<QuestTarget>(relaxed = true) {
                every { id } returns "stone"
                every { value } returns "stone"
            }
        val definitions =
            (1..4).map {
                QuestDefinition(
                    "quest-$it",
                    action = QuestAction.MINE_BLOCKS,
                    target = target,
                    targetCount = 100,
                    tier = QuestRewardTier.COMMON,
                )
            }
        week = WeeklyQuestSet("week", Instant.EPOCH, Instant.EPOCH.plusSeconds(1000), definitions)
        every { quests.activeQuestSet() } answers { week }
        every { quests.guildProgress(any()) } returns emptyList()
        every { quests.timeRemaining(any()) } returns Duration.ofHours(1)
        every { members.getMember(player.uniqueId, guild.id) } answers { if (member) mockk() else null }
        every { navigator.isNavigationCurrent(any()) } returns true
        every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        mockkObject(RecruitmentPresentation)
        every { RecruitmentPresentation.supportsDialog(player) } returns true
        mockkConstructor(GuildMenuDialog::class)
        every { anyConstructed<GuildMenuDialog>().show(player, capture(pages), capture(guards), any()) } returns true
        menu = GuildQuestsMenu(navigator, player, guild, members, guilds, quests, lang)
    }

    @AfterEach
    fun cleanup() {
        unmockkAll()
        MockBukkit.unmock()
    }

    @Test
    fun cardsPaginationAndRankingChooserUseTheActualMenuFlow() {
        menu.open()
        assertEquals(4, pages.last().items.size)
        assertTrue(pages.last().items.drop(1).all { it.onClick != null })
        action("recruitment.next")()
        assertEquals(2, pages.last().items.size)
        action("guild_dialogs.quests.rankings")()
        verify(exactly = 1) { navigator.openMenu(match { it is GuildQuestLeaderboardMenu }) }
        assertEquals(2, pages.last().items.size)
        pages.last().items.last().onClick!!.action()
        verify(exactly = 1) { navigator.openMenu(match { it is GuildQuestDetailMenu }) }
    }

    @Test
    fun callbackGuardRejectsLostMembershipNavigationAndWeekChanges() {
        menu.open()
        val guard = guards.last()
        assertTrue(guard())
        member = false
        assertFalse(guard())
        member = true
        permission.setPermission("lumaguilds.guild.quests", false)
        assertFalse(guard())
        permission.setPermission("lumaguilds.guild.quests", true)
        every { navigator.isNavigationCurrent(any()) } returns false
        assertFalse(guard())
        every { navigator.isNavigationCurrent(any()) } returns true
        week = week.copy(weekId = "replacement-week")
        assertFalse(guard())
    }

    private fun action(key: String): () -> Unit =
        pages.last().actions.single {
            it.label == net.lumalyte.lg.infrastructure.i18n.GuiTextStyler.style(Component.text(key))
        }.action
}
