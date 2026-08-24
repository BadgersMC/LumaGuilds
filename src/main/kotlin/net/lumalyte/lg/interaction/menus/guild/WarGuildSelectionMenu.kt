package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.GuiTheme
import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WarGuildSelectionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val availableGuilds: List<Guild>,
    private val callback: (Guild) -> Unit,
    private var currentPage: Int = 0
) : Menu, KoinComponent {

    private val memberService: MemberService by inject()
    private val lang: LangService by inject()

    companion object {
        private const val GUILDS_PER_PAGE = 28 // 4 rows
    }

    override fun open() {
        val totalPages = (availableGuilds.size + GUILDS_PER_PAGE - 1) / GUILDS_PER_PAGE
        val actualPage = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))

        val gui = ChestGui(6, MenuTitleBuilder.build(GuiTheme.NEUTRAL, 6, lang.legacy("menu.war_guild_selection.title", "page" to actualPage + 1, "pages" to totalPages)))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true }
        gui.addPane(pane)

        // Add guild items (rows 0-3)
        val startIndex = actualPage * GUILDS_PER_PAGE
        val endIndex = (startIndex + GUILDS_PER_PAGE).coerceAtMost(availableGuilds.size)
        val pageGuilds = availableGuilds.subList(startIndex, endIndex)

        var slot = 0
        for (guild in pageGuilds) {
            val x = slot % 9
            val y = slot / 9
            addGuildItem(pane, guild, x, y)
            slot++
        }

        // Add info item (row 4)
        addInfoItem(pane, availableGuilds.size, 4, 4)

        // Add navigation (row 5)
        if (actualPage > 0) {
            addPreviousPageButton(pane, 3, 5)
        }
        addBackButton(pane, 4, 5)
        if (actualPage < totalPages - 1) {
            addNextPageButton(pane, 5, 5)
        }

        gui.show(player)
    }

    private fun addGuildItem(pane: StaticPane, guild: Guild, x: Int, y: Int) {
        val memberCount = memberService.getGuildMembers(guild.id).size

        // Choose icon based on guild mode
        val icon = when (guild.mode) {
            GuildMode.HOSTILE -> Material.DIAMOND_SWORD
            GuildMode.PEACEFUL -> Material.IRON_SWORD
        }

        val item = ItemStack.of(icon)
            .name(lang.legacy("menu.war_guild_selection.guild.name", "guild" to guild.name))
            .lore(
                when (guild.mode) {
                    GuildMode.HOSTILE -> lang.legacy("menu.war_guild_selection.guild.mode.hostile")
                    GuildMode.PEACEFUL -> lang.legacy("menu.war_guild_selection.guild.mode.peaceful")
                }
            )
            .lore(lang.legacy("menu.war_guild_selection.guild.members", "count" to memberCount))
            .lore(lang.legacy("menu.war_guild_selection.guild.level", "level" to guild.level))
            .lore(lang.legacy("menu.common.blank"))

        // Add warning for peaceful guilds
        if (guild.mode == GuildMode.PEACEFUL) {
            item.lore(lang.legacy("menu.war_guild_selection.guild.peaceful.name"))
            item.lore(lang.legacy("menu.war_guild_selection.guild.peaceful.description"))
        } else {
            item.lore(lang.legacy("menu.war_guild_selection.guild.hostile.name"))
            item.lore(lang.legacy("menu.war_guild_selection.guild.hostile.description"))
        }

        item.lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.war_guild_selection.guild.action"))

        val guiItem = GuiItem(item) {
            callback(guild)
            player.sendMessage(lang.msg("menu.war_guild_selection.feedback.selected", "guild" to guild.name))
            menuNavigator.goBack()
        }

        pane.addItem(guiItem, x, y)
    }

    private fun addInfoItem(pane: StaticPane, totalGuilds: Int, x: Int, y: Int) {
        val item = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.war_guild_selection.info.name"))
            .lore(lang.legacy("menu.war_guild_selection.info.total", "count" to totalGuilds))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.war_guild_selection.info.instructions"))
            .lore(lang.legacy("menu.war_guild_selection.info.hostile"))
            .lore(lang.legacy("menu.war_guild_selection.info.peaceful"))

        pane.addItem(GuiItem(item) {}, x, y)
    }

    private fun addPreviousPageButton(pane: StaticPane, x: Int, y: Int) {
        val prevItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.war_guild_selection.navigation.previous.name"))
            .lore(lang.legacy("menu.war_guild_selection.navigation.page", "page" to currentPage))

        val guiItem = GuiItem(prevItem) {
            currentPage--
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addNextPageButton(pane: StaticPane, x: Int, y: Int) {
        val nextItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.war_guild_selection.navigation.next.name"))
            .lore(lang.legacy("menu.war_guild_selection.navigation.page", "page" to currentPage + 2))

        val guiItem = GuiItem(nextItem) {
            currentPage++
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.BARRIER)
            .name(lang.legacy("menu.war_guild_selection.navigation.back.name"))
            .lore(lang.legacy("menu.war_guild_selection.navigation.back.description"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    override fun passData(data: Any?) {
        // Not needed for this menu
    }
}
