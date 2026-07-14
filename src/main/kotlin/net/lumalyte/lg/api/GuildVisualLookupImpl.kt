@file:Suppress("LibraryEntitiesShouldNotBePublic")

package net.lumalyte.lg.api

import net.lumalyte.lg.application.services.GuildBannerService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import java.util.UUID

/** LumaGuilds-owned implementation of the stable public visual lookup. */
class GuildVisualLookupImpl(
    private val guilds: GuildService,
    private val members: MemberService,
    private val ranks: RankService,
    private val banners: GuildBannerService,
) : GuildVisualLookup {
    override fun getGuildVisual(guildId: UUID): GuildVisualSummary? {
        guilds.getGuild(guildId) ?: return null
        val leaderId = ranks.getHighestRank(guildId)
            ?.let { members.getMembersByRank(guildId, it.id) }
            ?.map { it.playerId }
            ?.minByOrNull(UUID::toString)
        val banner = banners.getGuildBanner(guildId)?.designData
            ?.takeIf { design -> design.patterns.size <= MAX_BANNER_PATTERNS && design.patterns.all { it.type in SUPPORTED_PATTERNS } }
            ?.let { design ->
            GuildBannerDesignSummary(
                baseColor = design.baseColor.name,
                patterns = design.patterns.take(MAX_BANNER_PATTERNS).map {
                    GuildBannerPatternSummary(it.type, it.color.name)
                },
            )
        }
        return GuildVisualSummary(leaderId, banner)
    }

    private companion object {
        const val MAX_BANNER_PATTERNS = 6
        val SUPPORTED_PATTERNS = setOf(
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
