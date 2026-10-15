package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildDialogPresenter
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val BROWSE_TITLE = "Browse"

@Suppress("MagicNumber") // Small explicit page fixtures keep boundary assertions readable.
internal class RecruitmentDialogMenuTest {
    private val player = mockk<Player>(relaxed = true)
    private val lang = mockk<LangService>()
    private val guilds = mockk<GuildService>()
    private var renderedPage: GuildDialogPage? = null
    private val presenter =
        GuildDialogPresenter { _, page, _, _ ->
            renderedPage = page
            true
        }
    private val controller = RecruitmentController(player, {})
    private var exits = 0
    private var closes = 0

    @BeforeEach
    fun setup() {
        stopKoin()
        org.mockbukkit.mockbukkit.MockBukkit.mock()
        every { lang.raw(any()) } answers { firstArg() }
        every { guilds.getPlayerGuilds(any()) } returns emptySet()
        startKoin {
            modules(
                module {
                    single { lang }
                    single { guilds }
                },
            )
        }
    }

    @AfterEach
    fun cleanup() {
        stopKoin()
        unmockkAll()
        org.mockbukkit.mockbukkit.MockBukkit.unmock()
    }

    private fun menu(): RecruitmentDialogMenu {
        return RecruitmentDialogMenu(
            controller,
            RecruitmentDialogCallbacks({ true }, { exits++ }, { closes++ }),
            presenter,
        )
    }

    @Test
    fun boundedPagesKeepResults() {
        val entries = (1..8).map { RecruitmentButton("Guild $it", listOf("Mode", "Spaces", "Level", "Fee", "Pitch")) }
        controller.page = 2
        controller.screen = RecruitmentScreen.BROWSE
        assertTrue(menu().show(RecruitmentView(BROWSE_TITLE, entries, emptyList())))
        assertEquals(2, requireNotNull(renderedPage).items.size)
        val text = PlainTextComponentSerializer.plainText().serialize(requireNotNull(renderedPage).items.first().text)
        assertTrue(text.contains("Guild 7"))
        assertTrue(text.contains("Fee"))
        assertTrue(!text.contains("Pitch"))
    }

    @Test
    fun retainsCompletePaymentQuote() {
        var confirms = 0
        controller.screen = RecruitmentScreen.CONFIRM
        val details = listOf("Guild", "Mode", "Slots", "Level", "100 coins", "Confirm this quote")
        val view = RecruitmentView("Confirm", listOf(RecruitmentButton("Pay", details) { confirms++ }), emptyList())
        menu().show(view)
        val text = PlainTextComponentSerializer.plainText().serialize(requireNotNull(renderedPage).items.single().text)
        assertTrue(text.contains("100 coins"))
        assertTrue(text.contains("Confirm this quote"))
        assertTrue(!text.contains("Pay"))
        requireNotNull(renderedPage).actions.first().action()
        assertEquals(1, confirms)
    }

    @Test
    fun searchContextStaysVisible() {
        controller.screen = RecruitmentScreen.BROWSE
        controller.query = "Everlights"
        menu().show(RecruitmentView(BROWSE_TITLE, emptyList(), emptyList()))
        val context =
            PlainTextComponentSerializer.plainText().serialize(
                requireNotNull(renderedPage).items.single().text,
            )
        assertTrue(context.contains("Everlights"))
    }

    @Test
    fun backPreservesNavigation() {
        controller.screen = RecruitmentScreen.BROWSE
        menu().show(RecruitmentView(BROWSE_TITLE, emptyList(), emptyList()))
        requireNotNull(renderedPage).back.action()
        assertEquals(1, exits)
        assertEquals(0, closes)
    }

    @Test
    fun managementChoicesAppearOnce() {
        controller.screen = RecruitmentScreen.MANAGE
        val entries = (1..6).map { RecruitmentButton("Choice $it") { exits++ } }
        menu().show(RecruitmentView("Manage", entries, emptyList()))
        val page = requireNotNull(renderedPage)
        assertTrue(page.items.isEmpty())
        assertEquals(
            entries.map { it.label },
            page.actions.take(entries.size).map {
                PlainTextComponentSerializer.plainText().serialize(it.label)
            },
        )
        assertEquals(1, page.columns)
        page.actions[5].action()
        assertEquals(1, exits)
    }

