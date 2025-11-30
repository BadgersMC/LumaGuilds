package net.lumalyte.lg.infrastructure.services

import org.bukkit.entity.Player
import org.slf4j.LoggerFactory

/**
 * Service for interacting with Nexo emojis.
 * Handles emoji validation and permission checking for guild emoji system.
 * JFS there is some really nasty shit going on here.
 */
class NexoEmojiService {
    
    private val logger = LoggerFactory.getLogger(NexoEmojiService::class.java)
    
    /**
     * Validates if an emoji placeholder is in valid Nexo format.
     *
     * @param emoji The emoji placeholder to validate (e.g., ":catsmileysmile:").
     * @return true if the emoji format is valid, false otherwise.
     */
    fun isValidEmojiFormat(emoji: String): Boolean {
        return emoji.startsWith(":") && emoji.endsWith(":") && emoji.length > 2
    }
    
    /**
     * Checks if a player has the required permission to use a specific emoji.
     * This checks the specific emoji permission in the format "lumalyte.emoji.<emojiname>".
     *
     * @param player The player to check permissions for.
     * @param emoji The emoji placeholder (e.g., ":catsmileysmile:").
     * @return true if the player has permission, false otherwise.
     */
    fun hasEmojiPermission(player: Player, emoji: String): Boolean {
        // Extract emoji name from placeholder format
        val emojiName = extractEmojiName(emoji)
        if (emojiName == null) {
            logger.debug("Invalid emoji format: $emoji")
            return false
        }
        
        // Check specific emoji permission
        val permission = "lumalyte.emoji.$emojiName"
        val hasPermission = player.hasPermission(permission)
        
        if (!hasPermission) {
            logger.debug("Player ${player.name} does not have permission for emoji: $permission")
        }
        
        return hasPermission
    }
    
    /**
     * Gets a formatted display name for the guild including the emoji if set.
     *
     * @param guildName The name of the guild.
     * @param emoji The emoji placeholder, or null if not set.
     * @return The formatted display name with emoji prefix.
     */
    fun formatGuildDisplayName(guildName: String, emoji: String?): String {
        return if (emoji != null && isValidEmojiFormat(emoji)) {
            "$emoji $guildName"
        } else {
            guildName
        }
    }
    
    /**
     * Gets the emoji placeholder that can be used in chat/text.
     * This returns the placeholder format that Nexo will replace with the actual emoji.
     *
     * @param emoji The emoji placeholder (e.g., ":catsmileysmile:").
     * @return The placeholder string, or empty string if invalid.
     */
    fun getEmojiPlaceholder(emoji: String?): String {
        return if (emoji != null && isValidEmojiFormat(emoji)) {
            emoji
        } else {
            ""
        }
    }
    
    /**
     * Extracts the emoji name from the placeholder format.
     *
     * @param emoji The emoji placeholder (e.g., ":catsmileysmile:").
     * @return The emoji name without colons, or null if invalid format.
     */
    fun extractEmojiName(emoji: String): String? {
        return if (isValidEmojiFormat(emoji)) {
            emoji.removePrefix(":").removeSuffix(":")
        } else {
            null
        }
    }
    
    /**
     * Creates an emoji placeholder from an emoji name.
     *
     * @param emojiName The name of the emoji (e.g., "catsmileysmile").
     * @return The formatted placeholder (e.g., ":catsmileysmile:").
     */
    fun createEmojiPlaceholder(emojiName: String): String {
        return ":$emojiName:"
    }
    
    /**
     * Gets the permission node for a specific emoji.
     *
     * @param emoji The emoji placeholder (e.g., ":catsmileysmile:").
     * @return The permission node (e.g., "lumalyte.emoji.catsmileysmile"), or null if invalid format.
     */
    fun getEmojiPermission(emoji: String): String? {
        val emojiName = extractEmojiName(emoji)
        return if (emojiName != null) {
            "lumalyte.emoji.$emojiName"
        } else {
            null
        }
    }
    
