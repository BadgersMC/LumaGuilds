package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuItemBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

class DescriptionEditorMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                           private var guild: Guild): Menu, KoinComponent, ChatInputHandler {

    private val guildService: GuildService by inject()
    private val menuItemBuilder: MenuItemBuilder by inject()
    private val chatInputListener: ChatInputListener by inject()

    // State for the description input
    private var currentDescription: String? = null
    private var inputDescription: String? = null
    private var validationError: String? = null

    override fun open() {
        // Load current description (only if not already loaded)
        if (currentDescription == null) {
            currentDescription = guildService.getDescription(guild.id)
        }

        // Initialize inputDescription only if it's null (preserve user input)
        if (inputDescription == null) {
            inputDescription = currentDescription
        }

        // Validate current input
        validationError = validateDescription(inputDescription)

        val gui = ChestGui(4, "§6Edit Guild Description")
        val pane = StaticPane(0, 0, 9, 4)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Current description display
        addCurrentDescriptionDisplay(pane, 4, 0)

        // Input field
        addInputField(pane, 2, 1)

        // Validation status
        addValidationStatus(pane, 6, 1)

        // Save button
        addSaveButton(pane, 3, 2)

        // Cancel button
        addCancelButton(pane, 5, 2)

        // Preview
        addPreview(pane, 4, 3)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addCurrentDescriptionDisplay(pane: StaticPane, x: Int, y: Int) {
        val displayItem = ItemStack(Material.BOOK)
            .name("§eCurrent Description")
            .lore("§7${parseMiniMessageForDisplay(currentDescription) ?: "§oNone set"}")
            .lore("")
            .lore("§7This is the description that")
            .lore("§7is currently displayed for your guild")

        pane.addItem(GuiItem(displayItem), x, y)
    }

    private fun addInputField(pane: StaticPane, x: Int, y: Int) {
        val inputItem = ItemStack(Material.WRITABLE_BOOK)
            .name("§bDescription Input")
            .lore("§7Click to type your new description")
            .lore("")
            .lore("§7Current input:")
            .lore("§f${inputDescription ?: "§oNone"}")
            .lore("")
            .lore("§7Supports MiniMessage formatting")
            .lore("§7Max 100 characters")

        val guiItem = GuiItem(inputItem) {
            // Start chat input for description
            startChatInput()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addValidationStatus(pane: StaticPane, x: Int, y: Int) {
        val statusItem = if (validationError != null) {
            ItemStack(Material.RED_CONCRETE)
                .name("§c❌ Validation Error")
                .lore("§7${validationError}")
        } else {
            ItemStack(Material.GREEN_CONCRETE)
                .name("§a✅ Valid Description")
                .lore("§7Description is ready to save")
        }

        pane.addItem(GuiItem(statusItem), x, y)
    }

    private fun addSaveButton(pane: StaticPane, x: Int, y: Int) {
        val saveItem = ItemStack(Material.EMERALD_BLOCK)
            .name("§a💾 Save Description")
            .lore("§7Save your changes")
            .lore("")
            .lore("§7This will update the guild description")
            .lore("§7for all members to see")

        val canSave = validationError == null && inputDescription != currentDescription
        if (!canSave) {
            saveItem.name("§7💾 Save Description")
                .lore("§7Save your changes")
                .lore("")
                .lore("§c❌ Cannot save - check validation")
        }

        val guiItem = GuiItem(saveItem) {
            if (canSave) {
                saveDescription()
            } else {
                player.sendMessage("§c❌ Cannot save description - please check validation errors")
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack(Material.REDSTONE_BLOCK)
            .name("§c❌ Cancel")
            .lore("§7Discard changes and go back")

        val guiItem = GuiItem(cancelItem) {
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPreview(pane: StaticPane, x: Int, y: Int) {
        val previewItem = ItemStack(Material.ITEM_FRAME)
            .name("§d🔍 Preview")
            .lore("§7How your description will appear:")

        if (inputDescription != null && validationError == null) {
            try {
                val miniMessage = MiniMessage.miniMessage()
                val component = miniMessage.deserialize(inputDescription!!)
                val plainText = PlainTextComponentSerializer.plainText().serialize(component)

                previewItem.lore("§f\"${plainText}\"")
            } catch (e: Exception) {
                previewItem.lore("§cError parsing description")
            }
        } else {
            previewItem.lore("§7§oEnter a description to see preview")
        }

        pane.addItem(GuiItem(previewItem), x, y)
    }

    private fun saveDescription() {
        val description = inputDescription

        // Check permission
        if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_DESCRIPTION)) {
            player.sendMessage("§c❌ You don't have permission to manage guild description")
            return
        }

        // Save the description
        val success = guildService.setDescription(guild.id, description, player.uniqueId)

        if (success) {
            player.sendMessage("§a✅ Guild description updated successfully!")
            player.sendMessage("§7New description: §f${parseMiniMessageForDisplay(description) ?: "§oCleared"}")

            // Refresh guild data
            guild = guildService.getGuild(guild.id) ?: guild

            // Go back to previous menu
            menuNavigator.goBack()
        } else {
            player.sendMessage("§c❌ Failed to update guild description")
        }
    }

    private fun validateDescription(description: String?): String? {
        if (description == null) return null

        if (description.length > 100) {
            return "Description too long (${description.length}/100 characters)"
        }

        // Try to parse with MiniMessage to check for errors
        try {
            val miniMessage = MiniMessage.miniMessage()
            miniMessage.deserialize(description)
        } catch (e: Exception) {
            return "Invalid MiniMessage format: ${e.message}"
        }

        return null
    }

    private fun startChatInput() {
        player.sendMessage("§6=== Guild Description Editor ===")
        player.sendMessage("§7Type your new guild description in chat")
        player.sendMessage("§7Supports MiniMessage formatting (e.g., <red>text</red>)")
        player.sendMessage("§7Maximum 100 characters")
        player.sendMessage("§7Type 'cancel' to cancel editing")

        chatInputListener.startInputMode(player, this)

        // Close the menu when entering input mode
        player.closeInventory()
    }

    override fun onChatInput(player: Player, input: String) {
        if (input.lowercase() == "cancel") {
            player.sendMessage("§7Description editing cancelled")
            open() // Reopen menu
            return
        }

        setInputDescription(input)
        open() // Reopen menu with updated input
    }

    override fun onCancel(player: Player) {
        player.sendMessage("§7Description editing cancelled")
        open() // Reopen menu
    }

    private fun setInputDescription(description: String?) {
        inputDescription = description
        validationError = validateDescription(description)
    }

    private fun parseMiniMessageForDisplay(description: String?): String? {
        if (description == null) return null
        return try {
            val miniMessage = MiniMessage.miniMessage()
            val component = miniMessage.deserialize(description)
            val plainText = PlainTextComponentSerializer.plainText().serialize(component)
            plainText
        } catch (e: Exception) {
            description // Fallback to raw text if parsing fails
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
