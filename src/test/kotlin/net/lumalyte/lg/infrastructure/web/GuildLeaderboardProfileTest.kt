package net.lumalyte.lg.infrastructure.web

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.LeaderboardRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.RewardOwnershipRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.GuildBannerService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.config.WebApiConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.rewards.RewardOwnership
import net.lumalyte.lg.domain.rewards.RewardOwnershipRead
import net.lumalyte.lg.domain.rewards.RewardOwnershipSnapshot
import net.lumalyte.lg.infrastructure.web.handlers.GuildLeaderboardHandler
import net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class GuildLeaderboardProfileTest {
    private val id = UUID.randomUUID()
    private val guild = Guild(id = id, name = "Example", mode = GuildMode.PEACEFUL, createdAt = Instant.EPOCH)
    private val guilds = mockk<GuildService>()
    private val members = mockk<MemberService>()
    private val progression = mockk<ProgressionRepository>()
    private val owners = mockk<RewardOwnershipRepository>()
    private val banner = mockk<GuildBannerService>()
    private val bank = mockk<BankService>(relaxed = true)
    private val leaderboard = mockk<LeaderboardService>()
    private val elo = mockk<SeasonalEloCoordinator>()

    private fun handler() = GuildLeaderboardHandler(
        leaderboardService = leaderboard,
        guildService = guilds,
        memberService = members,
        bannerService = banner,
        progressionRepository = progression,
        leaderboardRepository = mockk<LeaderboardRepository>(),
        bankService = bank,
        rewardOwnershipRepository = owners,
        seasonalElo = elo,
        config = WebApiConfig(),
    )

    private fun entry() = handler().build("level", null, "10").entries.single()

    private fun setUp() {
        every { guilds.getAllGuilds() } returns setOf(guild)
        every { guilds.getGuild(id) } returns guild
        every { progression.getGuildProgression(id) } returns null
        every { members.getGuildMembers(id) } returns emptySet()
        every { banner.getGuildBanner(id) } returns null
        every { leaderboard.getWeeklyActivity(id, any()) } returns null
        every { elo.recordedCurrentRating(id) } returns null
    }

    @Test
    fun `exports stored prestige including zero and actual guild mode`() {
        setUp()
        every { owners.read(id) } returns RewardOwnershipRead.Found(RewardOwnershipSnapshot(1, RewardOwnership(prestigeCount = 0)))
        assertEquals(0, entry().prestige)
        assertEquals(true, entry().peaceful)
        assertNull(entry().memberUuids)
        every { owners.read(id) } returns RewardOwnershipRead.Found(RewardOwnershipSnapshot(2, RewardOwnership(prestigeCount = 3)))
        assertEquals(3, entry().prestige)
    }

    @Test
    fun `does not invent a prestige value when lookup is missing or fails`() {
        setUp()
        every { owners.read(id) } returns RewardOwnershipRead.Missing
        assertNull(entry().prestige)
        every { owners.read(id) } returns RewardOwnershipRead.Failed("offline")
        assertNull(entry().prestige)
        every { owners.read(id) } throws IllegalStateException("offline")
        assertNull(entry().prestige)
    }

    @Test
    fun `exports recorded elo and leaves failed reads unavailable`() {
        setUp()
        every { owners.read(id) } returns RewardOwnershipRead.Missing
        every { elo.recordedCurrentRating(id) } returns 1450
        assertEquals(1450, entry().seasonalElo)
        every { elo.recordedCurrentRating(id) } throws IllegalStateException("offline")
        assertNull(entry().seasonalElo)
    }

    @Test
    fun `directory pages are bounded and ordered by stable guild id`() {
        setUp()
        every { owners.read(id) } returns RewardOwnershipRead.Missing
        val ids = (0..2).map { UUID.fromString("00000000-0000-0000-0000-00000000000$it") }
        val guildSet = ids.mapIndexed { index, guildId -> Guild(guildId, "Guild$index", createdAt = Instant.EPOCH) }.toSet()
        every { guilds.getAllGuilds() } returns guildSet
        for (guild in guildSet) {
            every { guilds.getGuild(guild.id) } returns guild
            every { progression.getGuildProgression(guild.id) } returns null
            every { members.getGuildMembers(guild.id) } returns emptySet()
            every { banner.getGuildBanner(guild.id) } returns null
            every { leaderboard.getWeeklyActivity(guild.id, any()) } returns null
            every { owners.read(guild.id) } returns RewardOwnershipRead.Missing
            every { elo.recordedCurrentRating(guild.id) } returns null
        }
        val directory = handler()
        val first = directory.buildDirectoryPage(0, 2)
        assertEquals(ids.take(2).map(UUID::toString), first.entries.map { it.id })
        assertEquals(listOf(emptyList(), emptyList()), first.entries.map { it.memberUuids })
        assertEquals(3, first.total)
        assertEquals(2, first.nextOffset)
        every { guilds.getAllGuilds() } returns emptySet()
        every { members.getGuildMembers(ids.last()) } returns setOf(Member(UUID.randomUUID(), ids.last(), UUID.randomUUID(), Instant.EPOCH))
        val last = directory.buildDirectoryPage(2, 2, first.directoryRevision)
        assertEquals(listOf(ids.last().toString()), last.entries.map { it.id })
        assertEquals(emptyList(), last.entries.single().memberUuids)
        assertEquals(first.directoryRevision, last.directoryRevision)
        assertNull(last.nextOffset)
        every { guilds.getAllGuilds() } returns guildSet.drop(1).toSet()
        assertEquals(first.directoryRevision, directory.buildDirectoryPage(0, 2).directoryRevision)
        assertFailsWith<IllegalArgumentException> { directory.buildDirectoryPage(2, 2) }
        assertFailsWith<IllegalStateException> { directory.buildDirectoryPage(2, 2, "f".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { handler().buildDirectoryPage(0, 51) }
    }

    @Test
    fun `member identifiers are present only in the authenticated directory projection`() {
        setUp()
        every { owners.read(id) } returns RewardOwnershipRead.Missing
        val player = UUID.randomUUID()
        every { members.getGuildMembers(id) } returns setOf(Member(player, id, UUID.randomUUID(), Instant.EPOCH))
        assertNull(entry().memberUuids)
        assertEquals(listOf(player.toString()), handler().buildDirectoryPage(0, 50).entries.single().memberUuids)
    }

    @Test
    fun `oversized rosters remain unavailable instead of being truncated`() {
        setUp()
        every { owners.read(id) } returns RewardOwnershipRead.Missing
        val roster = (1..201).map { Member(UUID.randomUUID(), id, UUID.randomUUID(), Instant.EPOCH) }.toSet()
        every { members.getGuildMembers(id) } returns roster
        val row = handler().buildDirectoryPage(0, 50).entries.single()
        assertEquals(201, row.memberCount)
        assertNull(row.memberUuids)
    }
}
