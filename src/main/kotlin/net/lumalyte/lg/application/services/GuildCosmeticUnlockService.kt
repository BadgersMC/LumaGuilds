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
import java.util.UUID

/**
 * Ownership policy for cosmetics granted by other plugins (REQ-121). Holiday GUI
 * themes ([GuiTheme.requiresUnlock]) are available only once owned; every other
 * theme keeps its existing behavior.
 */
class GuildCosmeticUnlockService(
    private val guilds: GuildRepository,
    private val unlocks: GuildCosmeticUnlockRepository,
    private val clock: () -> Instant = Instant::now,
) {
    /** Idempotent. False for a missing guild, invalid input or a persistence failure. */
    fun unlock(guildId: UUID, type: String, key: String, displayName: String, source: String): Boolean {
        val normalType = normalise(type, MAX_COSMETIC_TYPE_LENGTH) ?: return false
        val normalKey = normalise(key, MAX_COSMETIC_KEY_LENGTH) ?: return false
        if (guilds.getById(guildId) == null) return false
        if (unlocks.get(guildId, normalType, normalKey) != null) return true
        val name = displayName.trim().ifEmpty { normalKey }.take(MAX_COSMETIC_DISPLAY_NAME_LENGTH)
        return unlocks.saveIfAbsent(
            GuildCosmeticUnlock(guildId, normalType, normalKey, name, source.trim().take(MAX_COSMETIC_SOURCE_LENGTH), clock())
        )
    }

    /**
     * Idempotent. Revoking the menu theme a guild has equipped resets it to
     * [GuiTheme.DEFAULT]. False for a missing guild, invalid input or a persistence failure.
     */
    fun revoke(guildId: UUID, type: String, key: String): Boolean {
        val normalType = normalise(type, MAX_COSMETIC_TYPE_LENGTH) ?: return false
        val normalKey = normalise(key, MAX_COSMETIC_KEY_LENGTH) ?: return false
        val guild = guilds.getById(guildId) ?: return false
        if (!unlocks.delete(guildId, normalType, normalKey)) return false
        if (normalType == MENU_THEME_COSMETIC && guild.guiTheme.name == normalKey && guild.guiTheme.requiresUnlock) {
            return guilds.update(guild.copy(guiTheme = GuiTheme.DEFAULT))
        }
        return true
    }

    fun unlockedKeys(guildId: UUID, type: String): Set<String> {
        val normalType = normalise(type, MAX_COSMETIC_TYPE_LENGTH) ?: return emptySet()
        return unlocks.getForGuild(guildId).filter { it.type == normalType }.map { it.key }.toSet()
    }

    fun isThemeAvailable(guildId: UUID, theme: GuiTheme): Boolean =
        !theme.requiresUnlock || unlocks.get(guildId, MENU_THEME_COSMETIC, theme.name) != null

    /** The granted display name (e.g. "Halloween '26") when owned, else the theme's own name. */
    fun themeDisplayName(guildId: UUID, theme: GuiTheme): String =
        unlocks.get(guildId, MENU_THEME_COSMETIC, theme.name)?.displayName ?: theme.displayName

    private fun normalise(value: String, max: Int): String? =
        value.trim().uppercase().takeIf { it.isNotEmpty() && it.length <= max }
}
