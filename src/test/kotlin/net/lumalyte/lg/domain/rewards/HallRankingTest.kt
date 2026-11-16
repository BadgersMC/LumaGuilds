package net.lumalyte.lg.domain.rewards

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class HallRankingTest {
    private fun standing(prestige: Int, at: Long) = HallStanding(UUID.randomUUID(), prestige, Instant.ofEpochSecond(at))

    @Test
    fun `REQ-275 highest prestige first, ties go to whoever got there first, unprestiged guilds never stand`() {
        val early = standing(3, 100)
        val late = standing(3, 200)
        val top = standing(5, 900)
        val none = standing(0, 1)
        assertEquals(listOf(top, early, late), HallRanking.rank(listOf(late, none, early, top), 6))
        assertEquals(listOf(top), HallRanking.rank(listOf(late, early, top), 1))
    }

    @Test
    fun `REQ-275 pedestals fill in slot order and spare pedestals stay empty`() {
        val a = standing(2, 1)
        val b = standing(1, 1)
        val pedestals = listOf(3, 1, 2).map { HallPedestal(it, "world", 0.0, 64.0, 0.0, 0f) }
        val assigned = HallRanking.assign(pedestals, listOf(b, a))
        assertEquals(listOf(1, 2, 3), assigned.map { it.first.slot })
        assertEquals(a, assigned[0].second)
        assertEquals(b, assigned[1].second)
        assertNull(assigned[2].second)
    }
}
