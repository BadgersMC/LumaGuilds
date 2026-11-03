package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals

/** Exercise the real seven return button callbacks, not copied slot mappings. */
internal class GuildDiplomacyReturnTest : GuildRankMenuFixture() {
    @Test fun `diplomacy Back then control-panel Back never reopens the child`() {
        val factory = GlobalContext.get().get<MenuFactory>()
        val constructors: List<(MenuNavigator) -> Menu> = listOf(
            { AlliesListMenu(it, player, guild) },
            { EnemiesListMenu(it, player, guild) },
            { IncomingRequestsMenu(it, player, guild) },
            { OutgoingRequestsMenu(it, player, guild) },
            { AllianceRequestMenu(it, player, guild) },
            { TruceRequestMenu(it, player, guild) },
            { EnemyDeclarationMenu(it, player, guild) },
        )
        constructors.forEach { create ->
            val visited = mutableListOf<String>()
            val navigator = MenuNavigator(player)
            val dashboard = object : Menu { override fun open() { visited += "dashboard" } }
            val relations = mockk<GuildRelationsMenu>(relaxed = true)
            every { relations.open() } answers { visited += "relations"; Unit }
            every { factory.createGuildRelationsMenu(navigator, player, guild) } returns relations
            val child = spyk(create(navigator))
            every { child.open() } answers { visited += "child"; Unit }
            navigator.openMenu(dashboard)
            navigator.openMenu(relations)
            navigator.openMenu(child)
            val pane = StaticPane(0, 0, 9, 6)
            child.javaClass.getDeclaredMethod("addBackButton", StaticPane::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(child, pane, 4, 5)
            pane.getItem(4, 5)!!.callAction(mockk(relaxed = true))
            assertEquals("relations", visited.last(), child.javaClass.simpleName)
            navigator.goBack()
            assertEquals("dashboard", visited.last(), child.javaClass.simpleName)
        }
    }
}
