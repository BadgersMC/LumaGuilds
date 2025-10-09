package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
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

class TagEditorMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                   private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent, ChatInputHandler {

    private val guildService: GuildService by inject()
    private val menuItemBuilder: MenuItemBuilder by inject()
    private val chatInputListener: ChatInputListener by inject()

    // State for the tag input
    private var currentTag: String? = null
    private var inputTag: String? = null
    private var validationError: String? = null

    override fun open() {
        println("[LumaGuilds] TagEditorMenu: Opening menu for player ${player.name}")

        // Load current tag (only if not already loaded)
        if (currentTag == null) {
            currentTag = guildService.getTag(guild.id)
            println("[LumaGuilds] TagEditorMenu: Loaded currentTag from database: '$currentTag'")
        } else {
            println("[LumaGuilds] TagEditorMenu: Using existing currentTag: '$currentTag'")
        }

        // Initialize inputTag only if it's null (preserve user input)
        if (inputTag == null) {
            inputTag = currentTag
            println("[LumaGuilds] TagEditorMenu: Initialized inputTag to currentTag: '$inputTag'")
        } else {
            println("[LumaGuilds] TagEditorMenu: Preserving existing inputTag: '$inputTag'")
        }

        // Initialize validation state
        val currentInput = inputTag
        if (currentInput != null) {
            validationError = validateTag(currentInput)
            println("[LumaGuilds] TagEditorMenu: Validation result: ${validationError ?: "VALID"}")
        }

        // Create 3x9 chest GUI
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Tag Editor - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 3)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 0: Current tag display
        addCurrentTagDisplay(pane, 0, 0)
        addTagStatusIndicator(pane, 4, 0)

        // Row 1: Input and preview
        addTagInputField(pane, 0, 1)
        addPreviewSection(pane, 4, 1)

        // Row 2: Action buttons
        addSaveButton(pane, 2, 2)
        addClearButton(pane, 4, 2)
        addCancelButton(pane, 6, 2)

