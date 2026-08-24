package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.GuiTheme

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import net.badgersmc.nexus.i18n.LangService
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
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

class GuildSelectionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val currentGuild: Guild,
    private val selectedGuilds: MutableSet<UUID>
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    private lateinit var guildsPane: PaginatedPane
    private var currentPage = 0
    private val itemsPerPage = 45 // 9x5 grid

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.build(GuiTheme.NEUTRAL, 6, lang.legacy("menu.party.guild_selection.title")))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

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
                ItemStack.of(Material.WHITE_BANNER)
            }
        } else {
            ItemStack.of(Material.WHITE_BANNER)
        }

        val displayName = if (isSelected) {
            lang.legacy("menu.party.guild_selection.guild.selected_name", "guild" to guild.name)
        } else {
            lang.legacy("menu.party.guild_selection.guild.available_name", "guild" to guild.name)
        }
        val status = if (isSelected) {
            lang.legacy("menu.party.guild_selection.guild.selected_status")
        } else {
            lang.legacy("menu.party.guild_selection.guild.available_status")
        }
        val action = if (isSelected) {
            lang.legacy("menu.party.guild_selection.guild.remove")
        } else {
            lang.legacy("menu.party.guild_selection.guild.invite")
        }
        return bannerItem
            .name(displayName)
            .lore(lang.legacy("menu.party.guild_selection.guild.members", "count" to memberService.getGuildMembers(guild.id).size))
            .lore(status)
            .lore(lang.raw("menu.common.blank"))
            .lore(action)
    }

    private fun inviteGuild(guild: Guild) {
        if (selectedGuilds.contains(guild.id)) {
            // Remove from selection
            selectedGuilds.remove(guild.id)
            player.sendMessage(lang.msg("bedrock.party.guild_selection.feedback.removed", "guild" to guild.name))
        } else {
            // Add to selection
            selectedGuilds.add(guild.id)
            player.sendMessage(lang.msg("bedrock.party.guild_selection.feedback.added", "guild" to guild.name))
        }

        // Refresh the menu
        open()
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allGuilds = guildService.getAllGuilds()
            .filter { it.id != currentGuild.id }
        val totalPages = (allGuilds.size + itemsPerPage - 1) / itemsPerPage

        // Previous page button
        val prevItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.party.guild_selection.previous.name"))
            .lore(lang.legacy("menu.party.guild_selection.previous.lore"))

        val prevGuiItem = GuiItem(prevItem) {
            if (currentPage > 0) {
                currentPage--
                updateGuildsDisplay()
            }
        }
        pane.addItem(prevGuiItem, 0, 5)

        // Next page button
        val nextItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.party.guild_selection.next.name"))
            .lore(lang.legacy("menu.party.guild_selection.next.lore"))

        val nextGuiItem = GuiItem(nextItem) {
            if (currentPage < totalPages - 1) {
                currentPage++
                updateGuildsDisplay()
            }
        }
        pane.addItem(nextGuiItem, 8, 5)

        // Page indicator
        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.legacy("menu.party.guild_selection.page.name", "page" to currentPage + 1, "total_pages" to maxOf(1, totalPages)))
            .lore(lang.legacy("menu.party.guild_selection.page.lore"))

        pane.addItem(GuiItem(pageItem), 2, 5)
    }

    private fun addSelectedSummary(pane: StaticPane, x: Int, y: Int) {
        val selectedCount = selectedGuilds.size
        val summaryItem = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.party.guild_selection.summary.name", "count" to selectedCount))
            .lore(lang.legacy("menu.party.guild_selection.summary.lore_first"))
            .lore(lang.legacy("menu.party.guild_selection.summary.lore_second"))

        pane.addItem(GuiItem(summaryItem), x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.BARRIER)
            .name(lang.legacy("menu.party.guild_selection.back.name"))
            .lore(lang.legacy("menu.party.guild_selection.back.lore"))

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
    }

    override fun passData(data: Any?) {
        // Handle data passed back from sub-menus if needed
    }
}

