package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.rewards.HallPedestal
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HallPedestalRepositorySQLTest : RewardSqlTestFixture() {
    @Test
    fun `REQ-275 pedestals are placed, moved, listed in slot order and removed`() {
        val storage = openStorage()
        val repository = HallPedestalRepositorySQL(storage)
        repository.set(HallPedestal(2, "world", 10.5, 64.0, -3.5, 90f))
        repository.set(HallPedestal(1, "world", 1.5, 64.0, 1.5, 0f))
        repository.set(HallPedestal(2, "world", 12.5, 65.0, -3.5, 180f))
        assertEquals(listOf(HallPedestal(1, "world", 1.5, 64.0, 1.5, 0f), HallPedestal(2, "world", 12.5, 65.0, -3.5, 180f)),
            HallPedestalRepositorySQL(storage).all())
        assertTrue(repository.remove(1))
        assertFalse(repository.remove(1))
        assertEquals(listOf(2), repository.all().map { it.slot })
    }
}
