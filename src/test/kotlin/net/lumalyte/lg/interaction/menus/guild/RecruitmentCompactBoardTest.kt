package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class RecruitmentCompactBoardTest : RecruitmentDialogFixture() {
    @Test
    fun referenceBoardKeepsSearchCategoryLinksAndTwoColumnFooter() {
        val navigation =
            listOf(
                RecruitmentButton("recruitment.manage") {},
                RecruitmentButton("recruitment.filter", listOf("Any")) { controller.showScreen(RecruitmentScreen.FILTER) },
            )
        menu().show(RecruitmentView("Browse", emptyList(), navigation))
        val page = assertNotNull(renderedPage)
        assertNotNull(page.search)
        assertEquals(5, page.bodyActions.size)
        assertEquals(false, page.showCategoryInput)
        assertEquals(2, page.columns)
        assertEquals(
            listOf("dialog_v2.filters", "recruitment.mine", "recruitment.manage"),
            page.actions.map {
                PlainTextComponentSerializer.plainText().serialize(it.label)
            },
        )
        assertEquals("Any", PlainTextComponentSerializer.plainText().serialize(page.actions.first().tooltip))
        assertFalse(page.actions.first().showTooltip)
        page.actions.first().action()
        assertEquals(RecruitmentScreen.FILTER, controller.screen)
    }

    @Test
    fun filterDialogHasSegmentedChoicesAndExplicitResultsReset() {
        controller.screen = RecruitmentScreen.FILTER
        menu().show(RecruitmentView("Filters", emptyList(), emptyList()))
        val page = assertNotNull(renderedPage)
        assertNull(page.search)
        assertEquals(3, page.bodyActionGroupSize)
        assertTrue(PlainTextComponentSerializer.plainText().serialize(page.title).startsWith("dialog_v2.filters"))
        val labels = page.actions.map { PlainTextComponentSerializer.plainText().serialize(it.label) }
        assertTrue(page.bodyActions.size >= 9)
        page.bodyActions[2].action()
        assertEquals(2, controller.feeFilter)
        page.bodyActions[7].action()
        assertEquals(3, controller.sorting)
        assertTrue(labels.any { it.startsWith("recruitment.reset") })
        assertTrue(labels.none { it == "recruitment.manage" })
    }
}
