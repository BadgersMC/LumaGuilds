package net.lumalyte.lg.infrastructure.services

import com.nexomc.nexo.NexoPlugin
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.utils.ColorCodeUtils
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory

/** Font used by Nexo glyphs when the glyph does not declare its own font. */
private const val DEFAULT_GLYPH_FONT = "nexo:default"

/** Safe glyph id shape — rejects MiniMessage control characters (e.g. `x><reset>`). */
private val VALID_GLYPH_ID = Regex("^[a-zA-Z0-9_-]+$")

data class ResolvedNexoGlyph(val character: String, val font: String?)

fun interface NexoGlyphResolver {
    fun resolve(name: String): ResolvedNexoGlyph?
}

private object NexoPublicGlyphResolver : NexoGlyphResolver {
    override fun resolve(name: String): ResolvedNexoGlyph? {
        val glyph = nexoFontManager()?.glyphFromName(name) ?: return null
        val character = glyph.chars.firstOrNull()?.toString() ?: return null
        return ResolvedNexoGlyph(character, glyph.font.asString())
    }
}

private fun nexoFontManager() = try {
    NexoPlugin.instance().fontManager()
} catch (_: Exception) {
    null
} catch (_: LinkageError) {
    null
}

/**
 * Service for interacting with Nexo emojis.
 * Handles emoji validation and permission checking for guild emoji system.
 * JFS there is some really nasty shit going on here.
 */
class NexoEmojiService(
    private val configService: ConfigService,
    private val glyphResolver: NexoGlyphResolver = NexoPublicGlyphResolver
) {

    private val logger = LoggerFactory.getLogger(NexoEmojiService::class.java)

    /**
     * Gets the configured emoji permission prefix from config.
     * Defaults to "lumaguilds.emoji" if not configured.
     */
    private fun getEmojiPermissionPrefix(): String {
        return configService.loadConfig().chat.emojiPermissionPrefix
    }
    
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
     * This checks the specific emoji permission in the format "<prefix>.<emojiname>".
     * The prefix is configurable in config.yml under chat.emoji_permission_prefix.
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

        // Check specific emoji permission using configured prefix
        val prefix = getEmojiPermissionPrefix()
        val permission = "$prefix.$emojiName"
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
     * @param emojiName The name of the emoji (e.g. "catsmileysmile").
     * @return The formatted placeholder (e.g. ":catsmileysmile:").
     */
    fun createEmojiPlaceholder(emojiName: String): String {
        return ":$emojiName:"
    }

    /**
     * Converts a guild emoji (`:catsmileysmile:`) into a raw MiniMessage font fragment
     * (`<font:nexo:default>\uE001</font>`) for consumers whose MiniMessage cannot resolve
     * Nexo's custom `<glyph:...>` tag — e.g. UnlimitedNameTags (backend display-entity
     * nametags), the InteractiveChatDiscordSrvAddon playerlist image renderer, and Velocitab
     * via PapiProxyBridge. `<font:...>` is a standard Adventure tag and the char is drawn
     * from the glyph's own font in the mandatory resource pack, so no glyph-tag registration
     * is needed. This is the renderable counterpart to `%lumaguilds_guild_emoji_minimessage%`.
     *
     * Resolves the glyph char + font through Nexo's public FontManager API; falls back to
     * the `<glyph:name>` tag when Nexo is absent or the
     * char/font cannot be read (matching [ColorCodeUtils.emojiToGlyphTag] output).
     *
     * Non-`:name:` values pass through unchanged; null or blank return `""`.
     */
    fun emojiToFontTag(emoji: String?): String {
        if (emoji.isNullOrBlank()) return ""
        val emojiName = extractEmojiName(emoji) ?: return emoji
        // Reject glyph ids containing MiniMessage control characters (e.g. ":x><reset>:")
        // so they can never reach the generated tag — isValidEmojiFormat only checks delimiters.
        if (!VALID_GLYPH_ID.matches(emojiName)) return emoji
        val glyph = glyphResolver.resolve(emojiName) ?: return "<glyph:$emojiName>"
        if (glyph.character.isBlank()) return "<glyph:$emojiName>"
        val font = glyph.font?.takeUnless { it.isBlank() || it == "minecraft" } ?: DEFAULT_GLYPH_FONT
        return "<font:$font>${glyph.character}</font>"
    }
    
    /**
     * Gets the permission node for a specific emoji.
     *
     * @param emoji The emoji placeholder (e.g., ":catsmileysmile:").
     * @return The permission node (e.g., "<prefix>.catsmileysmile"), or null if invalid format.
     */
    fun getEmojiPermission(emoji: String): String? {
        val emojiName = extractEmojiName(emoji)
        return if (emojiName != null) {
            val prefix = getEmojiPermissionPrefix()
            "$prefix.$emojiName"
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

        return try {
            val fontManager = nexoFontManager() ?: return false
            if (fontManager.glyphFromPlaceholder(emoji) != null) return true
            val emojiName = extractEmojiName(emoji) ?: return false
            fontManager.glyphFromID(emojiName) != null
        } catch (e: RuntimeException) {
            logger.warn("Error validating emoji '$emoji': ${e.message}")
            false
        }
    }

    /**
     * Checks if Nexo plugin is available and loaded.
     * Tests both the Glyph API and NexoItems API for maximum compatibility.
     *
     * @return true if Nexo is available, false otherwise.
     */
    fun isNexoAvailable(): Boolean {
        return nexoFontManager() != null
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
        val prefix = getEmojiPermissionPrefix()
        return availableEmojis.filter { emojiName ->
            val permission = "$prefix.$emojiName"
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
            nexoFontManager()?.emojis()?.map { it.id }
        } catch (e: RuntimeException) {
            logger.warn("Error getting available emojis from FontManager: ${e.message}")
            null
        }
    }
}
