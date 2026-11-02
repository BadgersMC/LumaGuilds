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
    fun creationDebitsDigitalVaultBalanceAndRefundsWhenCreationFails() {
        val economy = FakePersonalEconomy()
        val service = service(config(enabled = true, create = 250), economy = economy)
        val player = UUID.randomUUID()

        val result = service.createGuild(UUID.randomUUID(), player) { null }

        assertTrue(result is GuildCreationCostResult.CreationFailed)
        assertTrue((result as GuildCreationCostResult.CreationFailed).compensated)
        assertEquals(250L, economy.lastDebited)
        assertEquals(250L, economy.lastCredited)
        assertEquals(1000L, economy.currentBalance)
    }

    @Test
    fun successfulCreationChargesDigitalVaultBalanceExactlyOnce() {
        val economy = FakePersonalEconomy()
        val service = service(config(enabled = true, create = 250), economy = economy)
        val guild = mockk<Guild>()

        val result = service.createGuild(UUID.randomUUID(), UUID.randomUUID()) { guild }

        assertEquals(GuildCreationCostResult.Applied(guild, 250), result)
        assertEquals(250L, economy.lastDebited)
        assertEquals(null, economy.lastCredited)
        assertEquals(750L, economy.currentBalance)
    }

    @Test
    fun insufficientDigitalVaultBalanceNeverAttemptsCreation() {
        val economy = FakePersonalEconomy(currentBalance = 100L)
        val service = service(config(enabled = true, create = 250), economy = economy)
        var created = false

        val result = service.createGuild(UUID.randomUUID(), UUID.randomUUID()) {
            created = true
            mockk<Guild>()
        }

        assertEquals(GuildCreationCostResult.InsufficientGold, result)
        assertEquals(null, economy.lastDebited)
        assertTrue(!created)
    }

    @Test
    fun unavailableDigitalVaultPaymentDoesNotCreateGuild() {
        val economy = FakePersonalEconomy(currentBalance = null)
        val service = service(config(enabled = true, create = 250), economy = economy)

        val result = service.createGuild(UUID.randomUUID(), UUID.randomUUID()) { mockk<Guild>() }

        assertEquals(GuildCreationCostResult.PaymentUnavailable, result)
        assertEquals(null, economy.lastDebited)
    }

    @Test
    fun rejectedDigitalVaultDebitDoesNotCreateGuild() {
        val economy = FakePersonalEconomy(debitResult = ExternalTransferResult.Rejected("provider denied"))
        val service = service(config(enabled = true, create = 250), economy = economy)
        var created = false

        val result = service.createGuild(UUID.randomUUID(), UUID.randomUUID()) {
            created = true
            mockk<Guild>()
        }

        assertEquals(GuildCreationCostResult.PaymentUnavailable, result)
        assertTrue(!created)
        assertEquals(null, economy.lastCredited)
    }

    @Test
    fun disabledChapterTwoCostsDoNotChargeDigitalVaultBalance() {
        val economy = FakePersonalEconomy(currentBalance = null)
        val service = service(config(enabled = false), economy = economy)
        val guild = mockk<Guild>()

        val result = service.createGuild(UUID.randomUUID(), UUID.randomUUID()) { guild }

        assertEquals(GuildCreationCostResult.Applied(guild, 0), result)
        assertEquals(null, economy.lastDebited)
    }

    @Test
    fun failedCreationReportsUncompensatedPaymentIfRefundFails() {
        val economy = FakePersonalEconomy(creditResult = ExternalTransferResult.Rejected("refund denied"))
        val service = service(config(enabled = true, create = 250), economy = economy)

        val result = service.createGuild(UUID.randomUUID(), UUID.randomUUID()) { null }

        assertEquals(GuildCreationCostResult.CreationFailed(compensated = false), result)
        assertEquals(250L, economy.lastDebited)
        assertEquals(250L, economy.lastCredited)
    }

    @Test
    fun uncertainDigitalVaultDebitDoesNotCreateOrSpeculativelyRefund() {
        val economy = FakePersonalEconomy(debitResult = ExternalTransferResult.Failed("timeout"))
        val service = service(config(enabled = true, create = 250), economy = economy)
        val tx = UUID.randomUUID()

        val result = service.createGuild(tx, UUID.randomUUID()) { mockk<Guild>() }

        assertEquals(GuildCreationCostResult.Uncertain(null, tx), result)
        assertEquals(null, economy.lastCredited)
    }

    @Test
    fun creationExceptionLeavesDigitalDebitForAdministratorReconciliation() {
        val economy = FakePersonalEconomy()
        val service = service(config(enabled = true, create = 250), economy = economy)
        val tx = UUID.randomUUID()

        val result = service.createGuild(tx, UUID.randomUUID()) {
            error("creation became uncertain after persistence")
        }

        assertTrue(result is GuildCreationCostResult.Uncertain)
        assertEquals(tx, (result as GuildCreationCostResult.Uncertain).transactionId)
        assertEquals(null, result.guild)
        assertEquals(250L, economy.lastDebited)
        assertEquals(null, economy.lastCredited)
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
        economy: PersonalEconomyPort = FakePersonalEconomy(),
        gold: GuildGoldService = mockk(relaxed = true),
    ) = GuildCostService({ cfg }, economy, gold)

    private fun config(enabled: Boolean, create: Int = 100, base: Int = 100, scale: Double = 2.0) =
        MainConfig(
            guild = GuildConfig(
                createGuildCost = create,
                homeActivationBaseCost = base,
                homeActivationScale = scale,
            ),
            chapterTwoGoldCostsEnabled = enabled,
        )

    private class FakePersonalEconomy(
        var currentBalance: Long? = 1000L,
        val debitResult: ExternalTransferResult = ExternalTransferResult.Applied,
        val creditResult: ExternalTransferResult = ExternalTransferResult.Applied,
    ) : PersonalEconomyPort {
        var lastDebited: Long? = null
        var lastCredited: Long? = null
        override fun isAvailable() = currentBalance != null
        override fun balance(playerId: UUID) = currentBalance
        override fun debit(playerId: UUID, amount: Long): ExternalTransferResult {
            lastDebited = amount
            if (debitResult == ExternalTransferResult.Applied) currentBalance = currentBalance!! - amount
            return debitResult
        }
        override fun credit(playerId: UUID, amount: Long): ExternalTransferResult {
            lastCredited = amount
            if (creditResult == ExternalTransferResult.Applied) currentBalance = currentBalance!! + amount
            return creditResult
        }
    }
}
