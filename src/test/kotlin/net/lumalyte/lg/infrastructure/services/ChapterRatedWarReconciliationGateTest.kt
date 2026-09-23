package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.WarRepository
import net.lumalyte.lg.domain.entities.DurableWarRecord
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarStatus
import net.lumalyte.lg.infrastructure.persistence.migrations.SeasonalWarRatingResult
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ChapterRatedWarReconciliationGateTest {
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun `reconciles completed rated war before rollover freeze`() {
        val war = endedRatedWar()
        val repository = mockk<WarRepository>()
        val elo = mockk<SeasonalEloCoordinator>()
        every { repository.getAll() } returns listOf(DurableWarRecord(war.id, war = war))
        every { elo.isWarRatingSettled(war.id) } returnsMany listOf(false, true)
        every { elo.rateResolvedWar(war) } returns
            SeasonalWarRatingResult.Rated(1000, 1000, 1020, 1000)

        ChapterRatedWarReconciliationGate(repository, elo).reconcile("c2", 2_000)

        verify(exactly = 1) { elo.rateResolvedWar(war) }
    }

    @Test
    fun `blocks rollover while completed rated war remains unresolved`() {
        val war = endedRatedWar()
        val repository = mockk<WarRepository>()
        val elo = mockk<SeasonalEloCoordinator>()
        every { repository.getAll() } returns listOf(DurableWarRecord(war.id, war = war))
        every { elo.isWarRatingSettled(war.id) } returns false
        every { elo.rateResolvedWar(war) } returns SeasonalWarRatingResult.Frozen

        assertThrows(IllegalStateException::class.java) {
            ChapterRatedWarReconciliationGate(repository, elo).reconcile("c2", 2_000)
        }
    }

    @Test
    fun `marks unfinished rated war terminally unrated at chapter cutoff`() {
        val war = War(
            declaringGuildId = first,
            defendingGuildId = second,
            status = WarStatus.ACTIVE,
            startedAt = Instant.ofEpochMilli(1_000),
            ratedChapterId = "c2",
        )
        val repository = mockk<WarRepository>()
        val elo = mockk<SeasonalEloCoordinator>()
        every { repository.getAll() } returns listOf(DurableWarRecord(war.id, war = war))
        every { elo.isWarRatingSettled(war.id) } returnsMany listOf(false, true)
        every { elo.markWarUnratedForChapterEnd(war, 2_000) } returns SeasonalWarRatingResult.Ineligible

        ChapterRatedWarReconciliationGate(repository, elo).reconcile("c2", 2_000)

        verify(exactly = 1) { elo.markWarUnratedForChapterEnd(war, 2_000) }
    }

    private fun endedRatedWar() = War(
        declaringGuildId = first,
        defendingGuildId = second,
        status = WarStatus.ENDED,
        endedAt = Instant.ofEpochMilli(1_500),
        winner = first,
        loser = second,
        ratedChapterId = "c2",
    )
}