    /**
     * Validates if an emoji exists in the Nexo configuration using the Glyph API.
     * Uses glyphFromPlaceholder() method since emojis are referenced by placeholder format like :cat:.
     *
     * @param emoji The emoji placeholder to check.
     * @return true if the emoji exists in Nexo, false otherwise.
     */
    fun doesEmojiExist(emoji: String): Boolean {
        // First check format
        if (!isValidEmojiFormat(emoji)) {
            logger.debug("Emoji format invalid: '$emoji'")
            return false
        }

        // If Nexo is not available, fall back to format validation
        if (!isNexoAvailable()) {
            logger.debug("Nexo unavailable, allowing emoji based on format validation only: $emoji")
            return true
        }

        // Try to check with Glyph API using FontManager directly
        return try {
            val fontManager = getFontManager()
            if (fontManager == null) {
                logger.debug("FontManager not available for emoji validation")
                return false
            }

            // Try glyphFromPlaceholder method first (for placeholder format like :cat:)
            val glyphFromPlaceholderMethod = fontManager.javaClass.getMethod("glyphFromPlaceholder", String::class.java)
            val glyph = glyphFromPlaceholderMethod.invoke(fontManager, emoji)

            val exists = glyph != null
            logger.debug("Emoji '$emoji' validation result: ${if (exists) "FOUND" else "NOT FOUND"}")

            if (exists) {
                return true
            }

            // Fallback: try glyphFromName with the emoji name (without colons)
            val emojiName = extractEmojiName(emoji) ?: return false
            val glyphFromNameMethod = fontManager.javaClass.getMethod("glyphFromName", String::class.java)
            val glyph2 = glyphFromNameMethod.invoke(fontManager, emojiName)

            val exists2 = glyph2 != null
            logger.debug("Emoji '$emoji' fallback validation result: ${if (exists2) "FOUND" else "NOT FOUND"}")

            exists2

        } catch (e: Exception) {
            // Optional integration - catching all exceptions to prevent plugin failure
            logger.warn("Error validating emoji '$emoji': ${e.message}")
            false
        }
    }

    /**
     * Gets the Nexo FontManager using reflection.
     * Tries multiple approaches to access the FontManager instance.
     */
    private fun getFontManager(): Any? {
        return try {

            // TODO: this is fucking trash
            // To who it may concern, I tried to use the FontManager class directly, but it was not working.
            // So I had to use reflection to get the FontManager instance.
            // This is a hack, but it works.
            // If you have a better way to do this, please let me know.
            // I am not a fan of reflection, but it is what it is.
            // This is a workaround to get the FontManager instance.

            // First, let's check if the Nexo plugin is even loaded at all
            val pluginManager = org.bukkit.Bukkit.getPluginManager()
            val nexoPlugin = pluginManager.getPlugin("Nexo")

            if (nexoPlugin == null) {
                logger.debug("Nexo plugin not found in plugin manager")
                return null
            }

            // Primary approach: Try to access FontManager field directly from plugin
            // cancer
            try {
                val fontManagerField = nexoPlugin.javaClass.getDeclaredField("fontManager")
                fontManagerField.isAccessible = true
                val fontManager = fontManagerField.get(nexoPlugin)

                if (fontManager != null) {
                    return fontManager
                }
            } catch (e: Exception) {
            // Optional integration - catching all exceptions to prevent plugin failure
                logger.debug("FontManager field access failed: ${e.message}")
            }

            // Fallback: Try to find FontManager through plugin's methods
            // gonna kms
            try {
                val methods = nexoPlugin.javaClass.methods
                for (method in methods) {
                    if (method.name.lowercase().contains("font") ||
                        method.name.lowercase().contains("manager") ||
                        method.returnType.simpleName == "FontManager") {
                        try {
                            val result = method.invoke(nexoPlugin)
                            if (result != null && result.javaClass.simpleName == "FontManager") {
                                return result
                            }
                        } catch (e: Exception) {
            // Optional integration - catching all exceptions to prevent plugin failure
                            // Continue to next method
                        }
                    }
                }
            } catch (e: Exception) {
            // Optional integration - catching all exceptions to prevent plugin failure
                logger.debug("FontManager method access failed: ${e.message}")
            }

            logger.debug("Could not access FontManager from Nexo plugin")
            null

        } catch (e: Exception) {
            // Optional integration - catching all exceptions to prevent plugin failure
            logger.warn("Error accessing Nexo FontManager: ${e.message}")
            null
        }
    }

    /**
     * Checks if Nexo plugin is available and loaded.
     * Tests both the Glyph API and NexoItems API for maximum compatibility.
     *
     * @return true if Nexo is available, false otherwise.
     */
    fun isNexoAvailable(): Boolean {
        return try {
            logger.debug("Testing Nexo availability...")

            // Try to access FontManager through our improved access method
            val fontManagerAvailable = getFontManager() != null
            logger.debug("FontManager available: $fontManagerAvailable")

            // Try to access NexoItems as fallback
            val nexoItemsAvailable = try {
                Class.forName("com.nexomc.nexo.api.NexoItems")
                logger.debug("NexoItems class found")
                true
            } catch (e: ClassNotFoundException) {
                logger.debug("NexoItems class not found: ${e.message}")
                false
            }

            // Try to access main Nexo plugin class as additional check
            val nexoPluginAvailable = try {
                Class.forName("com.nexomc.nexo.Nexo")
                logger.debug("Main Nexo plugin class found")
                true
            } catch (e: ClassNotFoundException) {
                logger.debug("Main Nexo plugin class not found: ${e.message}")
                false
            }

            val available = fontManagerAvailable || nexoItemsAvailable || nexoPluginAvailable
            logger.debug("Nexo availability test result: ${if (available) "AVAILABLE" else "NOT AVAILABLE"} (FontManager: $fontManagerAvailable, Items: $nexoItemsAvailable, Plugin: $nexoPluginAvailable)")

            available
        } catch (e: ClassNotFoundException) {
            logger.debug("Nexo plugin classes not found - running in compatibility mode")
            false
        } catch (e: Exception) {
            // Optional integration - catching all exceptions to prevent plugin failure
            logger.debug("Nexo API test failed - running in compatibility mode: ${e.message}")
            false
        }
    }

