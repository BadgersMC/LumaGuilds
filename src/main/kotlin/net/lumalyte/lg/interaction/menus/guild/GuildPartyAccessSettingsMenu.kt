package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyAccessLevel
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant
import java.util.*
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildPartyAccessSettingsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                  private val guild: Guild, private val party: Party, private val messageService: MessageService) : Menu, KoinComponent {

    private val partyService: PartyService by inject()

    override fun open() {
        val gui = ChestGui(4, "<gold>Party Access Settings - ${party.name ?: "Unnamed Party"}", )
        val pane = StaticPane(0, 0, 9, 4)

        // Current Settings Overview
        addCurrentSettings(pane)

        // Access Level Configuration
        addAccessLevelSettings(pane)

        // Member Limits
        addMemberLimitSettings(pane)

        // Description Management
        addDescriptionSettings(pane)

        // Back
        val backItem = ItemStack(Material.BARRIER)
        val backMeta = backItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BARRIER)!!
        backMeta.setDisplayName("<red>Back")
        backMeta.lore = listOf("<gray>Return to party details")
        backItem.itemMeta = backMeta
        pane.addItem(GuiItem(backItem) { _ ->
            menuNavigator.goBack()
        }, 4, 3)

        gui.addPane(pane)
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }
        gui.show(player)
    }

    private fun addCurrentSettings(pane: StaticPane) {
        // Current Settings Overview
        val settingsItem = ItemStack(Material.BOOK)
        val settingsMeta = settingsItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BOOK)!!
        settingsMeta.setDisplayName("<aqua>Current Settings")
        settingsMeta.lore = listOf(
            "<gray>Access Level: <white>${party.accessLevel.name.lowercase().replaceFirstChar { it.uppercase() }}",
            "<gray>Max Members: <white>${party.maxMembers}",
            "<gray>Description: <white>${party.description ?: "None"}",
            "<gray>Role Restrictions: <white>${if (party.restrictedRoles != null) "Enabled" else "Disabled"}"
        )
        settingsItem.itemMeta = settingsMeta

        pane.addItem(GuiItem(settingsItem), 0, 0)
    }

    private fun addAccessLevelSettings(pane: StaticPane) {
        // Access Level Selection
        val accessLevels = PartyAccessLevel.values()
        accessLevels.forEachIndexed { index, accessLevel ->
            val isSelected = accessLevel == party.accessLevel
            val material = if (isSelected) Material.LIME_WOOL else Material.WHITE_WOOL
            val itemName = if (isSelected) "<green>✓ ${accessLevel.name.lowercase().replaceFirstChar { it.uppercase() }}" else "<gray>${accessLevel.name.lowercase().replaceFirstChar { it.uppercase() }}"

            val accessItem = ItemStack(material)
            val accessMeta = accessItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(material)!!
            accessMeta.setDisplayName(itemName)
            accessMeta.lore = listOf(
                "<gray>${getAccessLevelDescription(accessLevel)}",
                if (isSelected) "<green>Currently selected" else "<gray>Click to select"
            )
            accessItem.itemMeta = accessMeta

            pane.addItem(GuiItem(accessItem) { _ ->
                updateAccessLevel(accessLevel)
            }, index + 1, 1)
        }
    }

    private fun addMemberLimitSettings(pane: StaticPane) {
        // Current Max Members
        val currentLimitItem = ItemStack(Material.PLAYER_HEAD)
        val currentLimitMeta = currentLimitItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.PLAYER_HEAD)!!
        currentLimitMeta.setDisplayName("<gold>Max Members: ${party.maxMembers}")
        currentLimitMeta.lore = listOf(
            "<gray>Current limit: <white>${party.maxMembers}",
            "<gray>Click to change limit"
        )
        currentLimitItem.itemMeta = currentLimitMeta

        pane.addItem(GuiItem(currentLimitItem) { _ ->
            openMemberLimitEditor()
        }, 0, 2)

        // Quick Limit Options
        val limits = listOf(10, 25, 50, 100, 200)
        limits.forEachIndexed { index, limit ->
            val isSelected = limit == party.maxMembers
            val material = if (isSelected) Material.LIME_WOOL else Material.WHITE_WOOL
            val itemName = if (isSelected) "<green>✓ $limit" else "<gray>$limit"

            val limitItem = ItemStack(material)
            val limitMeta = limitItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(material)!!
            limitMeta.setDisplayName(itemName)
            limitMeta.lore = listOf(if (isSelected) "<green>Currently selected" else "<gray>Click to select")
            limitItem.itemMeta = limitMeta

            pane.addItem(GuiItem(limitItem) { _ ->
                updateMaxMembers(limit)
            }, index + 2, 2)
        }
    }

    private fun addDescriptionSettings(pane: StaticPane) {
        // Current Description
        val descItem = ItemStack(Material.PAPER)
        val descMeta = descItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.PAPER)!!
        descMeta.setDisplayName("<aqua>Party Description")
        descMeta.lore = listOf(
            "<gray>Current: <white>${party.description ?: "No description"}",
            "<gray>Click to edit description"
        )
        descItem.itemMeta = descMeta

        pane.addItem(GuiItem(descItem) { _ ->
            openDescriptionEditor()
        }, 6, 2)

        // Clear Description
        val clearItem = ItemStack(Material.REDSTONE)
        val clearMeta = clearItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.REDSTONE)!!
        clearMeta.setDisplayName("<red>Clear Description")
        clearMeta.lore = listOf("<gray>Remove current description")
        clearItem.itemMeta = clearMeta

        pane.addItem(GuiItem(clearItem) { _ ->
            updateDescription(null)
        }, 8, 2)
    }

    private fun openMemberLimitEditor() {
        val anvilGui = AnvilGui("Set Max Members")

        // Prevent clicking in anvil slots
        anvilGui.setOnTopClick { event -> event.isCancelled = true }
        anvilGui.setOnBottomClick { event -> event.isCancelled = true }

        // Handle name input changes
        var currentLimit = party.maxMembers.toString()
        anvilGui.setOnNameInputChanged { newInput ->
            currentLimit = newInput
        }

        // Add confirm button
        val firstPane = com.github.stefvanschie.inventoryframework.pane.StaticPane(0, 0, 1, 1)
        val confirmItem = ItemStack(Material.LIME_WOOL)
        val confirmMeta = confirmItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.LIME_WOOL)!!
        confirmMeta.setDisplayName("<green>Set Limit")
        confirmMeta.lore = listOf("<gray>Click to set max members to $currentLimit")
        confirmItem.itemMeta = confirmMeta

        val guiItem = com.github.stefvanschie.inventoryframework.gui.GuiItem(confirmItem) { _ ->
            try {
                val limit = currentLimit.toInt()
                if (limit > 0 && limit <= 1000) {
                    updateMaxMembers(limit)
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>Max members set to $limit")
                } else {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>Invalid limit. Must be between 1 and 1000.")
                }
            } catch (e: NumberFormatException) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>Invalid number format.")
            }
            player.closeInventory()
        }
        firstPane.addItem(guiItem, 0, 0)
        anvilGui.firstItemComponent.addPane(firstPane)

        anvilGui.setOnTopClick { event -> event.isCancelled = true }
        anvilGui.setOnBottomClick { event -> event.isCancelled = true }
        anvilGui.show(player)
    }

    private fun openDescriptionEditor() {
        val anvilGui = AnvilGui("Party Description")

        // Prevent clicking in anvil slots
        anvilGui.setOnTopClick { event -> event.isCancelled = true }
        anvilGui.setOnBottomClick { event -> event.isCancelled = true }

        // Handle name input changes
        var currentDescription = party.description ?: ""
        anvilGui.setOnNameInputChanged { newInput ->
            currentDescription = newInput
        }

        // Add confirm button
        val firstPane = com.github.stefvanschie.inventoryframework.pane.StaticPane(0, 0, 1, 1)
        val confirmItem = ItemStack(Material.LIME_WOOL)
        val confirmMeta = confirmItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.LIME_WOOL)!!
        confirmMeta.setDisplayName("<green>Set Description")
        confirmMeta.lore = listOf("<gray>Click to set description")
        confirmItem.itemMeta = confirmMeta

        val guiItem = com.github.stefvanschie.inventoryframework.gui.GuiItem(confirmItem) { _ ->
            updateDescription(currentDescription)
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Party description updated")
            player.closeInventory()
        }
        firstPane.addItem(guiItem, 0, 0)
        anvilGui.firstItemComponent.addPane(firstPane)

        // Set initial text - this would need to be handled differently in the real implementation
        // For now, just show the anvil GUI
        anvilGui.setOnTopClick { event -> event.isCancelled = true }
        anvilGui.setOnBottomClick { event -> event.isCancelled = true }
        anvilGui.show(player)
    }

    private fun updateAccessLevel(accessLevel: PartyAccessLevel) {
        val updates = mapOf("accessLevel" to accessLevel)
        val success = partyService.updatePartySettings(party.id, updates, player.uniqueId)
        if (success) {
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Access level updated to ${accessLevel.name.lowercase().replaceFirstChar { it.uppercase() }}")
            open() // Refresh menu
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to update access level")
        }
    }

    private fun updateMaxMembers(limit: Int) {
        val updates = mapOf("maxMembers" to limit)
        val success = partyService.updatePartySettings(party.id, updates, player.uniqueId)
        if (success) {
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Max members updated to $limit")
            open() // Refresh menu
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to update max members")
        }
    }

    private fun updateDescription(description: String?) {
        val updates = mapOf("description" to (description ?: ""))
        val success = partyService.updatePartySettings(party.id, updates, player.uniqueId)
        if (success) {
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Party description updated")
            open() // Refresh menu
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to update description")
        }
    }

    private fun getAccessLevelDescription(accessLevel: PartyAccessLevel): String {
        return when (accessLevel) {
            PartyAccessLevel.OPEN -> "Anyone can join the party"
            PartyAccessLevel.INVITE_ONLY -> "Only invited players can join"
            PartyAccessLevel.GUILD_ONLY -> "Only guild members can join"
            PartyAccessLevel.LEADER_ONLY -> "Only party leader can invite"
        }
    }
}
