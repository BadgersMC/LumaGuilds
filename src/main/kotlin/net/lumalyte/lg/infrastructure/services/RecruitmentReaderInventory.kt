package net.lumalyte.lg.infrastructure.services

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.interaction.menus.GuildBannerItemResolver
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack

/** Reuses existing background glyphs and navigation models; Escape returns only from this reader. */
internal object RecruitmentReaderInventory {
    private const val ROWS = 6
    private const val COLUMNS = 9
    private const val FOOTER = 5
    private const val CENTER_COLUMN = 4
    private const val DETAILS_ROW = 2
    private const val LAST_COLUMN = COLUMNS - 1

    fun show(
        player: Player,
        content: RecruitmentBookContent,
        page: RecruitmentReaderPage,
        actions: RecruitmentReaderActions,
    ) {
        val title = "${content.title} ${page.number}/${page.count}"
        val gui = ChestGui(ROWS, MenuTitleBuilder.build(content.theme, ROWS, title))
        val pane = StaticPane(0, 0, COLUMNS, ROWS)
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        gui.setOnClose { event ->
            if (event.reason == InventoryCloseEvent.Reason.PLAYER) actions.dismissed()
        }
        val banner = content.guild?.let(GuildBannerItemResolver::resolveForDisplay)
        banner?.let { pane.addItem(GuiItem(it.name(Component.text(content.guild.name))), CENTER_COLUMN, 0) }
        val details = item("lg_description", content.title)
        if (content.context.isNotBlank()) details.lore(Component.text(content.context).color(content.accent()))
        page.text.lines().forEach { details.lore(Component.text(it).color(NamedTextColor.GRAY)) }
        pane.addItem(GuiItem(details), CENTER_COLUMN, DETAILS_ROW)
        pane.addItem(GuiItem(item("lg_back", content.returnLabel)) { actions.back() }, CENTER_COLUMN, FOOTER)
        actions.previous?.let { previous ->
            pane.addItem(GuiItem(item("lg_page_prev", actions.previousLabel)) { previous() }, 0, FOOTER)
        }
        actions.next?.let { next ->
            pane.addItem(GuiItem(item("lg_page_next", actions.nextLabel)) { next() }, LAST_COLUMN, FOOTER)
        }
        gui.addPane(pane)
        gui.show(player)
    }

    private fun item(id: String, label: String): ItemStack =
        NexoItemProvider.getItemStackOrFallback(id) { ItemStack.of(Material.BOOK) }
            .name(Component.text(label).color(NamedTextColor.GOLD))
}
