package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.domain.entities.GuildInvitationLeaderboardEntry
import java.util.UUID

data class InvitationLeaderboardPage(
    val entries: List<GuildInvitationLeaderboardEntry>,
    val page: Int,
    val pageSize: Int,
    val totalInviters: Int,
    val totalPages: Int
)

class InvitationStatisticsService(
    private val repository: GuildInvitationRepository
) {
    fun getLeaderboard(guildId: UUID, limit: Int = 10): List<GuildInvitationLeaderboardEntry> =
        repository.getInvitationLeaderboard(guildId, limit.coerceAtLeast(0))

    fun getLeaderboardPage(guildId: UUID, page: Int, pageSize: Int = 10): InvitationLeaderboardPage {
        val safePageSize = pageSize.coerceAtLeast(1)
        val totalInviters = repository.getInvitationLeaderboardInviterCount(guildId)
        val totalPages = maxOf(1, (totalInviters + safePageSize - 1) / safePageSize)
        val safePage = page.coerceIn(0, totalPages - 1)
        val entries = repository.getInvitationLeaderboardPage(guildId, safePage * safePageSize, safePageSize)
        return InvitationLeaderboardPage(entries, safePage, safePageSize, totalInviters, totalPages)
    }

    fun getTotalInvitations(guildId: UUID): Int =
        repository.getSentInvitationCount(guildId)
}
