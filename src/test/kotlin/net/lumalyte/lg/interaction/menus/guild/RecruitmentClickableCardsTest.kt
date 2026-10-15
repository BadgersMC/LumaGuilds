package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.domain.entities.Guild
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class RecruitmentClickableCardsTest : RecruitmentDialogFixture() {
    private val guild = Guild(UUID.randomUUID(), "Everlights", createdAt = Instant.EPOCH)

    @Test
    fun browseGuildGetsCardCallbackWithoutDuplicateOpenButton() {
        var opens = 0
        val button = RecruitmentButton("Everlights", listOf("Apply in game", "100 coins"), guild = guild) { opens++ }
        menu().show(RecruitmentView("Browse", listOf(button), emptyList()))
        val page = assertNotNull(renderedPage)
        val card = page.items.last()
        val click = assertNotNull(card.onClick)
        assertTrue(PlainTextComponentSerializer.plainText().serialize(click.tooltip).contains("100 coins"))
        assertTrue(page.actions.none { it.label == click.label })
        click.action()
        assertEquals(1, opens)
    }

    @Test
    fun disabledCardsRestoreWorkingOpenAction() {
        val button = RecruitmentButton("Everlights", listOf("Apply on Discord"), guild = guild) { exits++ }
        RecruitmentDialogMenu(controller, RecruitmentDialogCallbacks({ true }, {}, {}), presenter, false)
            .show(RecruitmentView("Browse", listOf(button), emptyList()))
        val page = assertNotNull(renderedPage)
        assertNull(page.items.last().onClick)
        page.actions.first().action()
        assertEquals(1, exits)
    }

    @Test
    fun informationOnlyGuildAndProfileItemsDoNotBecomeClickable() {
        menu().show(
            RecruitmentView("Browse", listOf(RecruitmentButton("Info", listOf("Details"), guild = guild)), emptyList()),
        )
        assertNull(assertNotNull(renderedPage).items.last().onClick)
        controller.screen = RecruitmentScreen.PROFILE
        menu().show(
            RecruitmentView("Profile", listOf(RecruitmentButton("Join", listOf("Fee"), guild = guild) {}), emptyList()),
        )
        assertNull(assertNotNull(renderedPage).items.single().onClick)
        assertEquals(2, assertNotNull(renderedPage).actions.size)
    }
}
