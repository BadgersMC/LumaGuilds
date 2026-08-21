package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuItemBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.lumalyte.lg.utils.ColorCodeUtils
import net.lumalyte.lg.utils.GuildTagValidationMessages
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
    private val configService: ConfigService by inject()
    private val lang: LangService by inject()

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
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.legacy("menu.tag_editor.title", "guild" to guild.name)))
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
            .name(lang.legacy("menu.tag_editor.current.name"))
            .lore(lang.legacy("menu.tag_editor.current.guild", "guild" to guild.name))

        currentTag?.let { tagValue ->
            val formattedTag = renderFormattedTag(tagValue)
            currentTagDisplay.lore(lang.legacy("menu.tag_editor.current.tag", "tag" to formattedTag))
                .lore(lang.legacy("menu.tag_editor.current.description"))
        } ?: run {
            currentTagDisplay.lore(lang.legacy("menu.tag_editor.current.not_set"))
                .lore(lang.legacy("menu.tag_editor.current.create"))
        }

        val guiItem = GuiItem(currentTagDisplay) {
            // Display only - no action needed
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTagStatusIndicator(pane: StaticPane, x: Int, y: Int) {
        val characterCount = inputTag?.let { countVisibleCharacters(it) } ?: 0
        val statusItem = ItemStack.of(Material.PAPER)
            .name(lang.legacy("menu.tag_editor.status.name"))
            .lore(lang.legacy("menu.tag_editor.status.characters", "count" to characterCount))

        if (characterCount > 32) {
            statusItem.name(lang.legacy("menu.tag_editor.status.too_long"))
                .lore(lang.legacy("menu.tag_editor.status.characters_error", "count" to characterCount))
                .lore(lang.legacy("menu.tag_editor.status.reduce"))
        } else if (characterCount > 28) {
            statusItem.name(lang.legacy("menu.tag_editor.status.nearly_full"))
                .lore(lang.legacy("menu.tag_editor.status.characters", "count" to characterCount))
                .lore(lang.legacy("menu.tag_editor.status.close"))
        } else {
            statusItem.name(lang.legacy("menu.tag_editor.status.ok"))
                .lore(lang.legacy("menu.tag_editor.status.characters", "count" to characterCount))
        }

        val guiItem = GuiItem(statusItem) {
            // Display only - no action needed
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTagInputField(pane: StaticPane, x: Int, y: Int) {
        val inputItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name(lang.legacy("menu.tag_editor.input.name"))
            .lore(lang.legacy("menu.tag_editor.input.format"))
            .lore(lang.legacy("menu.tag_editor.input.examples"))
            .lore(lang.legacy("menu.tag_editor.input.gradient"))
            .lore(lang.legacy("menu.tag_editor.input.color"))
            .lore(lang.legacy("menu.tag_editor.input.bold"))

        val currentInput = inputTag ?: ""
        if (currentInput.isNotEmpty()) {
            val formattedInput = renderFormattedTag(currentInput)
            inputItem.lore(lang.legacy("menu.tag_editor.input.current", "tag" to formattedInput))
        } else {
            inputItem.lore(lang.legacy("menu.tag_editor.input.none"))
        }

        // Add validation status
        if (validationError != null) {
            inputItem.lore(lang.legacy("menu.tag_editor.input.invalid", "error" to validationError!!))
        } else if (inputTag?.isNotEmpty() == true) {
            inputItem.lore(lang.legacy("menu.tag_editor.input.valid"))
        }

        if (isInInputMode()) {
            inputItem.name(lang.legacy("menu.tag_editor.input.waiting"))
                .lore(lang.legacy("menu.tag_editor.input.prompt"))
                .lore(lang.legacy("menu.rank_edit.info.cancel_hint"))
        } else {
            inputItem.lore(lang.legacy("menu.tag_editor.input.click"))
        }

        val guiItem = GuiItem(inputItem) {
            if (!isInInputMode()) {
                startChatInput()
            } else {
                player.sendMessage(lang.msg("menu.tag_editor.feedback.already_waiting"))
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPreviewSection(pane: StaticPane, x: Int, y: Int) {
        val previewTag = inputTag ?: guild.name
        val previewItem = ItemStack.of(Material.PAPER)
            .name(lang.legacy("menu.tag_editor.preview.name"))
            .lore(lang.legacy("menu.tag_editor.preview.description"))

        if (validationError != null) {
            // Show error state with unformatted tag
            previewItem.lore(lang.legacy("menu.tag_editor.preview.invalid_message", "player" to player.name, "tag" to previewTag))
                .lore(lang.legacy("menu.tag_editor.preview.invalid"))
        } else {
            // Show properly formatted tag using MiniMessage
            val formattedTag = renderFormattedTag(previewTag)
            previewItem.lore(lang.legacy("menu.tag_editor.preview.message", "player" to player.name, "tag" to formattedTag))

            if (inputTag != null && inputTag != currentTag) {
                previewItem.lore(lang.legacy("menu.tag_editor.preview.new"))
            } else {
                previewItem.lore(lang.legacy("menu.tag_editor.preview.current"))
            }
        }

        previewItem.lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.tag_editor.preview.hint"))

        val guiItem = GuiItem(previewItem) {
            // Preview only - no click action needed
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addSaveButton(pane: StaticPane, x: Int, y: Int) {
        val saveItem = ItemStack.of(Material.LIME_WOOL)
            .name(lang.legacy("menu.tag_editor.action.save.name"))
            .lore(lang.legacy("menu.tag_editor.action.save.description"))

        // Disable save if there are validation errors
        if (validationError != null) {
            saveItem.name(lang.legacy("menu.tag_editor.action.save.cannot"))
                .lore(lang.legacy("menu.tag_editor.action.save.fix"))
        } else if (inputTag == currentTag) {
            saveItem.name(lang.legacy("menu.tag_editor.action.save.no_changes"))
                .lore(lang.legacy("menu.tag_editor.action.save.unchanged"))
        } else {
            saveItem.lore(lang.legacy("menu.tag_editor.action.save.click"))
        }

        val guiItem = GuiItem(saveItem) {
            println("[LumaGuilds] TagEditorMenu: Save button clicked")
            println("[LumaGuilds] TagEditorMenu: currentTag: '$currentTag', inputTag: '$inputTag'")
            println("[LumaGuilds] TagEditorMenu: validationError: ${validationError ?: "NONE"}")

            if (validationError != null) {
                player.sendMessage(lang.msg("menu.tag_editor.feedback.cannot_save", "error" to validationError!!))
                return@GuiItem
            }

            if (inputTag == currentTag) {
                println("[LumaGuilds] TagEditorMenu: No changes detected - inputTag equals currentTag")
                player.sendMessage(lang.msg("menu.tag_editor.feedback.no_changes"))
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

                player.sendMessage(lang.msg("menu.tag_editor.feedback.updated"))
                if (tagToSave != null) {
                    val displayTag = ColorCodeUtils.renderTagForDisplay(tagToSave)
                    player.sendMessage(lang.msg("menu.tag_editor.feedback.new_tag", "tag" to displayTag))
                } else {
                    player.sendMessage(lang.msg("menu.tag_editor.feedback.cleared_tag"))
                }

                // Refresh the menu to show updated state
                open()
            } else {
                player.sendMessage(lang.msg("menu.tag_editor.feedback.save_failed"))
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addClearButton(pane: StaticPane, x: Int, y: Int) {
        val clearItem = ItemStack.of(Material.BARRIER)
            .name(lang.legacy("menu.tag_editor.action.clear.name"))
            .lore(lang.legacy("menu.tag_editor.action.clear.description"))
            .lore(lang.legacy("menu.tag_editor.action.clear.fallback"))

        val guiItem = GuiItem(clearItem) {
            inputTag = null
            validationError = null

            player.sendMessage(lang.msg("menu.tag_editor.feedback.cleared"))

            // Refresh the menu to show updated state
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack.of(Material.RED_WOOL)
            .name(lang.legacy("menu.tag_editor.action.cancel.name"))
            .lore(lang.legacy("menu.tag_editor.action.cancel.description"))

        if (isInInputMode()) {
            cancelItem.name(lang.legacy("menu.tag_editor.action.cancel.input_name"))
                .lore(lang.legacy("menu.tag_editor.action.cancel.input_description"))
        }

        val guiItem = GuiItem(cancelItem) {
            if (isInInputMode()) {
                chatInputListener.stopInputMode(player)
                player.sendMessage(lang.msg("menu.tag_editor.feedback.input_cancelled"))
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

        player.sendMessage(lang.msg("menu.tag_editor.chat.header"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.prompt"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.support"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.legacy"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.colors"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.gradients"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.formatting"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.limit"))
        player.sendMessage(lang.msg("menu.rank_edit.input.cancel"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.footer"))
    }


    private fun validateTag(tag: String): String? {
        // Length validation
        val visibleChars = countVisibleCharacters(tag)
        if (visibleChars > 32) {
            return lang.legacy("menu.tag_editor.validation.too_long", "count" to visibleChars)
        }

        if (tag.trim().isEmpty()) {
            return lang.legacy("menu.tag_editor.validation.empty")
        }

        // MiniMessage format validation
        // Check for balanced tags
        val openTags = Regex("<([^/>][^>]*)>").findAll(tag).count()
        val closeTags = Regex("</[^>]+>").findAll(tag).count()

        // Check for common syntax errors
        if (tag.contains("<<") || tag.contains(">>")) {
            return lang.legacy("menu.tag_editor.validation.double_brackets")
        }

        // Reject interactive MiniMessage event tags (click/hover/insertion)
        net.lumalyte.lg.utils.GuildTagValidator.validationFailure(tag, configService.loadConfig().guild.nameFilter)?.let {
            return when (it) {
                is net.lumalyte.lg.utils.GuildTagValidator.Failure.InteractiveTag ->
                    "Guild tags cannot contain interactive '${it.tagName}' tags. Use colors and formatting only."
                net.lumalyte.lg.utils.GuildTagValidator.Failure.InappropriateContent ->
                    "Name contains inappropriate content."
            }
        }

        // Try to parse with MiniMessage
        try {
            val miniMessage = MiniMessage.miniMessage()
            miniMessage.deserialize(tag)
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            // Parse the error message to provide helpful feedback
            val errorMsg = e.message ?: lang.raw("menu.tag_editor.validation.invalid_format")
            return when {
                errorMsg.contains("unclosed", ignoreCase = true) ->
                    lang.legacy("menu.tag_editor.validation.unclosed")
                errorMsg.contains("unknown tag", ignoreCase = true) ->
                    lang.legacy("menu.tag_editor.validation.unknown_tag")
                errorMsg.contains("invalid", ignoreCase = true) ->
                    lang.legacy("menu.tag_editor.validation.invalid_syntax")
                else -> lang.legacy("menu.tag_editor.validation.format_error", "error" to errorMsg.take(50))
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
                .replace(Regex("\u00A7[0-9a-fk-or]"), "")  // Remove section sign color codes
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
            player.sendMessage(lang.msg("menu.tag_editor.feedback.invalid", "error" to error))
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
        player.sendMessage(lang.msg("menu.tag_editor.feedback.set", "tag" to displayTag))
        player.sendMessage(lang.msg("menu.tag_editor.feedback.save_hint"))
    }

    override fun onCancel(player: Player) {
        println("[LumaGuilds] TagEditorMenu: Player cancelled tag input")   
        player.sendMessage(lang.msg("menu.tag_editor.feedback.input_cancelled"))

        // Reopen the menu without changes
        Bukkit.getScheduler().runTask(net.lumalyte.lg.common.PluginKeys.getPlugin(), Runnable {
            open()
        })
    }   
}
