package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import org.koin.core.context.GlobalContext

/** Invokes real item callbacks while tracing the navigator's retained menu instances. */
internal abstract class GuildParentReturnFixture : GuildRankMenuFixture() {
    protected val factory: MenuFactory get() = GlobalContext.get().get()

    protected inline fun <reified T : Menu> traced(menu: T, label: String, visits: MutableList<String>): T {
        val traced = spyk(menu)
        every { traced.open() } answers { visits += label; Unit }
        return traced
    }

    protected fun dashboard(visits: MutableList<String>): Menu = object : Menu {
        override fun open() { visits += "dashboard" }
    }

    protected fun paneFor(menu: Menu, method: String, vararg coordinates: Int): StaticPane {
        val pane = StaticPane(0, 0, 9, 6)
        val types = Array<Class<*>>(coordinates.size + 1) {
            if (it == 0) StaticPane::class.java else Integer.TYPE
        }
        menu.javaClass.getDeclaredMethod(method, *types).also { it.isAccessible = true }
            .invoke(menu, pane, *coordinates.toTypedArray())
        return pane
    }

    protected fun click(pane: StaticPane, x: Int, y: Int) {
        requireNotNull(pane.getItem(x, y)).callAction(mockk(relaxed = true))
    }

    protected fun field(menu: Menu, name: String): Any? =
        menu.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(menu)

    protected fun setField(menu: Menu, name: String, value: Any) {
        menu.javaClass.getDeclaredField(name).also { it.isAccessible = true }.set(menu, value)
    }
}
