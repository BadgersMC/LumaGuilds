package net.lumalyte.lg.interaction.menus.guild

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

    companion object {
        private const val GUILDS_PER_PAGE = 28 // 4 rows
    }

    override fun open() {
        val totalPages = (availableGuilds.size + GUILDS_PER_PAGE - 1) / GUILDS_PER_PAGE
        val actualPage = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))

        val gui = ChestGui(6, "§4⚔ Select War Target (${actualPage + 1}/$totalPages)")
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

        // Choose color based on guild mode
        val modeColor = when (guild.mode) {
            GuildMode.HOSTILE -> "§c"
            GuildMode.PEACEFUL -> "§a"
        }

        val item = ItemStack(icon)
            .name("§6${guild.name}")
            .lore("§7Mode: $modeColor${guild.mode.name}")
            .lore("§7Members: §f$memberCount")
            .lore("§7Level: §e${guild.level}")
            .lore("")

        // Add warning for peaceful guilds
        if (guild.mode == GuildMode.PEACEFUL) {
            item.lore("§a✓ Peaceful guild")
            item.lore("§7They must accept your declaration")
        } else {
            item.lore("§c⚔ Hostile guild")
            item.lore("§7War will start immediately!")
        }

        item.lore("")
            .lore("§eClick to select as war target")

        val guiItem = GuiItem(item) {
            callback(guild)
            player.sendMessage("§a✓ Selected §6${guild.name}§a as war target!")
            menuNavigator.goBack()
        }

        pane.addItem(guiItem, x, y)
    }

    private fun addInfoItem(pane: StaticPane, totalGuilds: Int, x: Int, y: Int) {
        val item = ItemStack(Material.BOOK)
            .name("§e📖 Guild Info")
            .lore("§7Total Available: §f$totalGuilds guilds")
            .lore("")
            .lore("§7Select a guild to declare war against.")
            .lore("§7Hostile guilds auto-accept immediately.")
            .lore("§7Peaceful guilds must accept your declaration.")

        pane.addItem(GuiItem(item) {}, x, y)
    }

    private fun addPreviousPageButton(pane: StaticPane, x: Int, y: Int) {
        val prevItem = ItemStack(Material.ARROW)
            .name("§e← Previous Page")
            .lore("§7Go to page ${currentPage}")

        val guiItem = GuiItem(prevItem) {
            currentPage--
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addNextPageButton(pane: StaticPane, x: Int, y: Int) {
        val nextItem = ItemStack(Material.ARROW)
            .name("§eNext Page →")
            .lore("§7Go to page ${currentPage + 2}")

        val guiItem = GuiItem(nextItem) {
            currentPage++
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.BARRIER)
            .name("§c← Back")
            .lore("§7Return to war declaration")

        val guiItem = GuiItem(backItem) {
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    override fun passData(data: Any?) {
        // Not needed for this menu
    }
}
