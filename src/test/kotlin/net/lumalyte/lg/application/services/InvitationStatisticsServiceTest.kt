package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.domain.entities.GuildInvitationLeaderboardEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class InvitationStatisticsServiceTest {
    @Test
    fun leaderboardDelegatesBoundedGuildQueryAndTotalCount() {
        val guildId = UUID.randomUUID()
        val inviter = UUID.randomUUID()
        val repository = mockk<GuildInvitationRepository>()
        val expected = listOf(GuildInvitationLeaderboardEntry(inviter, 7))
        every { repository.getInvitationLeaderboard(guildId, 10) } returns expected
        every { repository.getSentInvitationCount(guildId) } returns 12
        val service = InvitationStatisticsService(repository)

        assertEquals(expected, service.getLeaderboard(guildId, 10))
        assertEquals(12, service.getTotalInvitations(guildId))
        verify(exactly = 1) { repository.getInvitationLeaderboard(guildId, 10) }
        verify(exactly = 1) { repository.getSentInvitationCount(guildId) }
    }

    @Test
    fun negativeLeaderboardLimitIsClampedToZero() {
        val guildId = UUID.randomUUID()
        val repository = mockk<GuildInvitationRepository>()
        every { repository.getInvitationLeaderboard(guildId, 0) } returns emptyList()
        val service = InvitationStatisticsService(repository)

        assertEquals(emptyList<GuildInvitationLeaderboardEntry>(), service.getLeaderboard(guildId, -5))
        verify(exactly = 1) { repository.getInvitationLeaderboard(guildId, 0) }
    }

    @Test
    fun pagedLeaderboardClampsPageAndUsesRepositoryOffset() {
        val guildId = UUID.randomUUID()
        val inviter = UUID.randomUUID()
        val repository = mockk<GuildInvitationRepository>()
        val expected = listOf(GuildInvitationLeaderboardEntry(inviter, 3))
        every { repository.getInvitationLeaderboardInviterCount(guildId) } returns 21
        every { repository.getInvitationLeaderboardPage(guildId, 20, 10) } returns expected
        val service = InvitationStatisticsService(repository)

        val page = service.getLeaderboardPage(guildId, 99, 10)

        assertEquals(2, page.page)
        assertEquals(3, page.totalPages)
        assertEquals(21, page.totalInviters)
        assertEquals(expected, page.entries)
        verify(exactly = 1) { repository.getInvitationLeaderboardPage(guildId, 20, 10) }
    }
}
