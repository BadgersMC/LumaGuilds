package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService

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
    private val lang: LangService by inject()

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

        val gui = ChestGui(4, MenuTitleBuilder.build(guild.guiTheme, 4, lang.legacy("menu.description_editor.title")))
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
        val displayItem = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.description_editor.current.name"))
            .lore(lang.legacy("menu.description_editor.current.value", "description" to (parseMiniMessageForDisplay(currentDescription) ?: lang.legacy("menu.description_editor.current.none"))))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.description_editor.current.description"))
            .lore(lang.legacy("menu.description_editor.current.scope"))

        pane.addItem(GuiItem(displayItem), x, y)
    }

    private fun addInputField(pane: StaticPane, x: Int, y: Int) {
        val inputItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name(lang.legacy("menu.description_editor.input.name"))
            .lore(lang.legacy("menu.description_editor.input.description"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.description_editor.input.current"))
            .lore(lang.legacy("menu.description_editor.input.value", "description" to (inputDescription ?: lang.raw("menu.description_editor.input.none"))))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.description_editor.input.formatting"))
            .lore(lang.legacy("menu.description_editor.input.limit"))

        val guiItem = GuiItem(inputItem) {
            // Start chat input for description
            startChatInput()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addValidationStatus(pane: StaticPane, x: Int, y: Int) {
        val statusItem = if (validationError != null) {
            ItemStack.of(Material.RED_CONCRETE)
                .name(lang.legacy("menu.description_editor.validation.error.name"))
                .lore(lang.legacy("menu.description_editor.validation.error.description", "error" to validationError!!))
        } else {
            ItemStack.of(Material.GREEN_CONCRETE)
                .name(lang.legacy("menu.description_editor.validation.valid.name"))
                .lore(lang.legacy("menu.description_editor.validation.valid.description"))
        }

        pane.addItem(GuiItem(statusItem), x, y)
    }

    private fun addSaveButton(pane: StaticPane, x: Int, y: Int) {
        val saveItem = ItemStack.of(Material.EMERALD_BLOCK)
            .name(lang.legacy("menu.description_editor.save.name"))
            .lore(lang.legacy("menu.description_editor.save.description"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.description_editor.save.effect"))
            .lore(lang.legacy("menu.description_editor.save.scope"))

        val canSave = validationError == null && inputDescription != currentDescription
        if (!canSave) {
            saveItem.name(lang.legacy("menu.description_editor.save.disabled"))
                .lore(lang.legacy("menu.description_editor.save.description"))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.description_editor.save.invalid"))
        }

        val guiItem = GuiItem(saveItem) {
            if (canSave) {
                saveDescription()
            } else {
                player.sendMessage(lang.msg("menu.description_editor.feedback.cannot_save"))
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack.of(Material.REDSTONE_BLOCK)
            .name(lang.legacy("menu.description_editor.cancel.name"))
            .lore(lang.legacy("menu.description_editor.cancel.description"))

        val guiItem = GuiItem(cancelItem) {
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPreview(pane: StaticPane, x: Int, y: Int) {
        val previewItem = ItemStack.of(Material.ITEM_FRAME)
            .name(lang.legacy("menu.description_editor.preview.name"))
            .lore(lang.legacy("menu.description_editor.preview.description"))

        inputDescription?.let { desc ->
            if (validationError == null) {
                try {
                    val miniMessage = MiniMessage.miniMessage()
                    val component = miniMessage.deserialize(desc)
                    val plainText = PlainTextComponentSerializer.plainText().serialize(component)

                    previewItem.lore(lang.legacy("menu.description_editor.preview.value", "description" to plainText))
                } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                    previewItem.lore(lang.legacy("menu.description_editor.preview.error"))
                }
            } else {
                previewItem.lore(lang.legacy("menu.description_editor.preview.empty"))
            }
        } ?: run {
            previewItem.lore(lang.legacy("menu.description_editor.preview.empty"))
        }

        pane.addItem(GuiItem(previewItem), x, y)
    }

    private fun saveDescription() {
        val description = inputDescription

        // Check permission
        if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_DESCRIPTION)) {
            player.sendMessage(lang.msg("menu.description_editor.feedback.no_permission"))
            return
        }

        // Save the description
        val success = guildService.setDescription(guild.id, description, player.uniqueId)

        if (success) {
            player.sendMessage(lang.msg("menu.description_editor.feedback.updated"))

            // Show the description with MiniMessage formatting rendered
            if (description != null) {
                try {
                    val miniMessage = MiniMessage.miniMessage()
                    val component = miniMessage.deserialize("${lang.raw("menu.description_editor.feedback.new_description_prefix")}$description")
                    player.sendMessage(component)
                } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                    player.sendMessage(lang.msg("menu.description_editor.feedback.new_description", "description" to description))
                }
            } else {
                player.sendMessage(lang.msg("menu.description_editor.feedback.cleared"))
            }

            // Refresh guild data
            guild = guildService.getGuild(guild.id) ?: guild

            // Go back to previous menu
            menuNavigator.goBack()
        } else {
            player.sendMessage(lang.msg("menu.description_editor.feedback.update_failed"))
        }
    }

    private fun validateDescription(description: String?): String? {
        if (description == null) return null

        if (description.length > 100) {
            return lang.legacy("menu.description_editor.validation.too_long", "length" to description.length)
        }

        // Try to parse with MiniMessage to check for errors
        try {
            val miniMessage = MiniMessage.miniMessage()
            miniMessage.deserialize(description)
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            return lang.legacy("menu.description_editor.validation.invalid_format", "error" to (e.message ?: lang.raw("menu.description_editor.validation.unknown_error")))
        }

        return null
    }

    private fun startChatInput() {
        player.sendMessage(lang.msg("menu.description_editor.chat.header"))
        player.sendMessage(lang.msg("menu.description_editor.chat.prompt"))
        player.sendMessage(lang.msg("menu.description_editor.chat.formatting"))
        player.sendMessage(lang.msg("menu.description_editor.chat.limit"))
        player.sendMessage(lang.msg("menu.description_editor.chat.cancel"))

        chatInputListener.startInputMode(player, this)

        // Close the menu when entering input mode
        player.closeInventory()
    }

    override fun onChatInput(player: Player, input: String) {
        if (input.lowercase() == "cancel") {
            player.sendMessage(lang.msg("menu.description_editor.feedback.cancelled"))
            open() // Reopen menu
            return
        }

        setInputDescription(input)
        open() // Reopen menu with updated input
    }

    override fun onCancel(player: Player) {
        player.sendMessage(lang.msg("menu.description_editor.feedback.cancelled"))
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
            // Convert to legacy formatting for menu display
            val legacyText = LegacyComponentSerializer.legacySection().serialize(component)
            legacyText
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            description // Fallback to raw text if parsing fails
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
