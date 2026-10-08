package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildCosmeticUnlockRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.domain.entities.GuildCosmeticUnlock
import net.lumalyte.lg.domain.entities.MAX_COSMETIC_DISPLAY_NAME_LENGTH
import net.lumalyte.lg.domain.entities.MAX_COSMETIC_KEY_LENGTH
import net.lumalyte.lg.domain.entities.MAX_COSMETIC_SOURCE_LENGTH
import net.lumalyte.lg.domain.entities.MAX_COSMETIC_TYPE_LENGTH
import net.lumalyte.lg.domain.entities.MENU_THEME_COSMETIC
import net.lumalyte.lg.utils.GuiTheme
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * Ownership policy for cosmetics granted by other plugins (REQ-121). Holiday GUI
 * themes ([GuiTheme.requiresUnlock]) are available only once owned; every other
 * theme keeps its existing behavior.
 */
@Suppress("LibraryEntitiesShouldNotBePublic")
class GuildCosmeticUnlockService(
    private val guilds: GuildRepository,
    private val unlocks: GuildCosmeticUnlockRepository,
    private val clock: () -> Instant = Instant::now,
) {
    /** Idempotent. False for a missing guild, invalid input or a persistence failure. */
    fun unlock(guildId: UUID, type: String, key: String, displayName: String, source: String): Boolean {
        val identity = identity(type, key) ?: return false
        return guilds.getById(guildId) != null && (
            unlocks.get(guildId, identity.type, identity.key) != null ||
                unlocks.saveIfAbsent(createUnlock(guildId, identity, displayName, source))
            )
    }

    private fun createUnlock(
        guildId: UUID,
        identity: CosmeticKey,
        displayName: String,
        source: String,
    ): GuildCosmeticUnlock {
        val name = displayName.trim().ifEmpty { identity.key }.take(MAX_COSMETIC_DISPLAY_NAME_LENGTH)
        return GuildCosmeticUnlock(
            guildId,
            identity.type,
            identity.key,
            name,
            source.trim().take(MAX_COSMETIC_SOURCE_LENGTH),
            clock(),
        )
    }

    /**
     * Idempotent. Revoking the menu theme a guild has equipped resets it to
     * [GuiTheme.DEFAULT]. False for a missing guild, invalid input or a persistence failure.
     */
    fun revoke(guildId: UUID, type: String, key: String): Boolean {
        val identity = identity(type, key) ?: return false
        val guild = guilds.getById(guildId)
        val removed = guild != null && unlocks.delete(guildId, identity.type, identity.key)
        if (removed) resetEquippedTheme(requireNotNull(guild), identity)
        return removed
    }

    private fun resetEquippedTheme(guild: net.lumalyte.lg.domain.entities.Guild, identity: CosmeticKey) {
        if (identity.type == MENU_THEME_COSMETIC && guild.guiTheme.name == identity.key &&
            guild.guiTheme.requiresUnlock
        ) {
            // Compare-and-set preserves concurrent changes to other guild fields.
            guilds.updateGuiTheme(guild.id, guild.guiTheme, GuiTheme.DEFAULT)
        }
    }

    private fun identity(type: String, key: String): CosmeticKey? {
        val category = normalise(type, MAX_COSMETIC_TYPE_LENGTH) ?: return null
        val name = normalise(key, MAX_COSMETIC_KEY_LENGTH)
        return name?.let { CosmeticKey(category, it) }
    }

    private data class CosmeticKey(val type: String, val key: String)

    /** Read owned keys in the normalized category. */
    fun unlockedKeys(guildId: UUID, type: String): Set<String> {
        if (guilds.getById(guildId) == null) return emptySet()
        val normalType = normalise(type, MAX_COSMETIC_TYPE_LENGTH) ?: return emptySet()
        return unlocks.getForGuild(guildId).filter { it.type == normalType }.map { it.key }.toSet()
    }

    /** Progression themes stay available; holiday themes require ownership. */
    fun isThemeAvailable(guildId: UUID, theme: GuiTheme): Boolean =
        !theme.requiresUnlock || unlocks.get(guildId, MENU_THEME_COSMETIC, theme.name) != null

    /** The granted display name (e.g. "Halloween '26") when owned, else the theme's own name. */
    fun themeDisplayName(guildId: UUID, theme: GuiTheme): String =
        unlocks.get(guildId, MENU_THEME_COSMETIC, theme.name)?.displayName ?: theme.displayName

    private fun normalise(value: String, max: Int): String? =
        value.trim().uppercase(Locale.ROOT).takeIf { it.isNotEmpty() && it.length <= max }
}
