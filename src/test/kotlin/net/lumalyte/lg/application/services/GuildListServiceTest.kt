package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildListRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.config.GuildListConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.config.ProgressionSystemConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildListRankedRow
import net.lumalyte.lg.domain.entities.GuildListSortKey
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GuildListServiceTest {
    @Test
    fun `page lookup clamps page and delegates bounded offset limit to repository`() {
        val rankedId = UUID.randomUUID()
        val guild = Guild(
            id = rankedId,
            name = "Badgers",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        val repository = mockk<GuildListRepository>()
        val guildRepository = mockk<GuildRepository>()
        val configService = mockk<ConfigService>()
        val progressionConfig = mockk<ProgressionConfigService>()

        every { repository.getCount() } returns 41
        every {
            repository.getPage(
                offset = 36,
                limit = 18,
                sortKey = GuildListSortKey.WEEKLY_ACTIVE,
                ascending = false,
                weeklyStart = Instant.parse("2026-09-14T12:00:00Z"),
                claimsEnabled = true,
                uniqueKillWeight = 25,
            )
        } returns listOf(GuildListRankedRow(rankedId, 75, 2))
        every { guildRepository.getById(rankedId) } returns guild
        every { configService.loadConfig() } returns MainConfig(
            claimsEnabled = true,
            guildList = GuildListConfig(pageSize = 18),
        )
        every { progressionConfig.getProgressionConfig() } returns ProgressionSystemConfig()

        val service = GuildListService(
            repository,
            guildRepository,
            configService,
            progressionConfig,
            nowProvider = { Instant.parse("2026-09-21T12:00:00Z") },
        )

        val page = service.getPage(
            page = 99,
            pageSize = 18,
            sortKey = GuildListSortKey.WEEKLY_ACTIVE,
            ascending = false,
        )

        assertEquals(2, page.page)
        assertEquals(3, page.totalPages)
        assertEquals(41, page.totalCount)
        assertEquals(listOf(guild), page.entries.map { it.guild })
        assertEquals(75L, page.entries.single().sortValue)
        assertEquals(2, page.entries.single().uniquePvpKills)
        verify(exactly = 1) {
            repository.getPage(
                offset = 36,
                limit = 18,
                sortKey = GuildListSortKey.WEEKLY_ACTIVE,
                ascending = false,
                weeklyStart = Instant.parse("2026-09-14T12:00:00Z"),
                claimsEnabled = true,
                uniqueKillWeight = 25,
            )
        }
    }

    @Test
    fun `configured page size is bounded for inventory layout`() {
        val repository = mockk<GuildListRepository>(relaxed = true)
        val guildRepository = mockk<GuildRepository>(relaxed = true)
        val configService = mockk<ConfigService>()
        val progressionConfig = mockk<ProgressionConfigService>(relaxed = true)

        every { configService.loadConfig() } returns MainConfig(
            guildList = GuildListConfig(pageSize = 99),
        )

        val service = GuildListService(
            repository,
            guildRepository,
            configService,
            progressionConfig,
        )

        assertEquals(36, service.configuredPageSize())
    }

    @Test
    fun `sort defaults match product requirements`() {
        assertEquals(false, GuildListSortKey.ALL_TIME_ACTIVE.defaultAscending)
        assertEquals(false, GuildListSortKey.WEEKLY_ACTIVE.defaultAscending)
        assertEquals(true, GuildListSortKey.GUILD_LEVEL.defaultAscending)
        assertEquals(true, GuildListSortKey.CREATED_AT.defaultAscending)
    }
}
