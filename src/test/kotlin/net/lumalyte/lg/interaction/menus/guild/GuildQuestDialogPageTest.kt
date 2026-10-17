package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.lumalyte.lg.infrastructure.services.GuildDialogAction
import net.lumalyte.lg.utils.GuiTheme
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class GuildQuestDialogPageTest {
    @BeforeEach fun setup() {
        MockBukkit.mock()
    }

    @AfterEach fun cleanup() {
        MockBukkit.unmock()
    }

    @Test
    fun questCardsOpenStandingsWhileWeeklyRankingsAndBackRemainButtons() {
        var opened = 0
        var ranked = 0
        var returned = 0
        val item = questItem()
        val open =
            GuildDialogAction(Component.text("Open quest"), Component.text("Score, reward and rank")) { opened++ }
        val rankings = GuildDialogAction(Component.text("Weekly rankings"), Component.empty()) { ranked++ }
        val back = GuildDialogAction(Component.text("Back"), Component.empty()) { returned++ }
        val page =
            guildQuestDialogPage(
                Component.text("Quests"),
                item,
                listOf(item to open),
                listOf(rankings),
                back,
                GuiTheme.ENTHUSIA,
            )
        assertNull(page.items.first().onClick)
        assertNotNull(page.items.last().onClick).action()
        assertEquals(listOf(rankings), page.actions)
        page.actions.single().action()
        page.back.action()
        assertEquals(listOf(1, 1, 1), listOf(opened, ranked, returned))
    }

    @Test
    fun disabledQuestCardsRestoreExplicitActionsAndPreserveDetails() {
        val item = questItem()
        val open = GuildDialogAction(Component.text("Open quest"), Component.text("Standings")) {}
        val back = GuildDialogAction(Component.text("Back"), Component.empty()) {}
        val page =
            guildQuestDialogPage(
                Component.text("Quests"),
                item,
                listOf(item to open),
                emptyList(),
                back,
                GuiTheme.VANILLA,
                false,
            )
        assertNull(page.items.last().onClick)
        assertEquals(listOf(open), page.actions)
        assertEquals(2, assertNotNull(page.items.last().cardLines).size)
    }

    private fun questItem(): ItemStack =
        ItemStack.of(Material.BOOK).also {
            it.editMeta { meta ->
                meta.displayName(Component.text("Gather resources"))
                meta.lore(listOf(Component.text("50 / 100; 500 XP")))
            }
        }
}
