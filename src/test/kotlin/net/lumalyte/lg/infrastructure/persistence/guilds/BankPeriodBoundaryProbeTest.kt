package net.lumalyte.lg.infrastructure.persistence.guilds

import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import net.lumalyte.lg.application.services.ExternalTransferResult
import net.lumalyte.lg.domain.entities.BankDepositXpStatus
import net.lumalyte.lg.domain.gold.GuildGoldDirection
import net.lumalyte.lg.domain.gold.GuildGoldMutation
import net.lumalyte.lg.domain.gold.GuildGoldPreparation
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.gold.GuildGoldRoute
import net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Regression expectations: recycled deposits must not become fresh XP at midnight. */
// Paired database acceptance scenarios intentionally retain explicit fixture values and independent cases.
@Suppress("MagicNumber", "TooManyFunctions")
internal abstract class BankContributionContract : BankDepositDeliveryFixture() {
    /** Same Day Withdraw And Redeposit Is Blocked. */
    @Test fun sameDayReturn() {
        val first = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        assertEquals(10, delivery.complete(first.transactionId)!!.acceptedXp)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 1_000)))
        val recycled = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(recycled))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(recycled.transactionId)!!.status)
    }

    /** Withdraw Before Midnight And Redeposit Tomorrow Must Not Repeat Xp. */
    @Test fun dailyRollover() {
        val first = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        assertEquals(10, delivery.complete(first.transactionId)!!.acceptedXp)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 1_000)))
        now = occurredAt.plusSeconds(86_400)
        quote = quote.copy(occurredAt = now)
        restart()
        val recycled = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(recycled))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(recycled.transactionId)!!.status)
    }

    /** Debt Is Guild Wide And Fresh Part Still Earns After Restart. */
    @Test fun mixedFreshAcrossActors() {
        val first = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        assertEquals(10, delivery.complete(first.transactionId)!!.acceptedXp)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 600)))
        now = occurredAt.plusSeconds(86_400)
        quote = quote.copy(occurredAt = now)
        restart()
        val other = physicalRequest(amount = 1_000).copy(playerId = UUID.randomUUID())
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(other))
        assertEquals(4, delivery.complete(other.transactionId)!!.acceptedXp)
        assertEquals(400, contribution(other.transactionId))
        assertEquals(0, debt())
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(other))
        assertEquals(4, delivery.complete(other.transactionId)!!.acceptedXp)
        assertEquals(0, debt())
    }

    /** Failed Physical Payout Does Not Create Debt. */
    @Test fun failedPayout() {
        val first = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        delivery.complete(first.transactionId)
        kotlin.test.assertFalse(service.withdrawPhysical(physicalRequest(amount = 500)) is GuildGoldResult.Applied)
        assertEquals(1_000, gold.getBalance(guild))
        assertEquals(0, debt())
        now = occurredAt.plusSeconds(86_400)
        quote = quote.copy(occurredAt = now)
        restart()
        val fresh = personalRequest(amount = 100)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(fresh))
        assertEquals(1, delivery.complete(fresh.transactionId)!!.acceptedXp)
    }

    /** Fees Are Not Recycled Principal And Debt Survives Prestige. */
    @Test fun feesAndPrestige() {
        val first = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        delivery.complete(first.transactionId)
        moneyPolicy = moneyPolicy.copy(withdrawalFeePercent = 0.1)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 500)))
        assertEquals(500, debt())
        execute("UPDATE guild_reward_accounts SET prestige_count = 1 WHERE guild_id = ?", guild.toString())
        now = occurredAt.plusSeconds(86_400)
        quote = quote.copy(occurredAt = now)
        restart()
        val returned = personalRequest(amount = 500)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(returned))
        assertEquals(0, contribution(returned.transactionId))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(returned.transactionId)!!.status)
        assertEquals(0, debt())
    }

    /** Real Guild Spending Does Not Create Withdrawal Debt. */
    @Test fun spendingRefill() {
        val first = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        delivery.complete(first.transactionId)
        assertIs<GuildGoldResult.Applied>(service.debitSystem(UUID.randomUUID(), guild, actor, 1_000, "Guild purchase"))
        assertEquals(0, debt())
        now = occurredAt.plusSeconds(86_400)
        quote = quote.copy(occurredAt = now)
        restart()
        val refill = physicalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(refill))
        assertEquals(1_000, contribution(refill.transactionId))
        assertEquals(10, delivery.complete(refill.transactionId)!!.acceptedXp)
    }

    /** Cutover Keeps Confirmed Withdrawal Debt And Legacy Ready Does Not Mint. */
    @Test fun upgradeDebt() {
        val first = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        delivery.complete(first.transactionId)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 1_000)))
        execute("DELETE FROM guild_bank_contribution_receipts")
        execute("DELETE FROM guild_bank_contribution_accounts")
        now = occurredAt.plusSeconds(86_400)
        quote = quote.copy(occurredAt = now)
        restart()
        val returned = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(returned))
        assertEquals(0, contribution(returned.transactionId))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(returned.transactionId)!!.status)
        val fresh = personalRequest(amount = 100)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(fresh))
        execute("DELETE FROM guild_bank_contribution_receipts WHERE transaction_id = ?", fresh.transactionId.toString())
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(fresh.transactionId)!!.status)
    }

    /** Contribution Write Failure Rolls Back Bank And Debt Then Recovery Settles Once. */
    @Test fun atomicRecovery() {
        val first = personalRequest(amount = 1_000)
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
        assertEquals(1_000, gold.getBalance(guild))
        assertEquals(0, debt())
        assertEquals(0, contribution(recycled.transactionId))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(recycled.transactionId)!!.status)
        service.reconcilePending(Long.MAX_VALUE)
        assertEquals(2, personal.debitCalls)
    }

    /** Fractional Fresh Contributions Accumulate After Partial Debt Repayment. */
    @Test fun fractionalCredit() {
        val first = personalRequest(amount = 150)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        assertEquals(1, delivery.complete(first.transactionId)!!.acceptedXp)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 100)))
        now = occurredAt.plusSeconds(86_400)
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

    /** Different Repository Instances Cannot Prepare ADeposit Past An Unfinished Withdrawal. */
    @Test fun prepareRace() {
        val start = CountDownLatch(1)
        val deposit = GuildGoldMutation(UUID.randomUUID(), guild, actor,
            GuildGoldRoute.PERSONAL_ACCOUNT, GuildGoldDirection.CREDIT, 100, 0, "Deposit")
        val withdrawal = deposit.copy(transactionId = UUID.randomUUID(), direction = GuildGoldDirection.DEBIT)
        val other = GuildGoldRepositorySQL(db)
        val credit = CompletableFuture.supplyAsync { start.await(); gold.prepareDeposit(deposit, quote) }
        val debit = CompletableFuture.supplyAsync { start.await(); other.prepare(withdrawal) }
        start.countDown()
        val results = listOf(credit.get(15, TimeUnit.SECONDS), debit.get(15, TimeUnit.SECONDS))
        assertEquals(1, results.count { it is GuildGoldPreparation.New })
        assertEquals(1, results.count { it is GuildGoldPreparation.Pending })
    }

    /** Upgrade Does Not Misclassify Historical Guild Spending As Player Withdrawal. */
    @Test fun upgradeSpending() {
        val first = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(first))
        delivery.complete(first.transactionId)
        assertIs<GuildGoldResult.Applied>(service.debitSystem(UUID.randomUUID(), guild, actor, 1_000, "Real purchase"))
        execute("DELETE FROM guild_bank_contribution_receipts")
        execute("DELETE FROM guild_bank_contribution_accounts")
        now = occurredAt.plusSeconds(86_400)
        quote = quote.copy(occurredAt = now)
        restart()
        val fresh = physicalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(fresh))
        assertEquals(0, debt())
        assertEquals(1_000, contribution(fresh.transactionId))
        assertEquals(10, delivery.complete(fresh.transactionId)!!.acceptedXp)
    }

    /** Physical Withdrawal And Another Players Personal Redeposit Remain Recycled Tomorrow. */
    @Test fun physicalReturn() {
        val first = physicalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(first))
        delivery.complete(first.transactionId)
        physical.deliverResult = ExternalTransferResult.Applied
        assertIs<GuildGoldResult.Applied>(service.withdrawPhysical(physicalRequest(amount = 1_000)))
        assertEquals(1_000, debt())
        now = occurredAt.plusSeconds(86_400)
        quote = quote.copy(occurredAt = now)
        restart()
        val returned = personalRequest(amount = 1_000).copy(playerId = UUID.randomUUID())
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(returned))
        assertEquals(0, contribution(returned.transactionId))
        assertEquals(BankDepositXpStatus.NO_UNITS, delivery.complete(returned.transactionId)!!.status)
    }

    /** Returns only confirmed fresh principal. */
    protected fun contribution(id: UUID): Long {
        return checkNotNull(
            scalar(
                "SELECT fresh_after - fresh_before FROM guild_bank_contribution_receipts WHERE transaction_id = ?",
                id.toString(),
            ),
        )
    }

    /** Reads the retained withdrawal obligation. */
    protected fun debt(): Long {
        return scalar(
            "SELECT withdrawal_debt FROM guild_bank_contribution_accounts WHERE guild_id = ?",
            guild.toString(),
        ) ?: 0
    }

}

internal class BankPeriodBoundaryProbeTest : BankContributionContract() {
    @TempDir lateinit var directory: Path
    override fun newStorage() = VirtualThreadSQLiteStorage(directory.toFile())
}

internal class BankContributionMariaDbTest : BankContributionContract() {
    override fun newStorage(): MariaDBStorage {
        val port = System.getenv("GUILD_SHOP_XP_TEST_MARIA_PORT")?.toIntOrNull()
        assumeTrue(port != null, "Disposable loopback MariaDB XP test instance not configured")
        return MariaDBStorage("127.0.0.1", checkNotNull(port), "lg_shop_xp_test", "xp_test", "xp_test")
    }
}