    @Test
    fun guildStyleReachesDialog() {
        val guild = mockk<net.lumalyte.lg.domain.entities.Guild>()
        every { guild.guiTheme } returns net.lumalyte.lg.utils.GuiTheme.VERDANT
        every { guilds.getPlayerGuilds(any()) } returns setOf(guild)
        menu().show(RecruitmentView(BROWSE_TITLE, emptyList(), emptyList()))
        assertEquals(net.lumalyte.lg.utils.GuiTheme.VERDANT, requireNotNull(renderedPage).theme)
    }

    @Test
    fun boardSearchResetsPaginationAndPreservesOtherFilters() {
        controller.screen = RecruitmentScreen.BROWSE
        controller.interest = "building"
        controller.page = 2
        controller.browsePage = 2
        controller.beginnersOnly = true
        menu().show(RecruitmentView(BROWSE_TITLE, emptyList(), emptyList()))
        val search = requireNotNull(requireNotNull(renderedPage).search)
        assertEquals("building", search.category)
        assertTrue(search.accept("  Everlights  ", "quests"))
        assertEquals("Everlights", controller.query)
        assertEquals("quests", controller.interest)
        assertEquals(0, controller.page)
        assertEquals(0, controller.browsePage)
        assertTrue(controller.beginnersOnly)
    }

    @Test
    fun boardExposesDirectNavigationWithoutQuickFilterWall() {
        controller.screen = RecruitmentScreen.BROWSE
        val direct = listOf("recruitment.filter", "recruitment.mine", "recruitment.manage")
            .map { RecruitmentButton(it) { exits++ } }
        val quick = listOf(RecruitmentButton("Quick") { closes++ })
        menu().show(RecruitmentView(BROWSE_TITLE, emptyList(), direct, quick))
        val page = requireNotNull(renderedPage)
        val labels = page.actions.map { PlainTextComponentSerializer.plainText().serialize(it.label) }
        assertTrue(labels.containsAll(direct.map { it.label }))
        assertTrue("Quick" !in labels)
        assertEquals(1, page.columns)
        page.actions.first().action()
        assertEquals(1, exits)
    }

    @Test
    fun informationHasNoDeadButton() {
        controller.screen = RecruitmentScreen.PROFILE
        val entries = listOf(RecruitmentButton("Information", listOf("Full details")))
        menu().show(RecruitmentView("Profile", entries, emptyList()))
        val page = requireNotNull(renderedPage)
        assertEquals(1, page.items.size)
        assertTrue(
            page.actions.none {
                PlainTextComponentSerializer.plainText().serialize(it.label).contains("Information")
            },
        )
    }

    @Test
    fun boardRetainsBannerAndCompleteLongFeeAcrossPanelLines() {
        controller.screen = RecruitmentScreen.BROWSE
        val guild = net.lumalyte.lg.domain.entities.Guild(
            java.util.UUID.randomUUID(),
            "Everlights",
            createdAt = java.time.Instant.EPOCH,
        )
        val fee = "Joining fee: 123456789 a currency name that spans multiple panel lines"
        val entry = RecruitmentButton("Everlights", listOf("Apply", "Spaces: 6", "Level: 32", fee), guild = guild) {}
        menu().show(RecruitmentView(BROWSE_TITLE, listOf(entry), emptyList()))
        val item = requireNotNull(renderedPage).items.single()
        assertEquals(org.bukkit.Material.WHITE_BANNER, item.item.type)
        val panelText = requireNotNull(item.cardLines).drop(1).joinToString("") {
            PlainTextComponentSerializer.plainText().serialize(it)
        }
        assertTrue(panelText.endsWith(fee))
        assertTrue(PlainTextComponentSerializer.plainText().serialize(item.text).contains(fee))
    }
}
