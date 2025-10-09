package net.lumalyte.lg.utils

import net.lumalyte.lg.application.services.MessageService
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * Helper utility for converting legacy color codes to Adventure Components in menus.
 * Provides convenient methods for menu item creation with Adventure Components.
 */
object AdventureMenuHelper {

    private val miniMessage = MiniMessage.miniMessage()
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()

    /**
     * Converts legacy color codes to MiniMessage format.
     * Maps common legacy codes to their Adventure Components equivalents.
     */
    fun convertLegacyColors(text: String): String {
        return text
            .replace("§0", "<black>")
            .replace("§1", "<dark_blue>")
            .replace("§2", "<dark_green>")
            .replace("§3", "<dark_aqua>")
            .replace("§4", "<dark_red>")
            .replace("§5", "<dark_purple>")
            .replace("§6", "<gold>")
            .replace("§7", "<gray>")
            .replace("§8", "<dark_gray>")
            .replace("§9", "<blue>")
            .replace("§a", "<green>")
            .replace("§b", "<aqua>")
            .replace("§c", "<red>")
            .replace("§d", "<light_purple>")
            .replace("§e", "<yellow>")
            .replace("§f", "<white>")
            .replace("§k", "<obfuscated>")
            .replace("§l", "<bold>")
            .replace("§m", "<strikethrough>")
            .replace("§n", "<underline>")
            .replace("§o", "<italic>")
            .replace("§r", "<reset>")
    }

    /**
     * Sets the display name of an ItemStack using Adventure Components.
     */
    fun ItemStack.setAdventureName(player: Player, messageService: MessageService, text: String): ItemStack {
        val meta = this.itemMeta ?: return this
        val convertedText = convertLegacyColors(text)
        val component = messageService.renderLegacyUserInput(convertedText)
        meta.displayName(component)
        this.itemMeta = meta
        return this
    }

    /**
     * Adds a lore line to an ItemStack using Adventure Components.
     */
    fun ItemStack.addAdventureLore(player: Player, messageService: MessageService, text: String): ItemStack {
        val meta = this.itemMeta ?: return this
        val convertedText = convertLegacyColors(text)
        val component = messageService.renderLegacyUserInput(convertedText)
        val currentLore = meta.lore() ?: mutableListOf()
        currentLore.add(component)
        meta.lore(currentLore)
        this.itemMeta = meta
        return this
    }

    /**
     * Sets the lore of an ItemStack using Adventure Components.
     */
    fun ItemStack.setAdventureLore(player: Player, messageService: MessageService, lore: List<String>): ItemStack {
        val meta = this.itemMeta ?: return this
        val components = lore.map { text ->
            val convertedText = convertLegacyColors(text)
            messageService.renderLegacyUserInput(convertedText)
        }
        meta.lore(components)
        this.itemMeta = meta
        return this
    }

    /**
     * Creates a menu title using Adventure Components.
     */
    fun createMenuTitle(player: Player, messageService: MessageService, title: String): String {
        val convertedTitle = convertLegacyColors(title)
        val component = messageService.renderLegacyUserInput(convertedTitle)
        return plainTextSerializer.serialize(component)
    }

    /**
     * Sends a message to a player using Adventure Components.
     */
    fun sendMessage(player: Player, messageService: MessageService, text: String) {
        val convertedText = convertLegacyColors(text)
        val component = messageService.renderLegacyUserInput(convertedText)
        player.sendMessage(component)
    }

    /**
     * Extension function for ItemStack to easily set Adventure Components name and lore.
     */
    fun ItemStack.adventureName(player: Player, messageService: MessageService, text: String): ItemStack {
        return setAdventureName(player, messageService, text)
    }

    /**
     * Extension function for ItemStack to easily add Adventure Components lore.
     */
    fun ItemStack.adventureLore(player: Player, messageService: MessageService, text: String): ItemStack {
        return addAdventureLore(player, messageService, text)
    }

    /**
     * Extension function for ItemStack to easily set Adventure Components lore list.
     */
    fun ItemStack.adventureLore(player: Player, messageService: MessageService, lore: List<String>): ItemStack {
        return setAdventureLore(player, messageService, lore)
    }
}
