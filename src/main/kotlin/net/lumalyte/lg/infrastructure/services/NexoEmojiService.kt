package net.lumalyte.lg.infrastructure.services

import com.nexomc.nexo.NexoPlugin
import net.lumalyte.lg.application.services.ConfigService
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory

/** Font used by Nexo glyphs when the glyph does not declare its own font. */
private const val DEFAULT_GLYPH_FONT = "nexo:default"

/** Safe glyph id shape — rejects MiniMessage control characters (e.g. `x><reset>`). */
private val VALID_GLYPH_ID = Regex("^[a-zA-Z0-9_-]+$")

/** Snapshot of public Nexo glyph metadata used only by the infrastructure adapter. */
internal data class ResolvedNexoGlyph(
    /** Character sent to the client. */
    val character: String,
    /** Resource-pack font containing the character. */
    val font: String?,
    /** Whether Nexo explicitly registers the glyph as an emoji. */
    val isEmoji: Boolean,
    /** Canonical ID when resolving a placeholder alias. */
    val id: String? = null,
)

/** Optional-plugin resolution seam for infrastructure regression tests. */
internal fun interface NexoGlyphResolver {
    fun resolve(name: String): ResolvedNexoGlyph?
}

private object NexoPublicGlyphResolver : NexoGlyphResolver {
    override fun resolve(name: String): ResolvedNexoGlyph? {
        val manager = nexoFontManager() ?: return null
        val glyph = manager.glyphFromPlaceholder(":$name:") ?: manager.glyphFromName(name)
        val character = glyph.chars.firstOrNull()?.toString() ?: return null
        return ResolvedNexoGlyph(character, glyph.font.asString(), glyph.isEmoji, glyph.id)
    }
}

private fun resolveOptionalGlyph(resolver: NexoGlyphResolver, name: String): ResolvedNexoGlyph? {
    val glyph =
        try {
            resolver.resolve(name)
        } catch (_: IllegalStateException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: NullPointerException) {
            null
        } catch (_: LinkageError) {
            null
        }
    return glyph
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
 * Retains the existing public API used by commands, menus and integrations.
 */
@Suppress("TooManyFunctions")
class NexoEmojiService internal constructor(
    private val configService: ConfigService,
    private val glyphResolver: NexoGlyphResolver = NexoPublicGlyphResolver,
) {

    /** Creates the service using the optional installed Nexo plugin. */
    constructor(configService: ConfigService) : this(configService, NexoPublicGlyphResolver)

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
        return emoji.startsWith(":") && emoji.endsWith(":") && emoji.length > 2 &&
            VALID_GLYPH_ID.matches(emoji.substring(1, emoji.length - 1))
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
        val renderedEmoji = if (emoji != null && isValidEmojiFormat(emoji)) {
            emojiToFontTag(emoji)
        } else {
            ""
        }
        return if (renderedEmoji.isNotEmpty()) "$renderedEmoji $guildName" else guildName
    }
    
    /**
     * Gets the emoji placeholder that can be used in chat/text.
     * This returns the placeholder format that Nexo will replace with the actual emoji.
     *
     * @param emoji The emoji placeholder (e.g., ":catsmileysmile:").
     * @return The placeholder string, or empty string if invalid.
     */
    fun getEmojiPlaceholder(emoji: String?): String = emoji?.takeIf { resolveEmoji(it) != null }.orEmpty()

    /** Returns a validated PAPI glyph placeholder, or empty text for unsafe glyphs. */
    fun emojiToNexoPlaceholder(emoji: String?): String {
        val name = validatedEmojiName(emoji) ?: return ""
        return "%nexo_$name%"
    }

    /** Returns a validated MiniMessage glyph tag, or empty text for unsafe glyphs. */
    fun emojiToGlyphTag(emoji: String?): String {
        val name = validatedEmojiName(emoji) ?: return ""
        return "<glyph:$name>"
    }

    private fun resolveEmoji(emoji: String?): ResolvedNexoGlyph? {
        val name = emoji?.let(::extractEmojiName) ?: return null
        return resolveOptionalGlyph(glyphResolver, name)?.takeIf { it.isEmoji && it.character.isNotBlank() }
    }

    private fun validatedEmojiName(emoji: String?): String? {
        val glyph = resolveEmoji(emoji)
        val name = glyph?.let { it.id ?: emoji?.let(::extractEmojiName) }
        return name?.takeIf(VALID_GLYPH_ID::matches)
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
     * Resolves only registered emoji glyphs through Nexo's public FontManager API.
     * Unknown, malformed, non-emoji and unavailable glyphs return an empty string.
     */
    fun emojiToFontTag(emoji: String?): String {
        val glyph = resolveEmoji(emoji) ?: return ""
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
    fun doesEmojiExist(emoji: String): Boolean = resolveEmoji(emoji) != null

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
            "Unavailable - Emoji selection and rendering disabled"
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
