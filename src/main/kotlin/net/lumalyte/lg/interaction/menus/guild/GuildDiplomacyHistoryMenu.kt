package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.DiplomacyLog
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** REQ-204: a guild's diplomacy history, newest first, 45 per page. */
class GuildDiplomacyHistoryMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {
    private val history: DiplomacyLog by inject()
    private val guildService: GuildService by inject()
    private val lang: LangService by inject()
    private var page = 0

    override fun open() {
        val total = history.count(guild.id)
        val pages = maxOf(1, (total + PAGE_SIZE - 1) / PAGE_SIZE)
        page = page.coerceIn(0, pages - 1)
        val entries = history.page(guild.id, page * PAGE_SIZE, PAGE_SIZE)

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("diplomacy_history.title", "guild" to guild.name)))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)

        if (entries.isEmpty()) {
            pane.addItem(
                GuiItem(
                    ItemStack.of(Material.PAPER)
                        .name(lang.gui("diplomacy_history.empty.name"))
                        .lore(lang.gui("diplomacy_history.empty.lore")),
                ),
                4, 2,
            )
        }
        val unknown = lang.raw("menu.statistics.common.unknown_guild")
        entries.forEachIndexed { index, entry ->
            val other = guildService.getGuild(entry.otherGuildId)?.name ?: unknown
            val (id, fallback) = DiplomacyHistoryText.icon(entry.kind)
            val item = NexoItemProvider.getItemStackOrFallback(id) { ItemStack.of(fallback) }
                .name(DiplomacyHistoryText.title(lang, entry, other))
                .lore(DiplomacyHistoryText.dated(lang, entry))
            pane.addItem(GuiItem(item), index % 9, index / 9)
        }

        if (page > 0) pane.addItem(nav("lg_page_prev", lang.gui("guild_page.nav.previous")) { page--; open() }, 0, 5)
        pane.addItem(nav("lg_back", lang.gui("guild_page.nav.back")) { menuNavigator.goBack() }, 4, 5)
        if (page < pages - 1) pane.addItem(nav("lg_page_next", lang.gui("guild_page.nav.next")) { page++; open() }, 8, 5)
        gui.addPane(pane)
        gui.show(player)
    }

    private fun nav(id: String, title: net.kyori.adventure.text.Component, action: () -> Unit) =
        GuiItem(NexoItemProvider.getItemStackOrFallback(id) { ItemStack.of(Material.ARROW) }.name(title)) { action() }

    override fun passData(data: Any?) = Unit

    private companion object {
        const val PAGE_SIZE = 45
    }
}
