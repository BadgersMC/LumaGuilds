@file:Suppress("FunctionNaming", "MagicNumber", "UndocumentedPublicClass", "UndocumentedPublicFunction")

package net.lumalyte.lg.api

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.BannerColor
import net.lumalyte.lg.application.services.BannerDesignData
import net.lumalyte.lg.application.services.BannerPattern
import net.lumalyte.lg.application.services.GuildBannerService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildBanner
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuildVisualLookupImplTest {
    private val guildId = UUID.randomUUID()
    private val leaderId = UUID.randomUUID()
    private val rankId = UUID.randomUUID()
    private val guilds = mockk<GuildService>()
    private val members = mockk<MemberService>()
    private val ranks = mockk<RankService>()
    private val banners = mockk<GuildBannerService>()
    private val lookup = GuildVisualLookupImpl(guilds, members, ranks, banners)

    @Test
    fun `guild without banner exposes leader`() {
        stubGuildAndLeader(leaderId)
        every { banners.getGuildBanner(guildId) } returns null

        assertEquals(GuildVisualSummary(leaderId, null), lookup.getGuildVisual(guildId))
    }

    @Test
    fun `banner with zero patterns remains a valid ordered projection`() {
        stubGuildAndLeader(leaderId)
        every { banners.getGuildBanner(guildId) } returns banner(BannerDesignData(BannerColor.BLUE))

        assertEquals(
            GuildBannerDesignSummary("BLUE", emptyList()),
            lookup.getGuildVisual(guildId)?.banner,
        )
    }

    @Test
    fun `banner preserves all six layers in order`() {
        stubGuildAndLeader(leaderId)
        val patterns = listOf(
            BannerPattern("STRIPE_TOP", BannerColor.WHITE),
            BannerPattern("CROSS", BannerColor.RED),
            BannerPattern("BORDER", BannerColor.BLACK),
            BannerPattern("TRIANGLE_TOP", BannerColor.YELLOW),
            BannerPattern("CIRCLE", BannerColor.LIME),
            BannerPattern("FLOWER", BannerColor.PURPLE),
        )
        every { banners.getGuildBanner(guildId) } returns banner(BannerDesignData(BannerColor.BLUE, patterns))

        assertEquals(
            patterns.map { GuildBannerPatternSummary(it.type, it.color.name) },
            lookup.getGuildVisual(guildId)?.banner?.patterns,
        )
    }

    @Test
    fun `banner removal is visible on the next lookup`() {
        stubGuildAndLeader(leaderId)
        every { banners.getGuildBanner(guildId) } returnsMany listOf(
            banner(BannerDesignData(BannerColor.RED)),
            null,
        )

        assertEquals("RED", lookup.getGuildVisual(guildId)?.banner?.baseColor)
        assertNull(lookup.getGuildVisual(guildId)?.banner)
    }

    @Test
    fun `leadership transfer is visible on the next lookup`() {
        val newLeaderId = UUID.randomUUID()
        every { guilds.getGuild(guildId) } returns guild()
        every { ranks.getHighestRank(guildId) } returns Rank(rankId, guildId, "Owner", 0)
        every { members.getMembersByRank(guildId, rankId) } returnsMany listOf(
            setOf(member(leaderId)),
            setOf(member(newLeaderId)),
        )
        every { banners.getGuildBanner(guildId) } returns null

        assertEquals(leaderId, lookup.getGuildVisual(guildId)?.leaderId)
        assertEquals(newLeaderId, lookup.getGuildVisual(guildId)?.leaderId)
    }

    @Test
    fun `missing guild has no public visual`() {
        every { guilds.getGuild(guildId) } returns null
        assertNull(lookup.getGuildVisual(guildId))
    }

    private fun stubGuildAndLeader(id: UUID) {
        every { guilds.getGuild(guildId) } returns guild()
        every { ranks.getHighestRank(guildId) } returns Rank(rankId, guildId, "Owner", 0)
        every { members.getMembersByRank(guildId, rankId) } returns setOf(member(id))
    }

    private fun guild() = Guild(id = guildId, name = "Synthetic Guild", createdAt = Instant.EPOCH)

    private fun member(id: UUID) = Member(id, guildId, rankId, Instant.EPOCH)

    private fun banner(design: BannerDesignData) = GuildBanner(
        id = UUID.randomUUID(),
        guildId = guildId,
        name = null,
        designData = design,
        submittedBy = UUID.randomUUID(),
        createdAt = Instant.EPOCH,
        isActive = true,
    )
}
