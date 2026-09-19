package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.config.GuildConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.gold.GuildGoldRoute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class GuildCostServiceTest {
    @Test
    fun homeCostUsesConfiguredExponentialOrdinal() {
        val service = service(config(enabled = true, base = 100, scale = 2.0))
        assertEquals(100, service.homeActivationCost(1))
        assertEquals(200, service.homeActivationCost(2))
        assertEquals(400, service.homeActivationCost(3))
    }

    @Test
    fun creationReservesPhysicalGoldAndRestoresWhenCreationFails() {
        val physical = FakePhysicalGold()
        val service = service(config(enabled = true, create = 250), physical = physical)
        val player = UUID.randomUUID()

        val result = service.createGuild(UUID.randomUUID(), player) { null }

        assertTrue(result is GuildCreationCostResult.CreationFailed)
        assertEquals(250, physical.lastReserved)
        assertTrue(physical.restored)
    }

    @Test
    fun existingHomeMoveIsNotChargedByActivationService() {
        val gold = mockk<GuildGoldService>(relaxed = true)
        val service = service(config(enabled = true, base = 100, scale = 2.0), gold = gold)

        val result = service.activateHome(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2, alreadyActivated = true) { true }

        assertTrue(result is HomeActivationCostResult.Applied)
        verify(exactly = 0) { gold.debitSystem(any(), any(), any(), any(), any()) }
    }

    @Test
    fun failedHomePersistenceCompensatesCanonicalGold() {
        val guildId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val tx = UUID.randomUUID()
        val gold = mockk<GuildGoldService>()
        every { gold.debitSystem(tx, guildId, actorId, 100, any()) } returns GuildGoldResult.Applied(tx, 500, 400, 0)
        every { gold.creditSystem(any(), guildId, actorId, 100, GuildGoldRoute.SYSTEM, any()) } returns
            GuildGoldResult.Applied(UUID.randomUUID(), 400, 500, 0)
        val service = service(config(enabled = true, base = 100, scale = 2.0), gold = gold)

        val result = service.activateHome(tx, guildId, actorId, 1, alreadyActivated = false) { false }

        assertTrue(result is HomeActivationCostResult.ActivationFailed)
        assertTrue((result as HomeActivationCostResult.ActivationFailed).compensated)
    }

    private fun service(
        cfg: MainConfig,
        physical: PhysicalGoldPort = FakePhysicalGold(),
        gold: GuildGoldService = mockk(relaxed = true),
    ) = GuildCostService({ cfg }, physical, gold)

    private fun config(enabled: Boolean, create: Int = 100, base: Int = 100, scale: Double = 2.0) =
        MainConfig(
            guild = GuildConfig(
                createGuildCost = create,
                homeActivationBaseCost = base,
                homeActivationScale = scale,
            ),
            chapterTwoGoldCostsEnabled = enabled,
        )

    private class FakePhysicalGold : PhysicalGoldPort {
        var lastReserved: Long? = null
        var restored = false
        override fun reserve(transactionId: UUID, playerId: UUID, requestedValue: Long): PhysicalReservationResult {
            lastReserved = requestedValue
            return PhysicalReservationResult.Reserved(PhysicalGoldReservation(transactionId, playerId, requestedValue))
        }
        override fun commit(reservation: PhysicalGoldReservation) = PhysicalCommitResult.Committed
        override fun restore(reservation: PhysicalGoldReservation): Boolean { restored = true; return true }
        override fun deliver(playerId: UUID, value: Long, transactionId: UUID) = ExternalTransferResult.Applied
    }
}
