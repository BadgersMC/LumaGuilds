package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
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
    private var renderedPage: GuildDialogPage? = null
    private val rendered get() = requireNotNull(renderedPage)
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
        startKoin { modules(module { single { lang } }) }
    }

    @AfterEach
    fun cleanup() {
        stopKoin()
        unmockkAll()
        org.mockbukkit.mockbukkit.MockBukkit.unmock()
    }

    private fun menu() =
        RecruitmentDialogMenu(
            controller,
            RecruitmentDialogCallbacks({ true }, { exits++ }, { closes++ }),
            presenter,
        )

    @Test
    fun boundedPagesKeepResults() {
        val entries = (1..8).map { RecruitmentButton("Guild $it", listOf("Mode", "Spaces", "Level", "Fee", "Pitch")) }
        controller.page = 2
        controller.screen = RecruitmentScreen.BROWSE
        assertTrue(menu().show(RecruitmentView(BROWSE_TITLE, entries, emptyList())))
        assertEquals(2, rendered.items.size)
        val text = PlainTextComponentSerializer.plainText().serialize(rendered.items.first().text)
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
        val text = PlainTextComponentSerializer.plainText().serialize(rendered.items.single().text)
        assertTrue(text.contains("100 coins"))
        assertTrue(text.contains("Confirm this quote"))
        rendered.actions.first().action()
        assertEquals(1, confirms)
    }

    @Test
    fun searchContextStaysVisible() {
        controller.screen = RecruitmentScreen.BROWSE
        controller.query = "Everlights"
        menu().show(RecruitmentView(BROWSE_TITLE, emptyList(), emptyList()))
        val context = PlainTextComponentSerializer.plainText().serialize(rendered.items.single().text)
        assertTrue(context.contains("Everlights"))
    }

    @Test
    fun backPreservesNavigation() {
        controller.screen = RecruitmentScreen.BROWSE
        menu().show(RecruitmentView(BROWSE_TITLE, emptyList(), emptyList()))
        rendered.back.action()
        assertEquals(1, exits)
        assertEquals(0, closes)
    }
}
