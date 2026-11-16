package net.lumalyte.lg.infrastructure.services.hall

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.entity.Entity
import org.bukkit.event.entity.EntitySpawnEvent
import org.junit.jupiter.api.Test
import java.util.UUID

class HallSpawnGuardTest {
    private fun event(id: UUID, cancelled: Boolean): EntitySpawnEvent {
        val entity = mockk<Entity> { every { uniqueId } returns id }
        return mockk(relaxed = true) {
            every { this@mockk.entity } returns entity
            every { isCancelled } returns cancelled
        }
    }

    @Test
    fun `a statue being spawned gets through a protection plugin's cancel`() {
        val id = UUID.randomUUID()
        val event = event(id, cancelled = true)
        HallSpawnGuard.allow(id)
        try { HallSpawnGuard.spawn(event) } finally { HallSpawnGuard.done(id) }
        verify { event.isCancelled = false }
    }

    @Test
    fun `other entities and finished statues are left alone`() {
        val other = event(UUID.randomUUID(), cancelled = true)
        HallSpawnGuard.spawn(other)
        verify(exactly = 0) { other.isCancelled = any() }

        val id = UUID.randomUUID()
        HallSpawnGuard.allow(id)
        HallSpawnGuard.done(id)
        val late = event(id, cancelled = true)
        HallSpawnGuard.spawn(late)
        verify(exactly = 0) { late.isCancelled = any() }
    }
}
