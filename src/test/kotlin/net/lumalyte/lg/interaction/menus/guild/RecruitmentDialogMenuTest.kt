package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val BROWSE_TITLE = "Browse"

@Suppress("MagicNumber") // Small explicit page fixtures keep boundary assertions readable.
internal class RecruitmentDialogMenuTest : RecruitmentDialogFixture() {
    @Test
    fun boundedPagesKeepResults() {
        val entries = (1..8).map { RecruitmentButton("Guild $it", listOf("Mode", "Spaces", "Level", "Fee", "Pitch")) }
        controller.page = 1
        controller.screen = RecruitmentScreen.BROWSE
        assertTrue(menu().show(RecruitmentView(BROWSE_TITLE, entries, emptyList())))
        assertEquals(3, requireNotNull(renderedPage).items.size)
        val text = PlainTextComponentSerializer.plainText().serialize(requireNotNull(renderedPage).items.first().text)
        assertTrue(text.contains("Guild 4"))
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
        val context = requireNotNull(requireNotNull(renderedPage).search).initial
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

    @Test fun `management preview Back restores management page instead of browsing`() {
        controller.screen = RecruitmentScreen.MANAGE
        controller.page = 1
        controller.showScreen(RecruitmentScreen.PROFILE)
        menu().show(RecruitmentView("Profile", emptyList(), emptyList()))
        renderedPage!!.back.action()
        assertEquals(RecruitmentScreen.MANAGE, controller.screen)
        assertEquals(1, controller.page)
    }

    @Test fun `repeated non-root screens unwind visits instead of creating a Back cycle`() {
        controller.rootScreen = RecruitmentScreen.BROWSE
        controller.screen = RecruitmentScreen.MANAGE
        controller.page = 1
        controller.showScreen(RecruitmentScreen.PROFILE)
        controller.showScreen(RecruitmentScreen.MANAGE)
        controller.showScreen(RecruitmentScreen.PROFILE)
        repeat(3) {
            controller.returnToPrevious(RecruitmentScreen.BROWSE)
        }
        assertEquals(RecruitmentScreen.MANAGE, controller.screen)
        assertEquals(1, controller.page)
        controller.returnToPrevious(RecruitmentScreen.BROWSE)
        assertEquals(RecruitmentScreen.BROWSE, controller.screen)
    }

    @Test fun `review Back restores inbox page and browse filters survive profile visit`() {
        controller.screen = RecruitmentScreen.INBOX
        controller.page = 2
        controller.showScreen(RecruitmentScreen.REVIEW)
        menu().show(RecruitmentView("Review", emptyList(), emptyList()))
        renderedPage!!.back.action()
        assertEquals(RecruitmentScreen.INBOX, controller.screen)
        assertEquals(2, controller.page)
        controller.screen = RecruitmentScreen.BROWSE
        controller.page = 3
        controller.query = "Ever"
        controller.feeFilter = 1
        controller.showScreen(RecruitmentScreen.PROFILE)
        menu().show(RecruitmentView("Profile", emptyList(), emptyList()))
        renderedPage!!.back.action()
        assertEquals(3, controller.page)
        assertEquals("Ever", controller.query)
        assertEquals(1, controller.feeFilter)
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
        assertEquals(2, page.columns)
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
    fun listingTimeDropsFractionalSecondsAndUsesUtc() {
        assertEquals(
            "2026-10-14 23:30",
            recruitmentListingTime(java.time.Instant.parse("2026-10-14T23:30:16.198408669Z")),
        )
    }

    @Test
    fun nativeTitleIsNotRepeatedInTheBody() {
        val skin =
            net.lumalyte.lg.infrastructure.services.GuildDialogSkin(
                emptyMap(),
                net.kyori.adventure.text.format.NamedTextColor.GOLD,
            )
        val content = listOf(mockk<io.papermc.paper.registry.data.dialog.body.DialogBody>())
        assertEquals(
            content,
            skin.referenceBody(
                net.kyori.adventure.text.Component.text("Enthusiast"),
                net.kyori.adventure.text.Component.empty(),
                content,
            ),
        )
    }

    @Test
    fun profileDoesNotRepeatTheGenericReviewInstruction() {
        controller.screen = RecruitmentScreen.PROFILE
        menu().show(RecruitmentView("Profile", emptyList(), emptyList()))
        assertEquals("", PlainTextComponentSerializer.plainText().serialize(requireNotNull(renderedPage).subtitle))
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
}
