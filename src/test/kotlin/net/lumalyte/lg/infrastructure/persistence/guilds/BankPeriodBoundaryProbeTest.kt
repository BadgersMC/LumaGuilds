package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.BankDepositXpStatus
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Regression expectations: recycled deposits must not become fresh XP at midnight. */
abstract class BankContributionContract : BankDepositDeliveryFixture() {

    @Test fun sameDayWithdrawAndRedepositIsBlocked() {
        val first = personalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        assertEquals(10, delivery.complete(first.transactionId)!!.acceptedXp)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 1000)))
        val recycled = personalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(recycled))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(recycled.transactionId)!!.status)
    }

    @Test fun withdrawBeforeMidnightAndRedepositTomorrowMustNotRepeatXp() {
        val first = personalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        assertEquals(10, delivery.complete(first.transactionId)!!.acceptedXp)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 1000)))
        now = occurredAt.plusSeconds(86400)
        quote = quote.copy(occurredAt = now)
        restart()
        val recycled = personalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(recycled))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(recycled.transactionId)!!.status)
    }

    @Test fun debtIsGuildWideAndFreshPartStillEarnsAfterRestart() {
        val first = personalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        assertEquals(10, delivery.complete(first.transactionId)!!.acceptedXp)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 600)))
        now = occurredAt.plusSeconds(86400)
        quote = quote.copy(occurredAt = now)
        restart()
        val other = physicalRequest(amount = 1000).copy(playerId = java.util.UUID.randomUUID())
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(other))
        assertEquals(4, delivery.complete(other.transactionId)!!.acceptedXp)
        assertEquals(400, contribution(other.transactionId))
        assertEquals(0, debt())
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(other))
        assertEquals(4, delivery.complete(other.transactionId)!!.acceptedXp)
        assertEquals(0, debt())
    }

    @Test fun failedPhysicalPayoutDoesNotCreateDebt() {
        val first = personalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        delivery.complete(first.transactionId)
        kotlin.test.assertFalse(service.withdrawPhysical(physicalRequest(amount = 500)) is GuildGoldResult.Applied)
        assertEquals(1000, gold.getBalance(guild))
        assertEquals(0, debt())
        now = occurredAt.plusSeconds(86400)
        quote = quote.copy(occurredAt = now)
        restart()
        val fresh = personalRequest(amount = 100)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(fresh))
        assertEquals(1, delivery.complete(fresh.transactionId)!!.acceptedXp)
    }

    @Test fun feesAreNotRecycledPrincipalAndDebtSurvivesPrestige() {
        val first = personalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        delivery.complete(first.transactionId)
        moneyPolicy = moneyPolicy.copy(withdrawalFeePercent = 0.1)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 500)))
        assertEquals(500, debt())
        execute("UPDATE guild_reward_accounts SET prestige_count = 1 WHERE guild_id = ?", guild.toString())
        now = occurredAt.plusSeconds(86400)
        quote = quote.copy(occurredAt = now)
        restart()
        val returned = personalRequest(amount = 500)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(returned))
        assertEquals(0, contribution(returned.transactionId))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(returned.transactionId)!!.status)
        assertEquals(0, debt())
    }

    @Test fun realGuildSpendingDoesNotCreateWithdrawalDebt() {
        val first = personalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        delivery.complete(first.transactionId)
        assertIs<GuildGoldResult.Applied>(service.debitSystem(java.util.UUID.randomUUID(), guild, actor, 1000, "Guild purchase"))
        assertEquals(0, debt())
        now = occurredAt.plusSeconds(86400)
        quote = quote.copy(occurredAt = now)
        restart()
        val refill = physicalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(refill))
        assertEquals(1000, contribution(refill.transactionId))
        assertEquals(10, delivery.complete(refill.transactionId)!!.acceptedXp)
    }

    @Test fun cutoverKeepsConfirmedWithdrawalDebtAndLegacyReadyDoesNotMint() {
        val first = personalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        delivery.complete(first.transactionId)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 1000)))
        execute("DELETE FROM guild_bank_contribution_receipts")
        execute("DELETE FROM guild_bank_contribution_accounts")
        now = occurredAt.plusSeconds(86400)
        quote = quote.copy(occurredAt = now)
        restart()
        val returned = personalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(returned))
        assertEquals(0, contribution(returned.transactionId))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(returned.transactionId)!!.status)
        val fresh = personalRequest(amount = 100)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(fresh))
        execute("DELETE FROM guild_bank_contribution_receipts WHERE transaction_id = ?", fresh.transactionId.toString())
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(fresh.transactionId)!!.status)
    }

    @Test fun contributionWriteFailureRollsBackBankAndDebtThenRecoverySettlesOnce() {
        val first = personalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        delivery.complete(first.transactionId)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 500)))
        rejectInsert("guild_bank_contribution_receipts")
        val recycled = personalRequest(amount = 500)
        assertIs<GuildGoldResult.Failed>(service.depositPersonal(recycled))
        assertEquals(500, gold.getBalance(guild))
        assertEquals(500, debt())
        allowDeliveryWrites()
        restart()
        service.reconcilePending(Long.MAX_VALUE)
        assertEquals(1000, gold.getBalance(guild))
        assertEquals(0, debt())
        assertEquals(0, contribution(recycled.transactionId))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(recycled.transactionId)!!.status)
        service.reconcilePending(Long.MAX_VALUE)
        assertEquals(2, personal.debitCalls)
    }

    @Test fun fractionalFreshContributionsAccumulateAfterPartialDebtRepayment() {
        val first = personalRequest(amount = 150)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        assertEquals(1, delivery.complete(first.transactionId)!!.acceptedXp)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 100)))
        now = occurredAt.plusSeconds(86400)
        quote = quote.copy(occurredAt = now)
        val mixed = physicalRequest(amount = 125)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(mixed))
        assertEquals(25, contribution(mixed.transactionId))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(mixed.transactionId)!!.status)
        restart()
        val final = physicalRequest(amount = 25)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(final))
        assertEquals(1, delivery.complete(final.transactionId)!!.acceptedXp)
    }

    @Test fun differentRepositoryInstancesCannotPrepareADepositPastAnUnfinishedWithdrawal() {
        val start = java.util.concurrent.CountDownLatch(1)
        val deposit = net.lumalyte.lg.domain.gold.GuildGoldMutation(java.util.UUID.randomUUID(), guild, actor,
            net.lumalyte.lg.domain.gold.GuildGoldRoute.PERSONAL_ACCOUNT, net.lumalyte.lg.domain.gold.GuildGoldDirection.CREDIT, 100, 0, "Deposit")
        val withdrawal = deposit.copy(transactionId = java.util.UUID.randomUUID(), direction = net.lumalyte.lg.domain.gold.GuildGoldDirection.DEBIT)
        val other = GuildGoldRepositorySQL(db)
        val credit = java.util.concurrent.CompletableFuture.supplyAsync { start.await(); gold.prepareDeposit(deposit, quote) }
        val debit = java.util.concurrent.CompletableFuture.supplyAsync { start.await(); other.prepare(withdrawal) }
        start.countDown()
        val results = listOf(credit.get(15, java.util.concurrent.TimeUnit.SECONDS), debit.get(15, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(1, results.count { it is net.lumalyte.lg.domain.gold.GuildGoldPreparation.New })
        assertEquals(1, results.count { it is net.lumalyte.lg.domain.gold.GuildGoldPreparation.Pending })
    }

    @Test fun upgradeDoesNotMisclassifyHistoricalGuildSpendingAsPlayerWithdrawal() {
        val first = personalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        delivery.complete(first.transactionId)
        assertIs<GuildGoldResult.Applied>(service.debitSystem(java.util.UUID.randomUUID(), guild, actor, 1000, "Real purchase"))
        execute("DELETE FROM guild_bank_contribution_receipts")
        execute("DELETE FROM guild_bank_contribution_accounts")
        now = occurredAt.plusSeconds(86400)
        quote = quote.copy(occurredAt = now)
        restart()
        val fresh = physicalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(fresh))
        assertEquals(0, debt())
        assertEquals(1000, contribution(fresh.transactionId))
        assertEquals(10, delivery.complete(fresh.transactionId)!!.acceptedXp)
    }

    @Test fun physicalWithdrawalAndAnotherPlayersPersonalRedepositRemainRecycledTomorrow() {
        val first = physicalRequest(amount = 1000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(first))
        delivery.complete(first.transactionId)
        physical.deliverResult = net.lumalyte.lg.application.services.ExternalTransferResult.Applied
        assertIs<GuildGoldResult.Applied>(service.withdrawPhysical(physicalRequest(amount = 1000)))
        assertEquals(1000, debt())
        now = occurredAt.plusSeconds(86400)
        quote = quote.copy(occurredAt = now)
        restart()
        val returned = personalRequest(amount = 1000).copy(playerId = java.util.UUID.randomUUID())
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(returned))
        assertEquals(0, contribution(returned.transactionId))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(returned.transactionId)!!.status)
    }

    protected fun contribution(id: java.util.UUID): Long = checkNotNull(scalar(
        "SELECT fresh_after - fresh_before FROM guild_bank_contribution_receipts WHERE transaction_id = ?", id.toString()))
    protected fun debt(): Long = scalar("SELECT withdrawal_debt FROM guild_bank_contribution_accounts WHERE guild_id = ?", guild.toString()) ?: 0
}

class BankPeriodBoundaryProbeTest : BankContributionContract() {
    @TempDir lateinit var directory: Path
    override fun newStorage() = VirtualThreadSQLiteStorage(directory.toFile())
}

class BankContributionMariaDbTest : BankContributionContract() {
    override fun newStorage(): net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage {
        val port = System.getenv("GUILD_SHOP_XP_TEST_MARIA_PORT")?.toIntOrNull()
        org.junit.jupiter.api.Assumptions.assumeTrue(port != null, "Disposable loopback MariaDB XP test instance not configured")
        return net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage("127.0.0.1", checkNotNull(port), "lg_shop_xp_test", "xp_test", "xp_test")
    }
}
