@file:Suppress("FunctionNaming", "MagicNumber", "UndocumentedPublicClass", "UndocumentedPublicFunction")

package net.lumalyte.lg.api

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.utils.serializeToString
import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.block.banner.Pattern
import org.bukkit.block.banner.PatternType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
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
    private val lookup = GuildVisualLookupImpl(guilds, members, ranks)

    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `guild without banner exposes leader`() {
        stubGuildAndLeader(leaderId)

        assertEquals(GuildVisualSummary(leaderId, null), lookup.getGuildVisual(guildId))
    }

    @Test
    fun `banner with zero patterns remains a valid ordered projection`() {
        stubGuildAndLeader(leaderId, banner(Material.BLUE_BANNER))

        assertEquals(
            GuildBannerDesignSummary("BLUE", emptyList()),
            lookup.getGuildVisual(guildId)?.banner,
        )
    }

    @Test
    fun `banner preserves all six layers in order`() {
        val patterns = listOf(
            Pattern(DyeColor.WHITE, PatternType.STRIPE_TOP),
            Pattern(DyeColor.RED, PatternType.CROSS),
            Pattern(DyeColor.BLACK, PatternType.BORDER),
            Pattern(DyeColor.YELLOW, PatternType.TRIANGLE_TOP),
            Pattern(DyeColor.LIME, PatternType.CIRCLE),
            Pattern(DyeColor.PURPLE, PatternType.FLOWER),
        )
        stubGuildAndLeader(leaderId, banner(Material.BLUE_BANNER, patterns))

        assertEquals(
            patterns.map { GuildBannerPatternSummary(it.pattern.key.key.uppercase(), it.color.name) },
            lookup.getGuildVisual(guildId)?.banner?.patterns,
        )
    }

    @Test
    fun `banner removal is visible on the next lookup`() {
        every { guilds.getGuild(guildId) } returnsMany listOf(
            guild(banner(Material.RED_BANNER)),
            guild(),
        )
        stubLeader(leaderId)

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
        assertEquals(leaderId, lookup.getGuildVisual(guildId)?.leaderId)
        assertEquals(newLeaderId, lookup.getGuildVisual(guildId)?.leaderId)
    }

    @Test
    fun `missing guild has no public visual`() {
        every { guilds.getGuild(guildId) } returns null
        assertNull(lookup.getGuildVisual(guildId))
    }

    private fun stubGuildAndLeader(id: UUID, banner: String? = null) {
        every { guilds.getGuild(guildId) } returns guild(banner)
        stubLeader(id)
    }

    private fun stubLeader(id: UUID) {
        every { ranks.getHighestRank(guildId) } returns Rank(rankId, guildId, "Owner", 0)
        every { members.getMembersByRank(guildId, rankId) } returns setOf(member(id))
    }

    private fun guild(banner: String? = null) = Guild(
        id = guildId,
        name = "Synthetic Guild",
        banner = banner,
        createdAt = Instant.EPOCH,
    )

    private fun member(id: UUID) = Member(id, guildId, rankId, Instant.EPOCH)

    private fun banner(material: Material, patterns: List<Pattern> = emptyList()): String {
        val item = ItemStack(material)
        val meta = item.itemMeta as BannerMeta
        meta.patterns = patterns
        item.itemMeta = meta
        return item.serializeToString()
    }
}
