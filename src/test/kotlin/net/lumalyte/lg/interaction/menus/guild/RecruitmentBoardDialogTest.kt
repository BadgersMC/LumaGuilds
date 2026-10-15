package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.infrastructure.services.accept
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val BROWSE_TITLE = "Browse"
private const val SAMPLE_GUILD = "Everlights"

internal class RecruitmentBoardDialogTest : RecruitmentDialogFixture() {
    @Test
    fun searchResetsPageKeepsFilters() {
        controller.screen = RecruitmentScreen.BROWSE
        controller.interest = "building"
        controller.page = 2
        controller.browsePage = 2
        controller.beginnersOnly = true
        menu().show(RecruitmentView(BROWSE_TITLE, emptyList(), emptyList()))
        val search = requireNotNull(requireNotNull(renderedPage).search)
        assertEquals("building", search.category)
        assertTrue(search.accept("  Everlights  ", "quests"))
        assertEquals(SAMPLE_GUILD, controller.query)
        assertEquals("quests", controller.interest)
        assertEquals(0, controller.page)
        assertEquals(0, controller.browsePage)
        assertTrue(controller.beginnersOnly)
    }

    @Test
    fun boardHasDirectNavigation() {
        controller.screen = RecruitmentScreen.BROWSE
        val direct =
            listOf("recruitment.filter", "recruitment.mine", "recruitment.manage")
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
    fun boardKeepsBannerAndFee() {
        controller.screen = RecruitmentScreen.BROWSE
        val guild =
            net.lumalyte.lg.domain.entities.Guild(
                java.util.UUID.randomUUID(),
                SAMPLE_GUILD,
                createdAt = java.time.Instant.EPOCH,
            )
        val fee = "Joining fee: 123456789 a currency name that spans multiple panel lines"
        val entry = RecruitmentButton(SAMPLE_GUILD, listOf("Apply", "Spaces: 6", "Level: 32", fee), guild = guild) {}
        menu().show(RecruitmentView(BROWSE_TITLE, listOf(entry), emptyList()))
        val item = requireNotNull(renderedPage).items.single()
        assertEquals(org.bukkit.Material.WHITE_BANNER, item.item.type)
        val panelText =
            requireNotNull(item.cardLines).drop(1).joinToString("") {
                PlainTextComponentSerializer.plainText().serialize(it)
            }
        assertTrue(panelText.endsWith(fee))
        assertTrue(PlainTextComponentSerializer.plainText().serialize(item.text).contains(fee))
    }
}
