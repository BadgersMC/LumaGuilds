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
import net.lumalyte.lg.utils.AntiDupeUtil
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
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class DescriptionEditorMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                           private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent, ChatInputHandler {

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

        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Edit Guild Description"))
        val pane = StaticPane(0, 0, 9, 4)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        // Current description display
        addCurrentDescriptionDisplay(pane, 4, 0)

        // Input field
        addInputField(pane, 1, 1)

        // Templates
        addTemplatesSection(pane, 3, 1)

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
            .setAdventureName(player, messageService, "<yellow>Current Description")
            .lore("<gray>${parseMiniMessageForDisplay(currentDescription) ?: "<italic>None set"}")
            .lore("")
            .addAdventureLore(player, messageService, "<gray>This is the description that")
            .addAdventureLore(player, messageService, "<gray>is currently displayed for your guild")

        pane.addItem(GuiItem(displayItem), x, y)
    }

    private fun addInputField(pane: StaticPane, x: Int, y: Int) {
        val inputItem = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<aqua>Description Input")
            .addAdventureLore(player, messageService, "<gray>Click to type your new description")
            .lore("")
            .addAdventureLore(player, messageService, "<gray>Current input:")
            .lore("<white>${inputDescription ?: "<italic>None"}")
            .lore("")
            .addAdventureLore(player, messageService, "<gray>Supports MiniMessage formatting")
            .addAdventureLore(player, messageService, "<gray>Max 100 characters")

        val guiItem = GuiItem(inputItem) {
            // Start chat input for description
            startChatInput()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addValidationStatus(pane: StaticPane, x: Int, y: Int) {
        val statusItem = if (validationError != null) {
            ItemStack(Material.RED_CONCRETE)
                .setAdventureName(player, messageService, "<red>❌ Validation Error")
                .addAdventureLore(player, messageService, "<gray>${validationError}")
        } else {
            ItemStack(Material.GREEN_CONCRETE)
                .setAdventureName(player, messageService, "<green>✅ Valid Description")
                .addAdventureLore(player, messageService, "<gray>Description is ready to save")
        }

        pane.addItem(GuiItem(statusItem), x, y)
    }

    private fun addSaveButton(pane: StaticPane, x: Int, y: Int) {
        val saveItem = ItemStack(Material.EMERALD_BLOCK)
            .setAdventureName(player, messageService, "<green>💾 Save Description")
            .addAdventureLore(player, messageService, "<gray>Save your changes")
            .lore("")
            .addAdventureLore(player, messageService, "<gray>This will update the guild description")
            .addAdventureLore(player, messageService, "<gray>for all members to see")

        val canSave = validationError == null && inputDescription != currentDescription
        if (!canSave) {
            saveItem.setAdventureName(player, messageService, "<gray>💾 Save Description")
                .addAdventureLore(player, messageService, "<gray>Save your changes")
                .lore("")
                .addAdventureLore(player, messageService, "<red>❌ Cannot save - check validation")
        }

        val guiItem = GuiItem(saveItem) {
            if (canSave) {
                saveDescription()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Cannot save description - please check validation errors")
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack(Material.REDSTONE_BLOCK)
            .setAdventureName(player, messageService, "<red>❌ Cancel")
            .addAdventureLore(player, messageService, "<gray>Discard changes and go back")

        val guiItem = GuiItem(cancelItem) {
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPreview(pane: StaticPane, x: Int, y: Int) {
        val previewItem = ItemStack(Material.ITEM_FRAME)
            .setAdventureName(player, messageService, "<light_purple>🔍 Preview")
            .addAdventureLore(player, messageService, "<gray>How your description will appear:")

        if (inputDescription != null && validationError == null) {
            try {
                val miniMessage = MiniMessage.miniMessage()
                val component = miniMessage.deserialize(inputDescription!!)
                val plainText = PlainTextComponentSerializer.plainText().serialize(component)

                previewItem.lore("<white>\"${plainText}\"")
            } catch (e: Exception) {
                previewItem.addAdventureLore(player, messageService, "<red>Error parsing description")
            }
        } else {
            previewItem.addAdventureLore(player, messageService, "<gray><italic>Enter a description to see preview")
        }

        pane.addItem(GuiItem(previewItem), x, y)
    }

    private fun saveDescription() {
        val description = inputDescription

        // Check permission
        if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_DESCRIPTION)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to manage guild description")
            return
        }

        // Save the description
        val success = guildService.setDescription(guild.id, description, player.uniqueId)

        if (success) {
            AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Guild description updated successfully!")
            player.sendMessage("<gray>New description: <white>${parseMiniMessageForDisplay(description) ?: "<italic>Cleared"}")

            // Refresh guild data
            guild = guildService.getGuild(guild.id) ?: guild

            // Go back to previous menu
            menuNavigator.goBack()
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to update guild description")
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

        // Check for inappropriate content
        if (!guildService.isGuildDescriptionAppropriate(description)) {
            return "Description contains inappropriate content"
        }

        return null
    }

    private fun startChatInput() {
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>=== Guild Description Editor ===")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type your new guild description in chat")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Supports MiniMessage formatting (e.g., <red>text</red>)")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Maximum 100 characters")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type 'cancel' to cancel editing")

        chatInputListener.startInputMode(player, this)

        // Close the menu when entering input mode
        player.closeInventory()
    }

    override fun onChatInput(player: Player, input: String) {
        if (input.lowercase() == "cancel") {
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Description editing cancelled")
            open() // Reopen menu
            return
        }

        setInputDescription(input)
        open() // Reopen menu with updated input
    }

    override fun onCancel(player: Player) {
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Description editing cancelled")
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

    /**
     * Add templates section to the menu
     */
    private fun addTemplatesSection(pane: StaticPane, x: Int, y: Int) {
        val templatesItem = ItemStack(Material.BOOKSHELF)
            .setAdventureName(player, messageService, "<gold>📚 Description Templates")
            .addAdventureLore(player, messageService, "<gray>Quick templates to get started")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Templates available:")
            .addAdventureLore(player, messageService, "<gray>• Basic Guild Info")
            .addAdventureLore(player, messageService, "<gray>• Roleplay Guild")
            .addAdventureLore(player, messageService, "<gray>• Competitive Guild")
            .addAdventureLore(player, messageService, "<gray>• Social Guild")
            .addAdventureLore(player, messageService, "<gray>• Custom Template")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click to browse templates")

        val guiItem = GuiItem(templatesItem) {
            openTemplatesMenu()
        }
        pane.addItem(guiItem, x, y)
    }

    /**
     * Open templates selection menu
     */
    private fun openTemplatesMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Description Templates"))
        val pane = StaticPane(0, 0, 9, 4)
        AntiDupeUtil.protect(gui)

        var currentRow = 0
        var currentCol = 0

        val templates = getDescriptionTemplates()

        templates.forEach { template ->
            val templateItem = ItemStack(Material.BOOK)
                .setAdventureName(player, messageService, "<white>${template.name}")
                .addAdventureLore(player, messageService, "<gray>${template.description}")
                .addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<yellow>Click to use this template")

            val guiItem = GuiItem(templateItem) {
                inputDescription = template.content
                validationError = validateDescription(template.content)
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Applied template: ${template.name}")
                open() // Return to main menu
            }
            pane.addItem(guiItem, currentCol, currentRow)

            currentCol++
            if (currentCol >= 9) {
                currentCol = 0
                currentRow++
            }
        }

        // Back button
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<red>Back to Editor")
            .addAdventureLore(player, messageService, "<gray>Return to description editor")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(this)
        }
        pane.addItem(backGuiItem, 8, 3)

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Get available description templates
     */
    private fun getDescriptionTemplates(): List<DescriptionTemplate> {
        return listOf(
            DescriptionTemplate(
                name = "Basic Guild Info",
                description = "Simple guild information template",
                content = "<gray>A friendly guild focused on <gold>community</gold> and <green>cooperation</green>."
            ),
            DescriptionTemplate(
                name = "Roleplay Guild",
                description = "For roleplaying servers",
                content = "<dark_purple>🏰 The Ancient Order 🏰</dark_purple>\n<gray>Guardians of forgotten lore and mystical arts."
            ),
            DescriptionTemplate(
                name = "Competitive Guild",
                description = "For competitive gameplay",
                content = "<red>⚔️ Elite Warriors ⚔️</red>\n<gray>Conquerors of realms, masters of combat."
            ),
            DescriptionTemplate(
                name = "Social Guild",
                description = "For social and casual play",
                content = "<aqua>🌟 Harmony Collective 🌟</aqua>\n<gray>Where friends gather and adventures begin!"
            ),
            DescriptionTemplate(
                name = "Custom Template",
                description = "Start with a blank template",
                content = ""
            )
        )
    }

    /**
     * Description template data class
     */
    private data class DescriptionTemplate(
        val name: String,
        val description: String,
        val content: String
    )
}
