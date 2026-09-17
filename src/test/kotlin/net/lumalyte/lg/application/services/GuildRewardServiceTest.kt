package net.lumalyte.lg.application.services

import io.mockk.*
import net.lumalyte.lg.application.persistence.RewardStateRepository
import net.lumalyte.lg.domain.rewards.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

class GuildRewardServiceTest {
    private val guild = UUID.randomUUID()
    private val repository = mockk<RewardStateRepository>()
    private var enabled = false
    private var ceiling = 100_000L
    private var members = 50
    private val service = GuildRewardService(repository, RewardCatalog.chapterTwo(),
        { RewardReadSettings(enabled, ceiling, members) })

    @Test fun `disabled reads never touch storage`() {
        assertEquals(GuildRewardRead.Disabled, service.read(guild))
        assertNull(service.entitlementsIfEnabled(guild))
        verify { repository wasNot Called }
    }

    @Test fun `ownership and live config determine benefits without level grants`() {
        enabled = true
        every { repository.read(guild) } returns RewardStateRead.Found(100,
            RewardOwnershipSnapshot(4, RewardOwnership(currentRun = setOf("bank-1", "fee-1"),
                permanent = setOf("home-1"), initialHomeCapacity = 6)))
        val first = assertIs<GuildRewardRead.Available>(service.read(guild))
        assertEquals(4, first.version)
        assertEquals(48_500L, first.entitlements.bankCapacity)
        assertEquals(7, first.entitlements.homeCapacity)
        assertEquals(50, first.entitlements.memberCapacity)
        assertEquals(0.9, first.entitlements.withdrawalFeeMultiplier)
        assertEquals(1.0, first.entitlements.homeCooldownMultiplier)
        assertFalse(first.entitlements.allyHomes)
        ceiling = 10_000
        members = 31
        val next = service.entitlementsIfEnabled(guild)!!
        assertEquals(10_000L, next.bankCapacity)
        assertEquals(31, next.memberCapacity)
        assertEquals(48_500L, first.entitlements.bankCapacity)
        verify(exactly = 2) { repository.read(guild) }
    }

    @Test fun `missing failed invalid and thrown reads never become legacy defaults`() {
        enabled = true
        val outcomes = listOf(RewardStateRead.Missing, RewardStateRead.Failed,
            RewardStateRead.Found(101, RewardOwnershipSnapshot(0, RewardOwnership())),
            RewardStateRead.Found(1, RewardOwnershipSnapshot(0, RewardOwnership(currentRun = setOf("fee-1")))))
        for (outcome in outcomes) {
            every { repository.read(guild) } returns outcome
            assertEquals(GuildRewardRead.Unavailable, service.read(guild))
            assertFailsWith<RewardStateUnavailableException> { service.entitlementsIfEnabled(guild) }
        }
        every { repository.read(guild) } throws IllegalStateException("database offline")
        assertEquals(GuildRewardRead.Unavailable, service.read(guild))
    }
}
