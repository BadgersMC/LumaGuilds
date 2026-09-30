package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.GuildGoldRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.KillRepository
import net.lumalyte.lg.application.persistence.LeaderboardRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.EntityType
import net.lumalyte.lg.domain.entities.ExtendedLeaderboardType
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildKillStats
import net.lumalyte.lg.domain.entities.LeaderboardEntry
import net.lumalyte.lg.domain.entities.LeaderboardPeriod
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.WeeklyActivity
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuildLeaderboardProjectionServiceTest {
    private val guilds = mockk<GuildRepository>()
    private val members = mockk<MemberRepository>()
    private val claims = mockk<ClaimRepository>()
    private val gold = mockk<GuildGoldRepository>()
    private val kills = mockk<KillRepository>()
    private val leaderboards = mockk<LeaderboardRepository>()
    private val service = GuildLeaderboardProjectionService(
        mockk<LumaGuilds>(relaxed = true),
        guilds,
        members,
        claims,
        gold,
        kills,
        leaderboards,
    )

    @Test
    fun `reconcile projects authoritative guild state into every banner leaderboard`() {
        val alphaId = UUID.randomUUID()
        val betaId = UUID.randomUUID()
        val alpha = Guild(alphaId, "Alpha", level = 5, createdAt = Instant.EPOCH)
        val beta = Guild(betaId, "Beta", level = 12, createdAt = Instant.EPOCH)
        val alphaMember = mockk<Member> { every { guildId } returns alphaId }
        val betaMemberOne = mockk<Member> { every { guildId } returns betaId }

        val betaMemberTwo = mockk<Member> { every { guildId } returns betaId }
        val betaClaim = mockk<Claim> { every { teamId } returns betaId }

        every { guilds.getAll() } returns setOf(alpha, beta)
        every { members.getAll() } returns setOf(alphaMember, betaMemberOne, betaMemberTwo)
        every { claims.getAll() } returns setOf(betaClaim)
        every { kills.getAllGuildKillStats() } returns mapOf(
            alphaId to GuildKillStats(alphaId, totalKills = 3, totalDeaths = 8),
            betaId to GuildKillStats(betaId, totalKills = 9, totalDeaths = 2),
        )
        every { gold.getTopBalances(Int.MAX_VALUE) } returns listOf(alphaId to 2_000L, betaId to 500L)
        every { leaderboards.getWeeklyActivityForPeriod(any(), Int.MAX_VALUE) } returns listOf(
            WeeklyActivity(
                guildId = betaId,
                weekStart = Instant.EPOCH,
                weekEnd = Instant.EPOCH.plusSeconds(604_800),
                kills = 4,
            )
        )
        every { leaderboards.getLeaderboardEntryCount(any(), any()) } returns 0
        every { leaderboards.getLeaderboardEntries(any(), any(), any()) } returns emptyList()
        every { leaderboards.deleteLeaderboardEntries(any(), any(), any()) } returns 0

        val writes = mutableListOf<LeaderboardEntry>()
        every { leaderboards.batchUpdateEntries(any()) } answers {

            val batch = firstArg<List<LeaderboardEntry>>()
            writes += batch
            batch.size
        }

        val result = service.reconcile(service.captureSnapshot())

        assertEquals(14, writes.size)
        assertEquals(7, result.changedBoards.size)

        val levels = writes.filter { it.leaderboardType == ExtendedLeaderboardType.GUILD_LEVEL }
        assertEquals(betaId, levels.single { it.rank == 1 }.entityId)
        assertEquals(12.0, levels.single { it.entityId == betaId }.value)

        val wealth = writes.filter { it.leaderboardType == ExtendedLeaderboardType.GUILD_BANK_BALANCE }
        assertEquals(alphaId, wealth.single { it.rank == 1 }.entityId)
        assertEquals(2_000.0, wealth.single { it.entityId == alphaId }.value)

        val memberCounts = writes.filter { it.leaderboardType == ExtendedLeaderboardType.GUILD_MEMBER_COUNT }
        assertEquals(2.0, memberCounts.single { it.entityId == betaId }.value)

        val weekly = writes.filter { it.leaderboardType == ExtendedLeaderboardType.WEEKLY_ACTIVITY }
        assertEquals(40.0, weekly.single { it.entityId == betaId }.value)
        assertTrue(weekly.all { it.period == LeaderboardPeriod.WEEKLY })
    }

    @Test
    fun `reconcile deletes orphaned guild rows and rewrites stale ranks`() {
        val activeId = UUID.randomUUID()
        val staleId = UUID.randomUUID()
        val active = Guild(activeId, "Active", level = 20, createdAt = Instant.EPOCH)
        val staleEntry = LeaderboardEntry(
            leaderboardType = ExtendedLeaderboardType.GUILD_LEVEL,
            entityId = staleId,
            entityType = EntityType.GUILD,
            value = 99.0,
            rank = 1,
            period = LeaderboardPeriod.ALL_TIME,
        )
        val activeEntry = LeaderboardEntry(
            leaderboardType = ExtendedLeaderboardType.GUILD_LEVEL,
            entityId = activeId,
            entityType = EntityType.GUILD,
            value = 1.0,
            rank = 2,
            period = LeaderboardPeriod.ALL_TIME,
        )

        every { guilds.getAll() } returns setOf(active)
        every { members.getAll() } returns emptySet()
        every { claims.getAll() } returns emptySet()

        every { kills.getAllGuildKillStats() } returns emptyMap()
        every { gold.getTopBalances(Int.MAX_VALUE) } returns emptyList()
        every { leaderboards.getWeeklyActivityForPeriod(any(), Int.MAX_VALUE) } returns emptyList()
        every { leaderboards.getLeaderboardEntryCount(any(), any()) } answers {
            if (firstArg<ExtendedLeaderboardType>() == ExtendedLeaderboardType.GUILD_LEVEL) 2 else 0
        }
        every { leaderboards.getLeaderboardEntries(any(), any(), any()) } answers {
            if (firstArg<ExtendedLeaderboardType>() == ExtendedLeaderboardType.GUILD_LEVEL) {
                listOf(staleEntry, activeEntry)
            } else {
                emptyList()
            }
        }
        every { leaderboards.deleteLeaderboardEntries(any(), any(), any()) } returns 1
        every { leaderboards.batchUpdateEntries(any()) } answers { firstArg<List<LeaderboardEntry>>().size }

        val result = service.reconcile(service.captureSnapshot())

        verify(exactly = 1) {
            leaderboards.deleteLeaderboardEntries(
                ExtendedLeaderboardType.GUILD_LEVEL,
                staleId,
                LeaderboardPeriod.ALL_TIME,
            )
        }
        assertTrue(
            GuildLeaderboardProjectionKey(
                ExtendedLeaderboardType.GUILD_LEVEL,
                LeaderboardPeriod.ALL_TIME,
            ) in result.changedBoards
        )
    }
}
