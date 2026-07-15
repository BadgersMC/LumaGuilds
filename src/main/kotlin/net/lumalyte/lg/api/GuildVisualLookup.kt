// This is a deliberately public, cross-plugin integration API. Transfer types
// contain only JDK values and are shared through Paper's join-classpath.
@file:Suppress("LibraryEntitiesShouldNotBePublic", "ForbiddenPublicDataClass")

package net.lumalyte.lg.api

import java.util.UUID

/** Stable, read-only public presentation data for guild integrations. */
interface GuildVisualLookup {
    /** Public visual data for [guildId], or null when the guild does not exist. */
    fun getGuildVisual(guildId: UUID): GuildVisualSummary?
}

/** Public guild visual data without persistence or Bukkit implementation types. */
data class GuildVisualSummary(
    /** Current leader UUID, or null when leadership cannot be resolved. */
    val leaderId: UUID?,
    /** Active public banner design, or null when the guild has no usable banner. */
    val banner: GuildBannerDesignSummary?,
)

/** Bounded active banner design in Minecraft layer order. */
data class GuildBannerDesignSummary(
    /** Minecraft dye color used by the banner item itself. */
    val baseColor: String,
    /** Pattern layers in the order Minecraft applies them. */
    val patterns: List<GuildBannerPatternSummary>,
)

/** One public banner layer. */
data class GuildBannerPatternSummary(
    /** Stable Bukkit banner pattern name. */
    val type: String,
    /** Minecraft dye color for this layer. */
    val color: String,
)
