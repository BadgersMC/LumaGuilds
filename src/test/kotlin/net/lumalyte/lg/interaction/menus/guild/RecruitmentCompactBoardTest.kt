package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class RecruitmentCompactBoardTest : RecruitmentDialogFixture() {
    @Test
    fun boardMovesSearchToolsIntoFilterLinkAndKeepsCompactFooter() {
        val navigation =
            listOf(
                RecruitmentButton("recruitment.manage") {},
                RecruitmentButton("recruitment.filter") {},
            )
        menu().show(RecruitmentView("Browse", emptyList(), navigation))
        val page = assertNotNull(renderedPage)
        assertNull(page.search)
        assertEquals(2, page.columns)
        assertEquals(
            listOf("recruitment.mine", "recruitment.manage"),
            page.actions.map {
                PlainTextComponentSerializer.plainText().serialize(it.label)
            },
        )
        assertNotNull(page.items.first().onClick).action()
        assertEquals(RecruitmentScreen.FILTER, controller.screen)
    }

    @Test
    fun filterDialogOwnsSearchSortAndResetControls() {
        controller.screen = RecruitmentScreen.FILTER
        menu().show(RecruitmentView("Filters", emptyList(), emptyList()))
        val page = assertNotNull(renderedPage)
        assertNotNull(page.search)
        val labels = page.actions.map { PlainTextComponentSerializer.plainText().serialize(it.label) }
        assertTrue(labels.any { it.startsWith("recruitment.sort") })
        assertTrue(labels.any { it.startsWith("recruitment.reset") })
        assertTrue(labels.none { it == "recruitment.manage" })
    }
}
