package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.WarBannerRepository
import net.lumalyte.lg.config.WarBannerConfig
import net.lumalyte.lg.domain.entities.WarBannerState
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarBannerServiceTest {
    private val guildId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val worldId = UUID.randomUUID()
    private val repository = FakeWarBannerRepository()
    private val physical = FakePhysicalGold()
    private var member = true
    private var permitted = true
    private var activeWar = true

    private fun subject(
        config: WarBannerConfig = WarBannerConfig(rawGoldCost = 50, cooldownMinutes = 15),
    ) = WarBannerService(
        repository = repository,
        physicalGold = physical,
        config = { config },
        isMember = { actor, guild -> member && actor == playerId && guild == guildId },
        canPlace = { actor, guild -> permitted && actor == playerId && guild == guildId },
        hasActiveWar = { guild -> activeWar && guild == guildId },
    )

    @Test
    fun `successful placement charges exact raw gold and lasts fifteen minutes`() {
        val now = 1_000_000L
        val result = subject().place(
            UUID.randomUUID(), playerId, guildId, worldId, 10, 64, -5, now
        ) { true }

        val placed = assertIs<WarBannerPlacementResult.Placed>(result)
        assertEquals(50L, placed.cost)
        assertEquals(now + WarBannerService.ACTIVE_DURATION_MILLIS, placed.state.expiresAt)
        assertEquals(now + 15 * 60_000L, placed.state.cooldownUntil)
        assertEquals(listOf(50L), physical.reservedValues)
        assertEquals(1, physical.commitCount)
        assertEquals(0, physical.restoreCount)
        assertEquals(placed.state, repository.state)
    }

    @Test
    fun `membership permission and active war reject before payment`() {
        member = false
        assertIs<WarBannerPlacementResult.NotMember>(place())
        member = true
        permitted = false
        assertIs<WarBannerPlacementResult.NoPermission>(place())
        permitted = true
        activeWar = false
        assertIs<WarBannerPlacementResult.NoActiveWar>(place())
        assertEquals(0, physical.reserveCount)
    }

    @Test
    fun `one active banner and cooldown do not charge twice`() {
        val service = subject()
        val now = 2_000_000L
        assertIs<WarBannerPlacementResult.Placed>(place(service, now))
        assertIs<WarBannerPlacementResult.ActiveBanner>(place(service, now + 1))
        assertEquals(1, physical.reserveCount)

        val state = requireNotNull(repository.state)
        assertNotNull(service.destroyAt(state.worldId, state.x, state.y, state.z))
        val cooldown = assertIs<WarBannerPlacementResult.Cooldown>(place(service, now + 2))
        assertTrue(cooldown.remainingMillis > 0)
        assertEquals(1, physical.reserveCount)

        assertIs<WarBannerPlacementResult.Placed>(
            place(service, state.cooldownUntil + 1)
        )
        assertEquals(2, physical.reserveCount)
    }

    @Test
    fun `render failure restores gold and removes first placement state`() {
        val result = subject().place(
            UUID.randomUUID(), playerId, guildId, worldId, 1, 70, 1, 5_000L
        ) { false }

        val failed = assertIs<WarBannerPlacementResult.Failed>(result)
        assertTrue(failed.compensated)
        assertNull(repository.state)
        assertEquals(1, physical.restoreCount)
        assertEquals(0, physical.commitCount)
    }

    @Test
    fun `proven non consumption restores gold and tactical state`() {
        physical.commitResult = PhysicalCommitResult.NotConsumed
        val result = place(subject(), 10_000L)

        val failed = assertIs<WarBannerPlacementResult.Failed>(result)
        assertTrue(failed.compensated)
        assertNull(repository.state)
        assertEquals(1, physical.restoreCount)
        assertEquals(1, physical.commitCount)
    }

    @Test
    fun `unknown commit retains deployed banner and does not refund ambiguous payment`() {
        physical.commitResult = PhysicalCommitResult.Unknown
        val result = place(subject(), 20_000L)

        val unknown = assertIs<WarBannerPlacementResult.PaymentUnknown>(result)
        assertEquals(unknown.state, repository.state)
        assertTrue(requireNotNull(repository.state).active)
        assertEquals(0, physical.restoreCount)
        assertEquals(1, physical.commitCount)
    }

    @Test
    fun `failed redeploy restores prior inactive cooldown row`() {
        val service = subject()
        val first = assertIs<WarBannerPlacementResult.Placed>(place(service, 30_000L))
        assertNotNull(service.destroyAt(first.state.worldId, first.state.x, first.state.y, first.state.z))

        val afterCooldown = first.state.cooldownUntil + 1
        physical.commitResult = PhysicalCommitResult.NotConsumed
        assertIs<WarBannerPlacementResult.Failed>(place(service, afterCooldown))

        val restored = requireNotNull(repository.state)
        assertEquals(first.state.bannerId, restored.bannerId)
        assertFalse(restored.active)
        assertEquals(first.state.cooldownUntil, restored.cooldownUntil)
    }

    @Test
    fun `expired banner becomes inactive and member lookup remains membership gated`() {
        val service = subject()
        val placed = assertIs<WarBannerPlacementResult.Placed>(place(service, 40_000L))
        assertNotNull(service.activeForMember(playerId, guildId, 40_001L))

        member = false
        assertNull(service.activeForMember(playerId, guildId, 40_001L))
        member = true

        assertNull(service.active(guildId, placed.state.expiresAt))
        assertTrue(requireNotNull(repository.state).active)
        assertEquals(listOf(placed.state), service.expireDue(placed.state.expiresAt))
        assertFalse(requireNotNull(repository.state).active)
    }

    @Test
    fun `expiration deactivates the tactical point exactly at fifteen minutes`() {
        val service = subject()
        val now = 50_000L
        val placed = assertIs<WarBannerPlacementResult.Placed>(place(service, now))

        assertTrue(service.expireDue(placed.state.expiresAt - 1).isEmpty())
        assertTrue(requireNotNull(repository.state).active)

        assertEquals(listOf(placed.state), service.expireDue(placed.state.expiresAt))
        assertFalse(requireNotNull(repository.state).active)
        assertNull(service.active(guildId, placed.state.expiresAt))
    }

    private fun place(
        service: WarBannerService = subject(),
        now: Long = 1_000L,
    ): WarBannerPlacementResult = service.place(
        transactionId = UUID.randomUUID(),
        playerId = playerId,
        guildId = guildId,
        worldId = worldId,
        x = 3,
        y = 80,
        z = 4,
        now = now,
        render = { true },
    )

    private class FakeWarBannerRepository : WarBannerRepository {
        var state: WarBannerState? = null

        override fun get(guildId: UUID): WarBannerState? =
            state?.takeIf { it.guildId == guildId }

        override fun getActiveAt(
            worldId: UUID,
            x: Int,
            y: Int,
            z: Int,
        ): WarBannerState? = state?.takeIf {
            it.active && it.worldId == worldId && it.x == x && it.y == y && it.z == z
        }

        override fun savePlacement(state: WarBannerState): Boolean {
            this.state = state
            return true
        }

        override fun delete(guildId: UUID, bannerId: UUID): Boolean {
            val current = state ?: return false
            if (current.guildId != guildId || current.bannerId != bannerId) return false
            state = null
            return true
        }

        override fun deactivate(guildId: UUID, bannerId: UUID): Boolean {
            val current = state ?: return false
            if (!current.active || current.guildId != guildId || current.bannerId != bannerId) return false
            state = current.copy(active = false)
            return true
        }

        override fun expiredActive(now: Long): List<WarBannerState> =
            listOfNotNull(state?.takeIf { it.active && it.expiresAt <= now })
    }

    private class FakePhysicalGold : PhysicalGoldPort {
        var reserveMode = "reserved"
        var commitResult = PhysicalCommitResult.Committed
        var reserveCount = 0
        var commitCount = 0
        var restoreCount = 0
        val reservedValues = mutableListOf<Long>()

        override fun reserve(
            transactionId: UUID,
            playerId: UUID,
            requestedValue: Long,
        ): PhysicalReservationResult {
            reserveCount++
            reservedValues += requestedValue
            return when (reserveMode) {
                "insufficient" -> PhysicalReservationResult.Insufficient
                "unavailable" -> PhysicalReservationResult.Unavailable
                "unknown" -> PhysicalReservationResult.Unknown
                else -> PhysicalReservationResult.Reserved(
                    PhysicalGoldReservation(transactionId, playerId, requestedValue)
                )
            }
        }

        override fun commit(reservation: PhysicalGoldReservation): PhysicalCommitResult {
            commitCount++
            return commitResult
        }

        override fun restore(reservation: PhysicalGoldReservation): Boolean {
            restoreCount++
            return true
        }

        override fun deliver(
            playerId: UUID,
            value: Long,
            transactionId: UUID,
        ) = ExternalTransferResult.Applied
    }
}
