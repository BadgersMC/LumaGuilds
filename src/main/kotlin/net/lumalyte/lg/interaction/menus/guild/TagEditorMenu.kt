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
import net.lumalyte.lg.utils.MenuItemBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.lumalyte.lg.utils.ColorCodeUtils
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

class TagEditorMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                   private var guild: Guild): Menu, KoinComponent, ChatInputHandler {

    private val guildService: GuildService by inject()
    private val menuItemBuilder: MenuItemBuilder by inject()
    private val chatInputListener: ChatInputListener by inject()

    // State for the tag input
    private var currentTag: String? = null
    private var inputTag: String? = null
    private var validationError: String? = null
    private var inputInitialized: Boolean = false

    override fun open() {
        println("[LumaGuilds] TagEditorMenu: Opening menu for player ${player.name}")

        // Load current tag (only if not already loaded)
        if (currentTag == null) {
            currentTag = guildService.getTag(guild.id)
            println("[LumaGuilds] TagEditorMenu: Loaded currentTag from database: '$currentTag'")
        } else {
            println("[LumaGuilds] TagEditorMenu: Using existing currentTag: '$currentTag'")
        }

        // Initialize inputTag from currentTag on first open. After that, preserve
        // user state — including an explicit clear (inputTag == null) — across
        // re-opens triggered by button clicks.
        if (!inputInitialized) {
            inputTag = currentTag
            inputInitialized = true
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
        val gui = ChestGui(3, "§6Tag Editor - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
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
        val currentTagDisplay = ItemStack.of(Material.NAME_TAG)
            .name("§f🎯 CURRENT TAG")
            .lore("§7Guild: §f${guild.name}")

        currentTag?.let { tagValue ->
            val formattedTag = renderFormattedTag(tagValue)
            currentTagDisplay.lore("§7Tag: $formattedTag")
                .lore("§7This tag appears in chat")
        } ?: run {
            currentTagDisplay.lore("§7Tag: §c(Not set - using guild name)")
                .lore("§7Click edit to create a custom tag")
        }

        val guiItem = GuiItem(currentTagDisplay) {
            // Display only - no action needed
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTagStatusIndicator(pane: StaticPane, x: Int, y: Int) {
        val characterCount = inputTag?.let { countVisibleCharacters(it) } ?: 0
        val statusItem = ItemStack.of(Material.PAPER)
            .name("§a📊 TAG STATUS")
            .lore("§7Characters: §f$characterCount§7/32")

        if (characterCount > 32) {
            statusItem.name("§c❌ TAG TOO LONG")
                .lore("§cCharacters: §f$characterCount§c/32")
                .lore("§cReduce length to save")
        } else if (characterCount > 28) {
            statusItem.name("§e⚠ TAG NEARLY FULL")
                .lore("§7Characters: §f$characterCount§7/32")
                .lore("§eClose to limit")
        } else {
            statusItem.name("§a✅ TAG LENGTH OK")
                .lore("§7Characters: §f$characterCount§7/32")
        }

        val guiItem = GuiItem(statusItem) {
            // Display only - no action needed
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTagInputField(pane: StaticPane, x: Int, y: Int) {
        val inputItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name("§f✏ EDIT TAG")
            .lore("§7Format: MiniMessage supported")
            .lore("§7Examples:")
            .lore("§7  <gradient:#FF0000:#00FF00>MyGuild</gradient>")
            .lore("§7  <#FF6B35>MyGuild</#FF6B35>")
            .lore("§7  <bold>MyGuild</bold>")

        val currentInput = inputTag ?: ""
        if (currentInput.isNotEmpty()) {
            val formattedInput = renderFormattedTag(currentInput)
            inputItem.lore("§7Current: $formattedInput")
        } else {
            inputItem.lore("§7Current: §f(none)")
        }

        // Add validation status
        if (validationError != null) {
            inputItem.lore("§c❌ $validationError")
        } else if (inputTag?.isNotEmpty() == true) {
            inputItem.lore("§a✅ Format valid")
        }

        if (isInInputMode()) {
            inputItem.name("§e⏳ WAITING FOR CHAT INPUT...")
                .lore("§7Type your tag in chat")
                .lore("§7Or click cancel to stop")
        } else {
            inputItem.lore("§7Click to enter tag in chat")
        }

        val guiItem = GuiItem(inputItem) {
            if (!isInInputMode()) {
                startChatInput()
            } else {
                player.sendMessage("§eAlready waiting for chat input. Type your tag or click cancel.")
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPreviewSection(pane: StaticPane, x: Int, y: Int) {
        val previewTag = inputTag ?: guild.name
        val previewItem = ItemStack.of(Material.PAPER)
            .name("§a🔍 PREVIEW")
            .lore("§7Chat message:")

        if (validationError != null) {
            // Show error state with unformatted tag
            previewItem.lore("§7[${player.name}] §c$previewTag §7Hello!")
                .lore("§c⚠ Preview shows validation error")
        } else {
            // Show properly formatted tag using MiniMessage
            val formattedTag = renderFormattedTag(previewTag)
            previewItem.lore("§7[${player.name}] $formattedTag §7Hello!")

            if (inputTag != null && inputTag != currentTag) {
                previewItem.lore("§a✅ Preview shows new tag")
            } else {
                previewItem.lore("§7Preview shows current tag")
            }
        }

        previewItem.lore("§7")
            .lore("§7How it will appear in chat")

        val guiItem = GuiItem(previewItem) {
            // Preview only - no click action needed
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addSaveButton(pane: StaticPane, x: Int, y: Int) {
        val saveItem = ItemStack.of(Material.LIME_WOOL)
            .name("§a✅ SAVE TAG")
            .lore("§7Apply the new tag")

        // Disable save if there are validation errors
        if (validationError != null) {
            saveItem.name("§c❌ CANNOT SAVE")
                .lore("§cFix validation errors first")
        } else if (inputTag == currentTag) {
            saveItem.name("§7📝 NO CHANGES")
                .lore("§7Tag unchanged")
        } else {
            saveItem.lore("§7Click to save changes")
        }

        val guiItem = GuiItem(saveItem) {
            println("[LumaGuilds] TagEditorMenu: Save button clicked")
            println("[LumaGuilds] TagEditorMenu: currentTag: '$currentTag', inputTag: '$inputTag'")
            println("[LumaGuilds] TagEditorMenu: validationError: ${validationError ?: "NONE"}")

            if (validationError != null) {
                player.sendMessage("§c❌ Cannot save: $validationError")
                return@GuiItem
            }

            if (inputTag == currentTag) {
                println("[LumaGuilds] TagEditorMenu: No changes detected - inputTag equals currentTag")
                player.sendMessage("§7No changes to save.")
                return@GuiItem
            }

            println("[LumaGuilds] TagEditorMenu: Changes detected, proceeding with save...")

            // Convert legacy & codes to MiniMessage format before saving
            val tagToSave = inputTag?.let { ColorCodeUtils.convertLegacyToMiniMessage(it) }

            // Save the tag (now in MiniMessage format)
            val success = guildService.setTag(guild.id, tagToSave, player.uniqueId)
            if (success) {
                // Update local guild object
                currentTag = tagToSave

                player.sendMessage("§a✅ Guild tag updated successfully!")
                if (tagToSave != null) {
                    val displayTag = ColorCodeUtils.renderTagForDisplay(tagToSave)
                    player.sendMessage("§7New tag: $displayTag")
                } else {
                    player.sendMessage("§7New tag: §c(cleared)")
                }

                // Refresh the menu to show updated state
                open()
            } else {
                player.sendMessage("§c❌ Failed to save tag. Check permissions.")
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addClearButton(pane: StaticPane, x: Int, y: Int) {
        val clearItem = ItemStack.of(Material.BARRIER)
            .name("§c🗑 CLEAR TAG")
            .lore("§7Remove custom tag")
            .lore("§7Will use guild name instead")

        val guiItem = GuiItem(clearItem) {
            inputTag = null
            validationError = null

            player.sendMessage("§7Tag cleared. Will use guild name instead.")

            // Refresh the menu to show updated state
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack.of(Material.RED_WOOL)
            .name("§c❌ CANCEL")
            .lore("§7Discard changes")

        if (isInInputMode()) {
            cancelItem.name("§c⏹ CANCEL INPUT")
                .lore("§7Stop waiting for chat input")
        }

        val guiItem = GuiItem(cancelItem) {
            if (isInInputMode()) {
                chatInputListener.stopInputMode(player)
                player.sendMessage("§7Tag input cancelled.")
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

        player.sendMessage("§6=== TAG INPUT MODE ===")
        player.sendMessage("§7Type your guild tag in chat.")
        player.sendMessage("§7Supports both legacy & and MiniMessage:")
        player.sendMessage("§7  Legacy: &c&lRed Bold")
        player.sendMessage("§7  Colors: <#FF0000>Text</#FF0000>")
        player.sendMessage("§7  Gradients: <gradient:#FF0000:#00FF00>Text</gradient>")
        player.sendMessage("§7  Formatting: <bold>, <italic>, etc.")
        player.sendMessage("§7Character limit: 32 visible characters")
        player.sendMessage("§7Type 'cancel' to stop input mode")
        player.sendMessage("§6=====================")
    }


    private fun validateTag(tag: String): String? {
        // Length validation
        val visibleChars = countVisibleCharacters(tag)
        if (visibleChars > 32) {
            return "Tag too long ($visibleChars/32 characters)"
        }

        if (tag.trim().isEmpty()) {
            return "Tag cannot be empty"
        }

        // MiniMessage format validation
        // Check for balanced tags
        val openTags = Regex("<([^/>][^>]*)>").findAll(tag).count()
        val closeTags = Regex("</[^>]+>").findAll(tag).count()

        // Check for common syntax errors
        if (tag.contains("<<") || tag.contains(">>")) {
            return "Invalid tag syntax: double brackets"
        }

        // Try to parse with MiniMessage
        try {
            val miniMessage = MiniMessage.miniMessage()
            miniMessage.deserialize(tag)
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            // Parse the error message to provide helpful feedback
            val errorMsg = e.message ?: "Invalid format"
            return when {
                errorMsg.contains("unclosed", ignoreCase = true) ->
                    "Unclosed tag (missing closing tag)"
                errorMsg.contains("unknown tag", ignoreCase = true) ->
                    "Unknown tag format"
                errorMsg.contains("invalid", ignoreCase = true) ->
                    "Invalid MiniMessage syntax"
                else -> "Format error: ${errorMsg.take(50)}"
            }
        }

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
            // Menu operation - catching all exceptions to prevent UI failure
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
            // Menu operation - catching all exceptions to prevent UI failure
            // Fallback to plain text if MiniMessage parsing fails
            tag
        }
    }

    fun setInputTag(tag: String?) {
        println("[LumaGuilds] TagEditorMenu: setInputTag called with: '$tag'")
        inputTag = tag
        validationError = if (tag != null) validateTag(tag) else null
        println("[LumaGuilds] TagEditorMenu: Updated inputTag to: '$inputTag', validationError: ${validationError ?: "NONE"}")
    }

    fun getInputTag(): String? = inputTag

    fun isInInputMode(): Boolean = chatInputListener.isInInputMode(player)

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }

    // ChatInputHandler interface methods
    override fun onChatInput(player: Player, input: String) {
        println("[LumaGuilds] TagEditorMenu: Received tag input: '$input'")

        // Validate the input
        val error = validateTag(input)
        if (error != null) {
            player.sendMessage("§c❌ Invalid tag: $error")
            return
        }

        // Set the input tag
        setInputTag(input)

        // Reopen the menu with the new input
        Bukkit.getScheduler().runTask(net.lumalyte.lg.common.PluginKeys.getPlugin(), Runnable {
            open()
        })

        // Show formatted tag in message
        val displayTag = ColorCodeUtils.renderTagForDisplay(input)
        player.sendMessage("§a✅ Tag set to: $displayTag")
        player.sendMessage("§7Click save to apply the changes.")
    }

    override fun onCancel(player: Player) {
        println("[LumaGuilds] TagEditorMenu: Player cancelled tag input")   
        player.sendMessage("§7Tag input cancelled.")

        // Reopen the menu without changes
        Bukkit.getScheduler().runTask(net.lumalyte.lg.common.PluginKeys.getPlugin(), Runnable {
            open()
        })
    }   
}
