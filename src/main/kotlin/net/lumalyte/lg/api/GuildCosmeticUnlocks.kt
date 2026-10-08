// Deliberately public cross-plugin API loaded via ServicesManager (see GuildLookup).
@file:Suppress("LibraryEntitiesShouldNotBePublic")

package net.lumalyte.lg.api

import java.util.UUID

/**
 * Cosmetics granted to guilds by other plugins — currently holiday GUI themes
 * earned through EnthusiaHolidays guild goals (REQ-121). Registered in Bukkit's
 * ServicesManager at enable:
 *
 *     Bukkit.getServicesManager().load(GuildCosmeticUnlocks::class.java)
 *
 * JDK types only, so the boundary is classloader-safe. All calls are idempotent;
 * callers may retry freely.
 *
 * [type] is `MENU_THEME`, `BADGE`, `ICON_FRAME` or `CHAT_DECORATION`; for
 * `MENU_THEME`, [key] is a `GuiTheme` name such as `HALLOWEEN`. Unknown
 * types and keys are stored so a newer integration cannot wedge its sync.
 */
interface GuildCosmeticUnlocks {
    /** True if the guild owns the cosmetic afterwards (newly or already); false for a missing guild, invalid input or storage failure. */
    fun unlockCosmetic(
        guildId: UUID,
        type: String,
        key: String,
        displayName: String,
        source: String,
    ): Boolean

    /** True if the guild does not own it afterwards; resets an equipped holiday theme to the default. */
    fun revokeCosmetic(guildId: UUID, type: String, key: String): Boolean

    /** Keys of [type] the guild owns. */
    fun getUnlockedCosmetics(guildId: UUID, type: String): Set<String>
}
