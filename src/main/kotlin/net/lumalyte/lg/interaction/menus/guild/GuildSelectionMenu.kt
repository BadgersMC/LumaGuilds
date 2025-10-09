package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.lumalyte.lg.utils.deserializeToItemStack
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildSelectionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val currentGuild: Guild,
    private val selectedGuilds: MutableSet<UUID>
, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    private lateinit var guildsPane: PaginatedPane
    private var currentPage = 0
    private val itemsPerPage = 45 // 9x5 grid

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Select Guilds to Invite"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        // Initialize guilds display pane
        guildsPane = PaginatedPane(0, 0, 9, 5)
        updateGuildsDisplay()

        // Add navigation buttons
        addNavigationButtons(pane)

        // Add back button
        addBackButton(pane, 4, 5)

        // Add selected guilds summary
        addSelectedSummary(pane, 6, 5)

        gui.addPane(guildsPane)
        gui.addPane(pane)
        gui.show(player)
    }

    private fun updateGuildsDisplay() {
        val allGuilds = guildService.getAllGuilds()
            .filter { it.id != currentGuild.id } // Exclude current guild
            .sortedBy { it.name }

        // Calculate pagination
        val totalPages = (allGuilds.size + itemsPerPage - 1) / itemsPerPage
        if (currentPage >= totalPages && totalPages > 0) {
            currentPage = totalPages - 1
        }

        // Get guilds for current page
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, allGuilds.size)
        val pageGuilds = allGuilds.subList(startIndex, endIndex)

        // Clear existing panes
        guildsPane.clear()

        val newPage = StaticPane(0, 0, 9, 5)

        // Add guild items to the page
        for ((index, guild) in pageGuilds.withIndex()) {
            val x = index % 9
            val y = index / 9
            val guildItem = createGuildItem(guild)
            val guiItem = GuiItem(guildItem) {
                inviteGuild(guild)
            }
            newPage.addItem(guiItem, x, y)
        }

        guildsPane.addPage(newPage)

        // Update pagination
        guildsPane.page = 0
    }

    private fun createGuildItem(guild: Guild): ItemStack {
        val isSelected = selectedGuilds.contains(guild.id)

        // Try to use guild banner, fallback to default banner
        val bannerItem = if (guild.banner != null) {
            val deserialized = guild.banner.deserializeToItemStack()
            if (deserialized != null) {
                deserialized.clone()
            } else {
                ItemStack(Material.WHITE_BANNER)
            }
        } else {
            ItemStack(Material.WHITE_BANNER)
        }

        return bannerItem
            .name("${if (isSelected) "<green>✅" else "<gray>❌"} ${guild.name}")
            .addAdventureLore(player, messageService, "<gray>Members: <white>${memberService.getGuildMembers(guild.id).size}")
            .lore(if (isSelected) "<gray>Status: <green>Already invited" else "<gray>Status: <gray>Available")
            .addAdventureLore(player, messageService, "<gray>")
            .lore(if (isSelected) "<red>Click to remove invitation" else "<green>Click to invite")
    }

    private fun inviteGuild(guild: Guild) {
        if (selectedGuilds.contains(guild.id)) {
            // Remove from selection
            selectedGuilds.remove(guild.id)
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Removed ${guild.name} from party invitation")
        } else {
            // Add to selection
            selectedGuilds.add(guild.id)
            AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Added ${guild.name} to party invitation")
        }

        // Refresh the menu
        open()
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allGuilds = guildService.getAllGuilds()
            .filter { it.id != currentGuild.id }
        val totalPages = (allGuilds.size + itemsPerPage - 1) / itemsPerPage

        // Previous page button
        val prevItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<white>⬅️ PREVIOUS PAGE")
            .addAdventureLore(player, messageService, "<gray>Go to previous page")

        val prevGuiItem = GuiItem(prevItem) {
            if (currentPage > 0) {
                currentPage--
                updateGuildsDisplay()
            }
        }
        pane.addItem(prevGuiItem, 0, 5)

        // Next page button
        val nextItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<white>NEXT PAGE ➡️")
            .addAdventureLore(player, messageService, "<gray>Go to next page")

        val nextGuiItem = GuiItem(nextItem) {
            if (currentPage < totalPages - 1) {
                currentPage++
                updateGuildsDisplay()
            }
        }
        pane.addItem(nextGuiItem, 8, 5)

        // Page indicator
        val pageItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<white>📄 PAGE ${currentPage + 1}/${maxOf(1, totalPages)}")
            .addAdventureLore(player, messageService, "<gray>Current page indicator")

        pane.addItem(GuiItem(pageItem), 2, 5)
    }

    private fun addSelectedSummary(pane: StaticPane, x: Int, y: Int) {
        val selectedCount = selectedGuilds.size
        val summaryItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<gold>📋 Selected Guilds: $selectedCount")
            .addAdventureLore(player, messageService, "<gray>Selected guilds will be invited")
            .addAdventureLore(player, messageService, "<gray>to the party when created")

        pane.addItem(GuiItem(summaryItem), x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>⬅️ BACK TO CREATION")
            .addAdventureLore(player, messageService, "<gray>Return to party creation menu")

        val backGuiItem = GuiItem(backItem) {
            // Pass back the selected guilds to the party creation menu
            menuNavigator.openMenu(menuFactory.createPartyCreationMenu(menuNavigator, player, currentGuild).apply {
                passData(mapOf(
                    "selectedGuilds" to selectedGuilds.toSet(),
                    "partyName" to "", // Will be handled by the creation menu
                    "restrictedRoles" to setOf<UUID>() // Will be handled by the creation menu
                ))
            })
        }
        pane.addItem(backGuiItem, x, y)
    }}