        gui.show(player)
    }

    private fun addCurrentTagDisplay(pane: StaticPane, x: Int, y: Int) {
        val currentTagDisplay = ItemStack(Material.NAME_TAG)
            .setAdventureName(player, messageService, "<white>🎯 CURRENT TAG")
            .addAdventureLore(player, messageService, "<gray>Guild: <white>${guild.name}")

        if (currentTag != null) {
            val currentTagValue: String = currentTag!!
            val formattedTag = renderFormattedTag(currentTagValue)
            currentTagDisplay.addAdventureLore(player, messageService, "<gray>Tag: $formattedTag")
                .addAdventureLore(player, messageService, "<gray>This tag appears in chat")
        } else {
            currentTagDisplay.addAdventureLore(player, messageService, "<gray>Tag: <red>(Not set - using guild name)")
                .addAdventureLore(player, messageService, "<gray>Click edit to create a custom tag")
        }

        val guiItem = GuiItem(currentTagDisplay) {
            // Display only - no action needed
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTagStatusIndicator(pane: StaticPane, x: Int, y: Int) {
        val characterCount = if (inputTag != null) countVisibleCharacters(inputTag!!) else 0
        val statusItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<green>📊 TAG STATUS")
            .addAdventureLore(player, messageService, "<gray>Characters: <white>$characterCount<gray>/32")

        if (characterCount > 32) {
            statusItem.setAdventureName(player, messageService, "<red>❌ TAG TOO LONG")
                .addAdventureLore(player, messageService, "<red>Characters: <white>$characterCount<red>/32")
                .addAdventureLore(player, messageService, "<red>Reduce length to save")
        } else if (characterCount > 28) {
            statusItem.setAdventureName(player, messageService, "<yellow>⚠️ TAG NEARLY FULL")
                .addAdventureLore(player, messageService, "<gray>Characters: <white>$characterCount<gray>/32")
                .addAdventureLore(player, messageService, "<yellow>Close to limit")
        } else {
            statusItem.setAdventureName(player, messageService, "<green>✅ TAG LENGTH OK")
                .addAdventureLore(player, messageService, "<gray>Characters: <white>$characterCount<gray>/32")
        }

        val guiItem = GuiItem(statusItem) {
            // Display only - no action needed
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTagInputField(pane: StaticPane, x: Int, y: Int) {
        val inputItem = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<white>✏️ EDIT TAG")
            .addAdventureLore(player, messageService, "<gray>Format: MiniMessage supported")
            .addAdventureLore(player, messageService, "<gray>Examples:")
            .addAdventureLore(player, messageService, "<gray>  <gradient:#FF0000:#00FF00>MyGuild</gradient>")
            .addAdventureLore(player, messageService, "<gray>  <#FF6B35>MyGuild</#FF6B35>")
            .addAdventureLore(player, messageService, "<gray>  <bold>MyGuild</bold>")

        val currentInput = inputTag ?: ""
        if (currentInput.isNotEmpty()) {
            val formattedInput = renderFormattedTag(currentInput)
            inputItem.addAdventureLore(player, messageService, "<gray>Current: $formattedInput")
        } else {
            inputItem.addAdventureLore(player, messageService, "<gray>Current: <white>(none)")
        }

        // Add validation status
        if (validationError != null) {
            inputItem.addAdventureLore(player, messageService, "<red>❌ $validationError")
        } else if (inputTag != null && inputTag!!.isNotEmpty()) {
            inputItem.addAdventureLore(player, messageService, "<green>✅ Format valid")
        }

        if (isInInputMode()) {
            inputItem.setAdventureName(player, messageService, "<yellow>⏳ WAITING FOR CHAT INPUT...")
                .addAdventureLore(player, messageService, "<gray>Type your tag in chat")
                .addAdventureLore(player, messageService, "<gray>Or click cancel to stop")
        } else {
            inputItem.addAdventureLore(player, messageService, "<gray>Click to enter tag in chat")
        }

        val guiItem = GuiItem(inputItem) {
            if (!isInInputMode()) {
                startChatInput()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Already waiting for chat input. Type your tag or click cancel.")
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPreviewSection(pane: StaticPane, x: Int, y: Int) {
        val previewTag = inputTag ?: guild.name
        val previewItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<green>🔍 PREVIEW")
            .addAdventureLore(player, messageService, "<gray>Chat message:")

        if (validationError != null) {
            // Show error state with unformatted tag
            previewItem.addAdventureLore(player, messageService, "<gray>[${player.name}] <red>$previewTag <gray>Hello!")
                .addAdventureLore(player, messageService, "<red>⚠️ Preview shows validation error")
        } else {
            // Show properly formatted tag using MiniMessage
            val formattedTag = renderFormattedTag(previewTag)
            previewItem.addAdventureLore(player, messageService, "<gray>[${player.name}] $formattedTag <gray>Hello!")

            if (inputTag != null && inputTag != currentTag) {
                previewItem.addAdventureLore(player, messageService, "<green>✅ Preview shows new tag")
            } else {
                previewItem.addAdventureLore(player, messageService, "<gray>Preview shows current tag")
            }
        }

        previewItem.addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>How it will appear in chat")

        val guiItem = GuiItem(previewItem) {
            // Preview only - no click action needed
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addSaveButton(pane: StaticPane, x: Int, y: Int) {
        val saveItem = ItemStack(Material.LIME_WOOL)
            .setAdventureName(player, messageService, "<green>✅ SAVE TAG")
            .addAdventureLore(player, messageService, "<gray>Apply the new tag")

        // Disable save if there are validation errors
        if (validationError != null) {
            saveItem.setAdventureName(player, messageService, "<red>❌ CANNOT SAVE")
                .addAdventureLore(player, messageService, "<red>Fix validation errors first")
        } else if (inputTag == currentTag) {
            saveItem.setAdventureName(player, messageService, "<gray>📝 NO CHANGES")
                .addAdventureLore(player, messageService, "<gray>Tag unchanged")
        } else {
            saveItem.addAdventureLore(player, messageService, "<gray>Click to save changes")
        }

        val guiItem = GuiItem(saveItem) {
            println("[LumaGuilds] TagEditorMenu: Save button clicked")
            println("[LumaGuilds] TagEditorMenu: currentTag: '$currentTag', inputTag: '$inputTag'")
            println("[LumaGuilds] TagEditorMenu: validationError: ${validationError ?: "NONE"}")

            if (validationError != null) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Cannot save: $validationError")
                return@GuiItem
            }

            if (inputTag == currentTag) {
                println("[LumaGuilds] TagEditorMenu: No changes detected - inputTag equals currentTag")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>No changes to save.")
                return@GuiItem
            }

            println("[LumaGuilds] TagEditorMenu: Changes detected, proceeding with save...")

            // Save the tag
            val success = guildService.setTag(guild.id, inputTag, player.uniqueId)
            if (success) {
                // Update local guild object
                currentTag = inputTag

                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Guild tag updated successfully!")
                player.sendMessage("<gray>New tag: ${inputTag ?: "§c(cleared)"}")

                // Refresh the menu to show updated state
                open()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to save tag. Check permissions.")
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addClearButton(pane: StaticPane, x: Int, y: Int) {
        val clearItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>🗑️ CLEAR TAG")
            .addAdventureLore(player, messageService, "<gray>Remove custom tag")
            .addAdventureLore(player, messageService, "<gray>Will use guild name instead")

        val guiItem = GuiItem(clearItem) {
            inputTag = null
            validationError = null

            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Tag cleared. Will use guild name instead.")

            // Refresh the menu to show updated state
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack(Material.RED_WOOL)
            .setAdventureName(player, messageService, "<red>❌ CANCEL")
            .addAdventureLore(player, messageService, "<gray>Discard changes")

        if (isInInputMode()) {
            cancelItem.setAdventureName(player, messageService, "<red>⏹️ CANCEL INPUT")
                .addAdventureLore(player, messageService, "<gray>Stop waiting for chat input")
        }

        val guiItem = GuiItem(cancelItem) {
            if (isInInputMode()) {
                chatInputListener.stopInputMode(player)
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>Tag input cancelled.")
                // Reopen menu to refresh state
                open()
            } else {
                // Discard changes and return to previous menu
                menuNavigator.goBack()
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun startChatInput() {
        println("[LumaGuilds] TagEditorMenu: Starting chat input for player ${player.name}")


        chatInputListener.startInputMode(player, this)

        // Close the menu when entering input mode
        player.closeInventory()

        AdventureMenuHelper.sendMessage(player, messageService, "<gold>=== TAG INPUT MODE ===")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type your guild tag in chat.")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Supports MiniMessage formatting:")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>  Colors: <#FF0000>Text</#FF0000>")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>  Gradients: <gradient:#FF0000:#00FF00>Text</gradient>")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>  Formatting: <bold>, <italic>, etc.")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Character limit: 32 visible characters")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type 'cancel' to stop input mode")
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>=====================")
    }


    private fun validateTag(tag: String): String? {
        // Basic validation - more advanced validation will be added later
        val visibleChars = countVisibleCharacters(tag)
        if (visibleChars > 32) {
            return "Tag too long ($visibleChars/32 characters)"
        }

        // TODO: Add MiniMessage format validation
        return null
    }

    private fun countVisibleCharacters(tag: String): Int {
        return try {
            // Parse MiniMessage to get the actual formatted component
            val miniMessage = MiniMessage.miniMessage()
            val component = miniMessage.deserialize(tag)

            // Convert to plain text to get visible characters only
            val plainTextSerializer = PlainTextComponentSerializer.plainText()
            val plainText = plainTextSerializer.serialize(component)

            // Count the actual visible characters
            plainText.length
        } catch (e: Exception) {
            // Fallback to regex approach if MiniMessage parsing fails
            val withoutTags = tag
                .replace(Regex("<[^>]*>"), "")  // Remove all <tag> elements
                .replace(Regex("&[0-9a-fk-or]"), "")  // Remove legacy color codes
                .replace(Regex("§[0-9a-fk-or]"), "")  // Remove section sign color codes
            withoutTags.length
        }
    }

    private fun renderFormattedTag(tag: String): String {
        return try {
            // Parse MiniMessage and convert to legacy format for menu display
            val miniMessage = MiniMessage.miniMessage()
            val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

            val component = miniMessage.deserialize(tag)
            legacySerializer.serialize(component)
        } catch (e: Exception) {
            // Fallback to plain text if MiniMessage parsing fails
            tag
        }
    }

    fun setInputTag(tag: String?, messageService: MessageService) {
        println("[LumaGuilds] TagEditorMenu: setInputTag called with: '$tag'")
        inputTag = tag
        validationError = if (tag != null) validateTag(tag) else null
        println("[LumaGuilds] TagEditorMenu: Updated inputTag to: '$inputTag', validationError: ${validationError ?: "NONE"}")
    }

    fun getInputTag(): String? = inputTag

    fun isInInputMode(): Boolean = chatInputListener.isInInputMode(player)// ChatInputHandler interface methods
    override fun onChatInput(player: Player, input: String) {
        println("[LumaGuilds] TagEditorMenu: Received tag input: '$input'")

        // Validate the input
        val error = validateTag(input)
        if (error != null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Invalid tag: $error")
            return
        }

        // Set the input tag
        inputTag = input

        // Reopen the menu with the new input
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds")!!
        Bukkit.getScheduler().runTask(plugin, Runnable {
            open()
        })

        AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Tag set to: '$input'")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Click save to apply the changes.")
    }

    override fun onCancel(player: Player) {
        println("[LumaGuilds] TagEditorMenu: Player cancelled tag input")   
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Tag input cancelled.")

        // Reopen the menu without changes
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds")!!
        Bukkit.getScheduler().runTask(plugin, Runnable {
            open()
        })
    }   
}
