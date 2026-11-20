package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.MemberCapacityFloors
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.GuildProgression
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-190: the member service applies level, prestige and floor. */
class MemberCapacityServiceTest {
    private val guild = UUID.randomUUID()
    private val progression = mockk<ProgressionRepository>()
    private val floors = mockk<MemberCapacityFloors>()
    private var prestige: Int? = 0
    private val config = MainConfig()

    private fun service() = MemberServiceBukkit(
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), progression,
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), null,
        memberCapacity = { config },
        prestigeCount = { prestige },
        capacityFloors = floors,
    )

    private fun level(n: Int) {
        every { progression.getGuildProgression(guild) } returns mockk<GuildProgression>(relaxed = true).also {
            every { it.currentLevel } returns n
        }
    }

    @Test
    fun `level, prestige and floor combine`() {
        every { floors.floor(guild) } returns null
        level(12)
        assertEquals(20, service().getMemberLimit(guild))
        prestige = 1
        level(1)
        assertEquals(100, service().getMemberLimit(guild))
        prestige = 0
        every { floors.floor(guild) } returns 37
        assertEquals(37, service().getMemberLimit(guild))
    }

    @Test
    fun `unreadable level or prestige counts as level 1 and not prestiged`() {
        every { floors.floor(guild) } returns null
        every { progression.getGuildProgression(guild) } throws IllegalStateException("db down")
        prestige = null
        assertEquals(10, service().getMemberLimit(guild))
    }
}
