package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildListRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildListSortKey
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import java.time.Instant
import java.time.temporal.ChronoUnit

data class GuildListEntry(
    val guild: Guild,
    val sortValue: Long,
    val uniquePvpKills: Int,
)

data class GuildListPage(
    val entries: List<GuildListEntry>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val totalPages: Int,
    val sortKey: GuildListSortKey,
    val ascending: Boolean,
)

class GuildListService(
    private val repository: GuildListRepository,
    private val guildRepository: GuildRepository,
    private val configService: ConfigService,
    private val progressionConfigService: ProgressionConfigService,
    private val nowProvider: () -> Instant = Instant::now,
) {
    fun configuredPageSize(): Int =
        configService.loadConfig().guildList.pageSize.coerceIn(1, MAX_PAGE_SIZE)

    fun getPage(
        page: Int,
        pageSize: Int,
        sortKey: GuildListSortKey,
        ascending: Boolean,
    ): GuildListPage {
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val totalCount = repository.getCount().coerceAtLeast(0)
        val totalPages = maxOf(1, (totalCount + safePageSize - 1) / safePageSize)
        val safePage = page.coerceIn(0, totalPages - 1)
        val config = configService.loadConfig()
        val killWeight = progressionConfigService
            .getProgressionConfig()
            .activity
            .weights
            .killsThisWeek
            .coerceAtLeast(0)
        val weeklyStart = nowProvider().minus(7, ChronoUnit.DAYS)

        val entries = repository.getPage(
            offset = safePage * safePageSize,
            limit = safePageSize,
            sortKey = sortKey,
            ascending = ascending,
            weeklyStart = weeklyStart,
            claimsEnabled = config.claimsEnabled,
            uniqueKillWeight = killWeight,
        ).mapNotNull { ranked ->
            guildRepository.getById(ranked.guildId)?.let { guild ->
                GuildListEntry(
                    guild = guild,
                    sortValue = ranked.sortValue,
                    uniquePvpKills = ranked.uniquePvpKills,
                )
            }
        }

        return GuildListPage(
            entries = entries,
            page = safePage,
            pageSize = safePageSize,
            totalCount = totalCount,
            totalPages = totalPages,
            sortKey = sortKey,
            ascending = ascending,
        )
    }

    companion object {
        const val MAX_PAGE_SIZE = 36
    }
}
