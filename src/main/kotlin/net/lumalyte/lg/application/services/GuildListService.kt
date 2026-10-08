package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildListRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildDirectoryDetails
import net.lumalyte.lg.domain.entities.GuildListSortKey
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

data class GuildListEntry(
    val guild: Guild,
    val sortValue: Long,
    val uniquePvpKills: Int,
    val memberCount: Int,
    /** Public owner and alliance facts for this directory entry. */
    val details: GuildDirectoryDetails =
        GuildDirectoryDetails(),
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
    private val asyncExecutor: Executor = Executor { task -> task.run() },
    private val nowProvider: () -> Instant = Instant::now,
) {
    fun configuredPageSize(): Int =
        configService
            .loadConfig()
            .guildList
            .pageSize
            .coerceIn(1, MAX_PAGE_SIZE)

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
        val killWeight =
            progressionConfigService
                .getProgressionConfig()
                .activity
                .weights
                .killsThisWeek
                .coerceAtLeast(0)
        val weeklyStart = nowProvider().minus(7, ChronoUnit.DAYS)

        val entries =
            repository
                .getPage(
                    offset = safePage * safePageSize,
                    limit = safePageSize,
                    sortKey = sortKey,
                    ascending = ascending,
                    weeklyStart = weeklyStart,
                    claimsEnabled = config.claimsEnabled,
                    uniqueKillWeight = killWeight,
                )
                .mapNotNull { ranked ->
                    guildRepository.getById(ranked.guildId)?.let { guild ->
                        GuildListEntry(
                            guild = guild,
                            sortValue = ranked.sortValue,
                            uniquePvpKills = ranked.uniquePvpKills,
                            memberCount = ranked.memberCount,
                        )
                    }
                }

        val details = repository.getDetails(entries.map { it.guild.id }.toSet())
        val enriched = enrichEntries(entries, details)
        return GuildListPage(
            entries = enriched,
            page = safePage,
            pageSize = safePageSize,
            totalCount = totalCount,
            totalPages = totalPages,
            sortKey = sortKey,
            ascending = ascending,
        )
    }

    fun getPageAsync(
        page: Int,
        pageSize: Int,
        sortKey: GuildListSortKey,
        ascending: Boolean,
    ): CompletableFuture<GuildListPage> =
        CompletableFuture.supplyAsync(
            { getPage(page, pageSize, sortKey, ascending) },
            asyncExecutor,
        )

    private fun enrichEntries(
        entries: List<GuildListEntry>,
        details: Map<java.util.UUID, GuildDirectoryDetails>,
    ): List<GuildListEntry> =
        entries.map { entry ->
            entry.copy(details = details[entry.guild.id] ?: GuildDirectoryDetails())
        }
    companion object {
        const val MAX_PAGE_SIZE = 36
    }

}
