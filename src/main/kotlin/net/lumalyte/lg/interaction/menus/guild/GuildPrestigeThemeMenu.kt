package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.lumalyte.lg.domain.rewards.HallTheme
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * REQ-260: the step between choosing the reward to keep and confirming a prestige. Owned themes
 * are shown greyed out; picking one opens the confirmation with that theme.
 */
class GuildPrestigeThemeMenu(
    private val player: Player,
    private val owned: List<HallTheme>,
    private val back: () -> Unit,
    private val chosen: (HallTheme) -> Unit,
) : Menu, KoinComponent {
    private val lang: LangService by inject()

    internal fun createGui(): ChestGui {
        val gui = ChestGui(3, lang.guiTitle("hall.pick.title"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 3)
        HallTheme.entries.zip(SLOTS).forEach { (theme, slot) ->
            val has = theme in owned
            val item = ItemStack.of(if (has) Material.GRAY_DYE else HallThemeText.icon(theme))
            item.editMeta { meta ->
                meta.displayName(plain(lang.gui("hall.pick.name", "theme" to theme.label)))
                meta.lore((HallThemeText.cosmeticLines(lang, theme) + Component.empty() +
                    if (has) lang.gui("hall.pick.owned") else lang.gui("hall.pick.action")).map(::plain))
            }
            pane.addItem(GuiItem(item) { event ->
                event.isCancelled = true
                if (!has && player.isOnline) chosen(theme)
            }, slot % 9, slot / 9)
        }
        val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
        backItem.editMeta { it.displayName(plain(lang.gui("hall.pick.back"))) }
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            back()
        }, 4, 2)
        gui.addPane(pane)
        return gui
    }

    override fun open() {
        if (player.isOnline) createGui().show(player)
    }

    private fun plain(component: Component) = component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    private companion object {
        val SLOTS = listOf(10, 11, 12, 14, 15, 16)
    }
}
