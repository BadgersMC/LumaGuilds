package net.lumalyte.lg.application.services

import io.mockk.*
import net.lumalyte.lg.application.persistence.ExperienceAwardRepository
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class ExperienceBoostTest {
    private val start = Instant.parse("2026-09-18T00:00:00Z")
    private val end = start.plusSeconds(86400)
    private val source = ExperienceSource.MOB_KILL

    @Test fun `boost has exact UTC boundaries selected sources and floors the complete award`() {
        val sources = mutableSetOf(source)
        val boost = ExperienceBoost(start, end, 1.5, sources)
        sources.clear()
        assertEquals(3, boost.apply(3, source, start.minusNanos(1)))
        assertEquals(4, boost.apply(3, source, start))
        assertEquals(4, boost.apply(3, source, end.minusNanos(1)))
        assertEquals(3, boost.apply(3, source, end))
        assertEquals(3, boost.apply(3, ExperienceSource.ADMIN_BONUS, start))
        assertFailsWith<ArithmeticException> { boost.apply(Int.MAX_VALUE, source, start) }
    }

    @Test fun `invalid windows multipliers and empty source selections are rejected`() {
        for (multiplier in listOf(0.0, 0.5, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { ExperienceBoost(start, end, multiplier, setOf(source)) }
        }
        assertFailsWith<IllegalArgumentException> { ExperienceBoost(end, start, 2.0, setOf(source)) }
        assertFailsWith<IllegalArgumentException> { ExperienceBoost(start, start, 2.0, setOf(source)) }
        assertFailsWith<IllegalArgumentException> { ExperienceBoost(start, end, 2.0, emptySet()) }
    }

    @Test fun `service multiplies requested XP but never cap and resolves changes per award`() {
        val repository = mockk<ExperienceAwardRepository>()
        val activity = mockk<PlaytimeActivityService>()
        var boost: ExperienceBoost? = ExperienceBoost(start, end, 1.5, setOf(source))
        val service = PermanentExperienceService(repository, activity) { boost }
        val request = ExperienceAwardRequest(UUID.randomUUID(), null, source, 3, start)
        val policy = ExperiencePolicy(source, "MOB_KILL", 3, 20, CapPeriod.DAILY, true)
        every { repository.awardAtomically(any(), any(), any(), any()) } returns ExperienceAwardResult.Awarded(13, 13, true)
        service.award(request, policy)
        verify(exactly = 1) { repository.awardAtomically(request, policy, 13, policy.windowContaining(start)) }
        boost = null
        service.award(request, policy)
        verify(exactly = 1) { repository.awardAtomically(request, policy, 9, policy.windowContaining(start)) }
        assertEquals(20, policy.capXp)
    }

    @Test fun `ineligible activity never resolves a boost and overflow never reserves a cap`() {
        val repository = mockk<ExperienceAwardRepository>()
        val service = PermanentExperienceService(repository, mockk()) { error("Must not resolve invalid activity") }
        val request = ExperienceAwardRequest(UUID.randomUUID(), null, source, 1, start, eligible = false)
        val policy = ExperiencePolicy(source, "MOB_KILL", Int.MAX_VALUE, Int.MAX_VALUE, CapPeriod.DAILY, true)
        assertEquals(ExperienceAwardResult.Rejected(AwardRejection.INELIGIBLE), service.award(request, policy))
        val boosted = PermanentExperienceService(repository, mockk()) { ExperienceBoost(start, end, 2.0, setOf(source)) }
        assertEquals(ExperienceAwardResult.Rejected(AwardRejection.INVALID_UNITS), boosted.award(request.copy(eligible = true), policy))
        verify { repository wasNot Called }
    }
}
