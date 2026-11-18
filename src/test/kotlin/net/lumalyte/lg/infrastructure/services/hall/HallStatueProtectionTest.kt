package net.lumalyte.lg.infrastructure.services.hall

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.entity.Entity
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.Test

class HallStatueProtectionTest {
    private fun entity(statue: Boolean): Entity {
        val data = mockk<PersistentDataContainer> {
            every { has(HallStatueProtection.KEY, PersistentDataType.BYTE) } returns statue
        }
        return mockk { every { persistentDataContainer } returns data }
    }

    @Test
    fun `REQ-292 a Hall statue takes no damage, even from a creative player`() {
        val target = entity(statue = true)
        val event = mockk<EntityDamageEvent>(relaxed = true) { every { entity } returns target }
        HallStatueProtection.damage(event)
        verify { event.isCancelled = true }
    }

    @Test
    fun `other entities are left alone`() {
        val target = entity(statue = false)
        val event = mockk<EntityDamageEvent>(relaxed = true) { every { entity } returns target }
        HallStatueProtection.damage(event)
        verify(exactly = 0) { event.isCancelled = any() }
    }
}
