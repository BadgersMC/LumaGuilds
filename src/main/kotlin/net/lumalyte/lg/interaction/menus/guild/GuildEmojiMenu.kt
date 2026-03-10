package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuItemBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuildEmojiMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                    private var guild: Guild): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val menuItemBuilder: MenuItemBuilder by inject()
    private val nexoEmojiService: NexoEmojiService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    init {
        // Run validation test on initialization (for development/testing)
        if (System.getProperty("bellclaims.emoji.test") == "true") {
            testEmojiValidation()
        }
    }

    // State for the emoji input
    private var currentEmoji: String? = null
    private var inputEmoji: String? = null
    private var validationError: String? = null

    override fun open() {
        println("[LumaGuilds] GuildEmojiMenu: Opening menu for player ${player.name}")

        // Load current emoji (only if not already loaded)
        if (currentEmoji == null) {
            currentEmoji = guildService.getEmoji(guild.id)
            println("[LumaGuilds] GuildEmojiMenu: Loaded currentEmoji from database: '$currentEmoji'")
        } else {
            println("[LumaGuilds] GuildEmojiMenu: Using existing currentEmoji: '$currentEmoji'")
        }

        // Initialize inputEmoji only if it's null (preserve user input)
        if (inputEmoji == null) {
            inputEmoji = currentEmoji
            println("[LumaGuilds] GuildEmojiMenu: Initialized inputEmoji to currentEmoji: '$inputEmoji'")
        } else {
            println("[LumaGuilds] GuildEmojiMenu: Preserving existing inputEmoji: '$inputEmoji'")
        }

        // Initialize validation state
        val currentInput = inputEmoji
        println("[LumaGuilds] GuildEmojiMenu: Validating emoji: '$currentInput'")
        validationError = if (currentInput == null || currentInput.isBlank()) {
            println("[LumaGuilds] GuildEmojiMenu: Empty/null emoji - treating as valid")
            null // Empty is valid (clears emoji)
        } else if (!nexoEmojiService.doesEmojiExist(currentInput)) {
            println("[LumaGuilds] GuildEmojiMenu: Emoji validation FAILED - not found in registry")
            "Emoji not found in Nexo registry"
        } else {
            println("[LumaGuilds] GuildEmojiMenu: Emoji validation PASSED")
            null // Valid
        }
        println("[LumaGuilds] GuildEmojiMenu: Final validation result: ${validationError ?: "VALID"}")

        val gui = ChestGui(3, "§6Guild Emoji - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }
        gui.addPane(pane)

        // Row 1: Current emoji display and Nexo status
        addCurrentEmojiDisplay(pane, 0, 0)
        addNexoStatusIndicator(pane, 4, 0)

        // Row 2: Input field and preview
        addEmojiInputField(pane, 0, 1)
        addEmojiSelectorButton(pane, 3, 1)
        addPreviewSection(pane, 4, 1)

        // Row 3: Action buttons
        addSaveButton(pane, 3, 2)
        addClearButton(pane, 4, 2)
        addCancelButton(pane, 5, 2)
        addBackButton(pane, 8, 2)

        gui.show(player)
    }

    private fun addCurrentEmojiDisplay(pane: StaticPane, x: Int, y: Int) {
        val currentEmojiText = currentEmoji ?: "§cNot set"
        val displayItem = ItemStack.of(Material.NAME_TAG)
            .name("§e🎨 CURRENT EMOJI")
            .lore("§7$currentEmojiText")
            .lore("§7")
            .lore("§7This emoji appears in guild chat")

        val guiItem = GuiItem(displayItem) {
            // Display only - no click action needed
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addNexoStatusIndicator(pane: StaticPane, x: Int, y: Int) {
        val isNexoAvailable = nexoEmojiService.isNexoAvailable()
        val statusText = nexoEmojiService.getNexoStatusDescription()

        val statusItem = if (isNexoAvailable) {
            ItemStack.of(Material.LIME_WOOL)
                .name("§a✅ NEXO: Available")
                .lore("§7Nexo plugin detected")
                .lore("§7Full emoji validation active")
                .lore("§7Emojis must be configured in Nexo")
        } else {
            ItemStack.of(Material.RED_WOOL)
                .name("§c❌ NEXO: Unavailable")
                .lore("§7Nexo plugin not found")
                .lore("§7Format-only validation active")
                .lore("§7Contact admin to install Nexo")
                .lore("§7for full emoji functionality")
        }

        val guiItem = GuiItem(statusItem) {
            // Status display only
            player.sendMessage("§7$statusText")
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addEmojiInputField(pane: StaticPane, x: Int, y: Int) {
        println("[LumaGuilds] GuildEmojiMenu: Adding emoji input field with current input: '$inputEmoji'")
        val inputItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name("§f✏ SET NEW EMOJI")
            .lore("§7Format: :emoji_name:")
            .lore("§7Example: :cat:")
            .lore("§7Current input: ${inputEmoji ?: "§c(none)"}")

        // Add validation status
        if (validationError != null) {
            inputItem.lore("§c❌ $validationError")
        } else if (inputEmoji != null) {
            inputItem.lore("§a✅ Format valid")
        }

        inputItem.lore("§7Click to input emoji")

        val guiItem = GuiItem(inputItem) {
            println("[LumaGuilds] GuildEmojiMenu: Emoji input field clicked - starting chat input")
            // Start chat input mode for emoji
            chatInputListener.startInputMode(player, EmojiInputHandler(menuNavigator, player, guild, this))
            player.closeInventory()

            player.sendMessage("§e✏ Enter your guild emoji:")
            player.sendMessage("§7Format: §f:emoji_name: §7(e.g., §f:cat:§7)")
            player.sendMessage("§7Type §c'cancel' §7to cancel")
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addEmojiSelectorButton(pane: StaticPane, x: Int, y: Int) {
        val unlockedCount = nexoEmojiService.getPlayerUnlockedEmojis(player).size
        println("[LumaGuilds] GuildEmojiMenu: Player ${player.name} has $unlockedCount unlocked emojis")
        val selectorItem = ItemStack.of(Material.ENDER_CHEST)
            .name("§d🎨 SELECT FROM UNLOCKED")
            .lore("§7Browse emojis you have access to")
            .lore("§7Unlocked emojis: §f$unlockedCount")
            .lore("§7Click to open emoji selector")

        val guiItem = GuiItem(selectorItem) {
            println("[LumaGuilds] GuildEmojiMenu: Emoji selector button clicked")
            if (unlockedCount == 0) {
                player.sendMessage("§cYou don't have any unlocked emojis!")
                player.sendMessage("§7Contact an admin to get emoji permissions.")
            } else {
                println("[LumaGuilds] GuildEmojiMenu: Opening emoji selection menu")
                // Open emoji selection menu
                menuNavigator.openMenu(menuFactory.createEmojiSelectionMenu(menuNavigator, player, guild, this))
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPreviewSection(pane: StaticPane, x: Int, y: Int) {
        val previewEmoji = inputEmoji ?: ":cat:" // Default preview
        val previewItem = ItemStack.of(Material.PAPER)
            .name("§a🔍 PREVIEW")
            .lore("§7Guild Chat:")

        if (validationError != null) {
            previewItem.lore("§7[${player.name}] §c$previewEmoji §7Hello!")
                .lore("§c⚠ Preview shows validation error")
        } else {
            previewItem.lore("§7[${player.name}] $previewEmoji §7Hello!")
                .lore("§a✅ Preview shows valid format")
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
            .name("§a✅ SAVE CHANGES")
            .lore("§7Apply the new emoji")

        // Disable save if there are validation errors or no changes
        if (validationError != null) {
            saveItem.name("§c❌ CANNOT SAVE")
                .lore("§cFix validation errors first")
        } else if (inputEmoji == currentEmoji) {
            saveItem.name("§7📝 NO CHANGES")
                .lore("§7Emoji unchanged")
        } else {
            saveItem.lore("§7Click to save changes")
        }

        val guiItem = GuiItem(saveItem) {
            println("[LumaGuilds] GuildEmojiMenu: Save button clicked")
            println("[LumaGuilds] GuildEmojiMenu: currentEmoji: '$currentEmoji', inputEmoji: '$inputEmoji'")
            println("[LumaGuilds] GuildEmojiMenu: validationError: ${validationError ?: "NONE"}")

            if (validationError != null) {
                player.sendMessage("§c❌ Cannot save: $validationError")
                return@GuiItem
            }

            if (inputEmoji == currentEmoji) {
                println("[LumaGuilds] GuildEmojiMenu: No changes detected - inputEmoji equals currentEmoji")
                player.sendMessage("§7No changes to save.")
                return@GuiItem
            }

            println("[LumaGuilds] GuildEmojiMenu: Changes detected, proceeding with save...")

            // Save the emoji
            val success = guildService.setEmoji(guild.id, inputEmoji, player.uniqueId)
            if (success) {
                // Update local guild object
                currentEmoji = inputEmoji

                player.sendMessage("§a✅ Guild emoji updated successfully!")
                player.sendMessage("§7New emoji: ${inputEmoji ?: "§c(cleared)"}")

                // Refresh the menu to show updated state
                open()
            } else {
                player.sendMessage("§c❌ Failed to save emoji. Check permissions.")
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addClearButton(pane: StaticPane, x: Int, y: Int) {
        val clearItem = ItemStack.of(Material.ORANGE_WOOL)
            .name("§6🗑 CLEAR EMOJI")
            .lore("§7Remove guild emoji")
            .lore("§7Will use no emoji in chat")

        val guiItem = GuiItem(clearItem) {
            println("[LumaGuilds] GuildEmojiMenu: Clear button clicked")
            println("[LumaGuilds] GuildEmojiMenu: Setting inputEmoji to null")

            inputEmoji = null
            validationError = null

            player.sendMessage("§7Emoji cleared. Will use no emoji in chat.")

            // Refresh the menu to show updated state
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack.of(Material.RED_WOOL)
            .name("§c❌ CANCEL")
            .lore("§7Discard changes")

        val guiItem = GuiItem(cancelItem) {
            // Close menu without saving
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name("§e⬅ BACK")
            .lore("§7Return to guild settings")

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun validateEmojiFormat(emoji: String?): ValidationResult {
        if (emoji.isNullOrBlank()) {
            return ValidationResult.Valid // Empty is valid (clears emoji)
        }

        // Check format: must start and end with colon
        if (!emoji.startsWith(":") || !emoji.endsWith(":")) {
            return ValidationResult.Invalid("Must be in format :emoji_name:")
        }

        // Extract emoji name (remove colons)
        val emojiName = emoji.substring(1, emoji.length - 1)

        // Check if emoji name is empty
        if (emojiName.isBlank()) {
            return ValidationResult.Invalid("Emoji name cannot be empty")
        }

        // Check length (including colons)
        if (emoji.length > 50) {
            return ValidationResult.Invalid("Emoji too long (max 50 characters)")
        }

        // Basic sanitization - check for potentially problematic characters
        val problematicChars = setOf('<', '>', '"', '\'', '\\', '\n', '\r', '\t')
        if (emojiName.any { it in problematicChars }) {
            return ValidationResult.Invalid("Emoji contains invalid characters")
        }

        return ValidationResult.Valid
    }

    private fun setInputEmoji(emoji: String?) {
        println("[LumaGuilds] GuildEmojiMenu: setInputEmoji called with: '$emoji'")
        inputEmoji = emoji
        validationError = if (emoji == null || emoji.isBlank()) {
            null // Empty is valid (clears emoji)
        } else if (!nexoEmojiService.doesEmojiExist(emoji)) {
            "Emoji not found in Nexo registry"
        } else {
            null // Valid
        }
        println("[LumaGuilds] GuildEmojiMenu: Updated inputEmoji to: '$inputEmoji', validationError: ${validationError ?: "NONE"}")
    }

    /**
     * Public method to set emoji input (can be called from chat commands)
     * @param emoji The emoji string to validate and set
     * @return true if valid, false if invalid
     */
    fun setEmojiInput(emoji: String?): Boolean {
        setInputEmoji(emoji)
        return validationError == null
    }

    /**
     * Get the current validation error message
     * @return The error message, or null if valid
     */
    fun getValidationError(): String? = validationError

    /**
     * Check if the current input is valid
     * @return true if valid, false otherwise
     */
    fun isInputValid(): Boolean = validationError == null

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

/**
 * Handler for emoji chat input, similar to TagEditorMenu's approach
 */
private class EmojiInputHandler(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val emojiMenu: GuildEmojiMenu
) : ChatInputHandler {

    /**
     * Called when the player enters emoji input via chat
     */
    override fun onChatInput(player: Player, input: String) {
        println("[LumaGuilds] EmojiInputHandler: Received emoji input: '$input'")

        // Validate the input format
        if (!input.startsWith(":") || !input.endsWith(":")) {
            player.sendMessage("§c❌ Invalid format! Emoji must be in format :emoji_name:")
            player.sendMessage("§7Example: §f:cat:")
            return
        }

        // Set the emoji input in the menu
        emojiMenu.setEmojiInput(input)

        // Reopen the menu with the new input (reuse existing instance)
        val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
            emojiMenu.open()
        })

        player.sendMessage("§a✅ Emoji set to: $input")
        player.sendMessage("§7Click save to apply the changes.")
    }

    /**
     * Called when the player cancels emoji input
     */
    override fun onCancel(player: Player) {
        println("[LumaGuilds] EmojiInputHandler: Player cancelled emoji input")
        player.sendMessage("§7Emoji input cancelled.")

        // Reopen the menu without changes (reuse existing instance)
        val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
            emojiMenu.open()
        })
    }
}

// Sealed class for validation results
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}

/**
 * Menu for selecting emojis from the player's unlocked collection.
 * Shows all emojis the player has permission to use via LuckPerms.
 */
class EmojiSelectionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val parentMenu: GuildEmojiMenu
) : Menu, KoinComponent {

    private val nexoEmojiService: NexoEmojiService by inject()
    private val menuItemBuilder: MenuItemBuilder by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    // Pagination state
    private var currentPage = 0
    private val itemsPerPage = 45 // 9x5 grid (45 slots in a double chest)
    private lateinit var unlockedEmojis: List<String>

    override fun open() {
        // Get all unlocked emojis for this player
        unlockedEmojis = nexoEmojiService.getPlayerUnlockedEmojis(player)

        if (unlockedEmojis.isEmpty()) {
            player.sendMessage("§cYou don't have any unlocked emojis!")
            menuNavigator.openMenu(parentMenu)
            return
        }

        // Calculate total pages
        val totalPages = (unlockedEmojis.size + itemsPerPage - 1) / itemsPerPage

        // Create double chest GUI (6 rows x 9 columns = 54 slots)
        val gui = ChestGui(6, "§6Select Emoji - Page ${currentPage + 1}/$totalPages")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Add emoji items (5 rows of 9 items each)
        val startIndex = currentPage * itemsPerPage
        val endIndex = if (startIndex + itemsPerPage < unlockedEmojis.size) startIndex + itemsPerPage else unlockedEmojis.size

        for (i in startIndex until endIndex) {
            val emojiName = unlockedEmojis[i]
            val slotIndex = i - startIndex
            val row = slotIndex / 9
            val col = slotIndex % 9

            addEmojiItem(pane, emojiName, col, row)
        }

        // Add navigation buttons (bottom row)
        if (currentPage > 0) {
            addPreviousPageButton(pane, 2, 5)
        }
        if (currentPage < totalPages - 1) {
            addNextPageButton(pane, 6, 5)
        }

        addBackButton(pane, 4, 5)

        gui.show(player)
    }

    private fun addEmojiItem(pane: StaticPane, emojiName: String, x: Int, y: Int) {
        val emojiPlaceholder = ":$emojiName:"
        println("[LumaGuilds] GuildEmojiMenu: Adding emoji item: $emojiPlaceholder")
        val emojiItem = ItemStack.of(Material.PAPER)
            .name("§e$emojiPlaceholder")
            .lore("§7Click to select this emoji")
            .lore("§7This will become your guild emoji")

        val guiItem = GuiItem(emojiItem) {
            println("[LumaGuilds] GuildEmojiMenu: Emoji item clicked: $emojiPlaceholder")
            // Set the emoji in the parent menu and return to it
            parentMenu.setEmojiInput(emojiPlaceholder)
            println("[LumaGuilds] GuildEmojiMenu: Set emoji input to: $emojiPlaceholder")
            menuNavigator.openMenu(parentMenu)
            player.sendMessage("§aSelected emoji: $emojiPlaceholder")
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPreviousPageButton(pane: StaticPane, x: Int, y: Int) {
        val prevItem = ItemStack.of(Material.ARROW)
            .name("§e⬅ Previous Page")
            .lore("§7Go to previous page")

        val guiItem = GuiItem(prevItem) {
            currentPage--
            open() // Reopen menu with new page
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addNextPageButton(pane: StaticPane, x: Int, y: Int) {
        val nextItem = ItemStack.of(Material.ARROW)
            .name("§eNext Page ➡")
            .lore("§7Go to next page")

        val guiItem = GuiItem(nextItem) {
            currentPage++
            open() // Reopen menu with new page
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.BARRIER)
            .name("§c⬅ Back to Emoji Menu")
            .lore("§7Return to emoji settings")

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(parentMenu)
        }
        pane.addItem(guiItem, x, y)
    }

    override fun passData(data: Any?) {
        // Handle page navigation data if needed
        when (data) {
            is Int -> currentPage = data
        }
    }
}

/**
 * Test function to demonstrate validation functionality
 * This can be removed once proper testing is implemented
 */
fun testEmojiValidation() {
    val testCases = listOf(
        ":cat:" to "Valid emoji",
        "cat" to "Missing colons",
        ":cat" to "Missing closing colon",
        "cat:" to "Missing opening colon",
        ":" to "Empty emoji name",
        "::" to "Empty emoji name (double colon)",
        ":verylongemojinamethatexceedsfiftycharacterslimit:" to "Too long",
        ":emoji<script>:" to "Invalid characters",
        null to "Null input (clears emoji)",
        "" to "Empty string (clears emoji)",
        "   " to "Blank string (clears emoji)"
    )

    println("=== Emoji Validation Test Results ===")
    testCases.forEach { (input, description) ->
        val result = validateEmojiFormat(input)
        val status = when (result) {
            is ValidationResult.Valid -> "✅ VALID"
            is ValidationResult.Invalid -> "❌ INVALID: ${result.message}"
        }
        println("$description: '$input' -> $status")
    }
}

// Helper function for testing (can be called from anywhere)
private fun validateEmojiFormat(emoji: String?): ValidationResult {
    if (emoji.isNullOrBlank()) {
        return ValidationResult.Valid // Empty is valid (clears emoji)
    }

    // Check format: must start and end with colon
    if (!emoji.startsWith(":") || !emoji.endsWith(":")) {
        return ValidationResult.Invalid("Must be in format :emoji_name:")
    }

    // Extract emoji name (remove colons)
    val emojiName = emoji.substring(1, emoji.length - 1)

    // Check if emoji name is empty
    if (emojiName.isBlank()) {
        return ValidationResult.Invalid("Emoji name cannot be empty")
    }

    // Check length (including colons)
    if (emoji.length > 50) {
        return ValidationResult.Invalid("Emoji too long (max 50 characters)")
    }

    // Basic sanitization - check for potentially problematic characters
    val problematicChars = setOf('<', '>', '"', '\'', '\\', '\n', '\r', '\t')
    if (emojiName.any { it in problematicChars }) {
        return ValidationResult.Invalid("Emoji contains invalid characters")
    }

    return ValidationResult.Valid
}

