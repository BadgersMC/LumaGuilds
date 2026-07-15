@file:Suppress("LibraryEntitiesShouldNotBePublic")

package net.lumalyte.lg.api

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.utils.deserializeToItemStack
import org.bukkit.inventory.meta.BannerMeta
import java.util.UUID

/** LumaGuilds-owned implementation of the stable public visual lookup. */
class GuildVisualLookupImpl(
    private val guilds: GuildService,
    private val members: MemberService,
    private val ranks: RankService,
) : GuildVisualLookup {
    override fun getGuildVisual(guildId: UUID): GuildVisualSummary? {
        val guild = guilds.getGuild(guildId) ?: return null
        val leaderId =
            ranks.getHighestRank(guildId)
                ?.let { members.getMembersByRank(guildId, it.id) }
                ?.map { it.playerId }
                ?.minByOrNull(UUID::toString)
        val banner = guild.banner?.let(::projectBanner)
        return GuildVisualSummary(leaderId, banner)
    }

    private fun projectBanner(serialized: String): GuildBannerDesignSummary? =
        serialized.deserializeToItemStack()?.let { item ->
            (item.itemMeta as? BannerMeta)?.let { metadata ->
                projectBannerMetadata(item.type.name, metadata)
            }
        }

    private fun projectBannerMetadata(typeName: String, metadata: BannerMeta): GuildBannerDesignSummary? {
        val baseColor = bannerBaseColor(typeName)
        val patterns = bannerPatterns(metadata)
        return baseColor?.takeIf { patternsAreSupported(patterns) }
            ?.let { GuildBannerDesignSummary(it, patterns) }
    }

    private fun bannerBaseColor(typeName: String): String? {
        return typeName.removeSuffix("_BANNER")
            .takeIf { typeName.endsWith("_BANNER") && it in SUPPORTED_COLORS }
    }

    private fun bannerPatterns(metadata: BannerMeta): List<GuildBannerPatternSummary> =
        metadata.patterns.map { pattern ->
            GuildBannerPatternSummary(pattern.pattern.key.key.uppercase(), pattern.color.name)
        }

    private fun patternsAreSupported(patterns: List<GuildBannerPatternSummary>): Boolean =
        patterns.size <= MAX_BANNER_PATTERNS && patterns.all { it.type in SUPPORTED_PATTERNS }

    private companion object {
        const val MAX_BANNER_PATTERNS = 6
        val SUPPORTED_COLORS =
            setOf(
                "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
                "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK",
            )
        val SUPPORTED_PATTERNS =
            setOf(
                "SQUARE_BOTTOM_LEFT", "SQUARE_BOTTOM_RIGHT", "SQUARE_TOP_LEFT", "SQUARE_TOP_RIGHT",
                "STRIPE_BOTTOM", "STRIPE_TOP", "STRIPE_LEFT", "STRIPE_RIGHT", "STRIPE_CENTER",
                "STRIPE_MIDDLE", "STRIPE_DOWNRIGHT", "STRIPE_DOWNLEFT", "STRIPE_SMALL", "CROSS",
                "STRAIGHT_CROSS", "TRIANGLE_BOTTOM", "TRIANGLE_TOP", "TRIANGLES_BOTTOM", "TRIANGLES_TOP",
                "DIAGONAL_LEFT", "DIAGONAL_RIGHT", "DIAGONAL_LEFT_MIRROR", "DIAGONAL_RIGHT_MIRROR",
                "CIRCLE", "RHOMBUS", "HALF_VERTICAL", "HALF_HORIZONTAL", "HALF_VERTICAL_MIRROR",
                "HALF_HORIZONTAL_MIRROR", "BORDER", "CURLY_BORDER", "GRADIENT", "GRADIENT_UP",
                "BRICKS", "GLOBE", "CREEPER", "SKULL", "FLOWER", "MOJANG", "PIGLIN", "FLOW", "GUSTER",
            )
    }
}