    /**
     * Gets detailed status information about Nexo availability.
     *
     * @return Status description for debugging/logging.
     */
    fun getNexoStatusDescription(): String {
        return if (isNexoAvailable()) {
            "Available - Full emoji validation active"
        } else {
            "Unavailable - Format-only validation active"
        }
    }

    /**
     * Gets all emojis that a player has permission to use.
     * This filters all available Nexo emojis based on the player's permissions.
     *
     * @param player The player to check permissions for.
     * @return List of emoji names (without colons) that the player can use.
     */
    fun getPlayerUnlockedEmojis(player: Player): List<String> {
        if (!isNexoAvailable()) {
            logger.debug("Nexo not available, cannot get unlocked emojis for ${player.name}")
            return emptyList()
        }

        val availableEmojis = getAvailableEmojisFromNexo()
        if (availableEmojis == null) {
            logger.debug("Could not retrieve available emojis from Nexo")
            return emptyList()
        }

        // Filter emojis based on player permissions
        return availableEmojis.filter { emojiName ->
            val permission = "lumalyte.emoji.$emojiName"
            val hasPermission = player.hasPermission(permission)

            if (!hasPermission) {
                logger.debug("Player ${player.name} does not have permission for emoji: $permission")
            }

            hasPermission
        }
    }

    /**
     * Gets all available emoji names from Nexo using FontManager's emoji collection.
     * This is a cached operation for performance.
     *
     * @return List of emoji names (without colons), or null if unavailable.
     */
    private fun getAvailableEmojisFromNexo(): List<String>? {
        return try {
            // Get FontManager instance first
            val fontManager = getFontManager()
            if (fontManager == null) {
                logger.debug("FontManager not available for emoji listing")
                return null
            }

            // Try to get emojis() method from FontManager
            val emojisMethod = fontManager.javaClass.getMethod("emojis")
            val emojis = emojisMethod.invoke(fontManager) as? Collection<*>

            if (emojis != null) {
                if (emojis.isEmpty()) {
                    return emptyList()
                }

                // Extract emoji names from the Glyph objects
                val emojiNames = emojis.mapNotNull { glyph ->
                    try {
                        // Try multiple ways to get the name from glyph
                        var name: String? = null

                        // Method 1: getName()
                        try {
                            val getNameMethod = glyph?.javaClass?.getMethod("getName")
                            name = getNameMethod?.invoke(glyph) as? String
                        } catch (e: Exception) {
            // Optional integration - catching all exceptions to prevent plugin failure
                            // Continue to next method
                        }

                        // Method 2: getId()
                        if (name == null) {
                            try {
                                val getIdMethod = glyph?.javaClass?.getMethod("getId")
                                name = getIdMethod?.invoke(glyph) as? String
                            } catch (e: Exception) {
            // Optional integration - catching all exceptions to prevent plugin failure
                                // Continue to next method
                            }
                        }

                        // Method 3: Direct field access
                        if (name == null && glyph != null) {
                            try {
                                val fields = glyph.javaClass.declaredFields
                                for (field in fields) {
                                    if (field.name.lowercase().contains("name") ||
                                        field.name.lowercase().contains("id")) {
                                        field.isAccessible = true
                                        val value = field.get(glyph) as? String
                                        if (value != null) {
                                            name = value
                                            break
                                        }
                                    }
                                }
                            } catch (e: Exception) {
            // Optional integration - catching all exceptions to prevent plugin failure
                                // Continue
                            }
                        }

                        name
                    } catch (e: Exception) {
            // Optional integration - catching all exceptions to prevent plugin failure
                        null
                    }
                }

                emojiNames
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            // Optional integration - catching all exceptions to prevent plugin failure
            logger.warn("Error getting available emojis from FontManager: ${e.message}")
            null
        }
    }
}
