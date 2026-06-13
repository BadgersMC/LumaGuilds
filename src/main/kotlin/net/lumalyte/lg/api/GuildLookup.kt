package net.lumalyte.lg.api

import java.util.UUID

/**
 * Stable, cross-plugin integration surface for reading LumaGuilds guild data.
 *
 * LumaGuilds registers an implementation in Bukkit's ServicesManager at enable,
 * so consumers (EnthusiaMarket) access it via:
 *
 *     Bukkit.getServicesManager().load(GuildLookup::class.java)
 *
 * This deliberately avoids reaching into LumaGuilds' Koin container from another
 * plugin's classloader: a reified Koin `get<T>()` carries a `kotlin.reflect.KClass`
 * in its signature, and that type resolves to different Class objects across the
 * two plugin classloaders, producing a JVM loader-constraint LinkageError. Every
 * method here uses only JDK types plus [GuildSummary], all loaded from the shared
 * join-classpath, so the boundary is safe.
 *
 * All `permission` strings are [net.lumalyte.lg.domain.entities.RankPermission]
 * enum names (e.g. "EDIT_SHOP_STOCK").
 */
interface GuildLookup {

    /** Guild ids the player is a member of (may be empty). */
    fun getPlayerGuildIds(playerId: UUID): Set<UUID>

    /** Summary for [guildId], or null if no such guild. */
    fun getGuild(guildId: UUID): GuildSummary?

    /** Summaries for every guild. */
    fun getAllGuilds(): List<GuildSummary>

    /** True if [playerId] is a member of [guildId]. */
    fun isMember(playerId: UUID, guildId: UUID): Boolean

    /**
     * True if [playerId] holds the given [RankPermission][net.lumalyte.lg.domain.entities.RankPermission]
     * (passed by enum name) in [guildId]. Unknown permission names return false.
     */
    fun hasShopPermission(playerId: UUID, guildId: UUID, permission: String): Boolean

    /**
     * True if [playerId]'s rank in [guildId] is at least as high as the rank named
     * [rankName] (lower priority number = higher rank). Case-insensitive on the
     * rank name. Returns false if the player has no rank or the named rank is absent.
     */
    fun hasRankAtLeast(playerId: UUID, guildId: UUID, rankName: String): Boolean

    /** Guild bank balance for [guildId], or 0 if unknown. */
    fun getBankBalance(guildId: UUID): Long

    /** Withdraw [amount] from [guildId]'s bank as [actorId]. True on success. */
    fun bankWithdraw(guildId: UUID, actorId: UUID, amount: Int, reason: String): Boolean

    /** Deposit [amount] into [guildId]'s bank as [actorId]. True on success. */
    fun bankDeposit(guildId: UUID, actorId: UUID, amount: Int, reason: String): Boolean
}

/**
 * Minimal guild projection for consumers — only the fields integrations need,
 * so the boundary doesn't expose the full domain entity. Tag/emoji are the raw
 * stored values (consumers normalise formatting themselves).
 */
data class GuildSummary(
    val id: UUID,
    val name: String,
    val tag: String?,
    val emoji: String?,
)
