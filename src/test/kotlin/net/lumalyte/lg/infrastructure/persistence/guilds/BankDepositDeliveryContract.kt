package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.services.PaidGuildAdmission
import net.lumalyte.lg.application.services.PersonalGoldRequest
import net.lumalyte.lg.application.services.PhysicalCommitResult
import net.lumalyte.lg.domain.entities.BankDepositXpCompletion
import net.lumalyte.lg.domain.entities.BankDepositXpEligibility
import net.lumalyte.lg.domain.entities.BankDepositXpStatus
import net.lumalyte.lg.domain.gold.GuildGoldDirection
import net.lumalyte.lg.domain.gold.GuildGoldMutation
import net.lumalyte.lg.domain.gold.GuildGoldOperationStatus
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.gold.GuildGoldRoute
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * These contracts execute the production gold service and SQL repositories, then reconstruct them
 * without the post-credit Bukkit callback. Triggers reject actual SQL writes at transaction boundaries.
 * External receipt state survives a Guilds restart; quest/activity events are deliberately not replayed.
 */
@Suppress("MagicNumber", "TooManyFunctions")
abstract class BankDepositDeliveryContract : BankDepositDeliveryFixture() {
    @Test fun personalAppliedCreditSurvivesMissedCallbackAndRestart() {
        moneyPolicy = moneyPolicy.copy(depositFeePercent = 0.01)
        val request = personalRequest(amount = 200)
        val applied = assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        assertEquals(2L, applied.fee)
        assertEquals("READY", deliveryStatus(request.transactionId))
        assertAccounting(200, null, 0, 0)

        restart()
        assertEquals(listOf(request.transactionId), delivery.pending(10))
        assertAward(request.transactionId, 2)
        assertEquals(applied, service.depositPersonal(request))
        assertReplay(request.transactionId, BankDepositXpStatus.AWARDED, 2)
        assertEquals(202L, personal.debited)
        assertEquals(1, personal.debitCalls)
        assertEquals(0L, personal.credited)
        assertAccounting(200, 200, 2, 1)
    }

    @Test fun physicalAppliedCreditSurvivesMissedCallbackAndRestart() {
        moneyPolicy = moneyPolicy.copy(depositFeePercent = 0.01)
        val request = physicalRequest(amount = 200)
        val applied = assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        assertEquals(2L, applied.fee)
        assertEquals(GuildGoldOperationStatus.APPLIED, gold.findOperation(request.transactionId)?.status)
        assertAccounting(200, null, 0, 0)

        restart()
        assertEquals(listOf(request.transactionId), delivery.pending(10))
        assertAward(request.transactionId, 2)
        assertEquals(applied, service.depositPhysical(request))
        assertReplay(request.transactionId, BankDepositXpStatus.AWARDED, 2)
        assertEquals(202L, physical.consumed)
        assertEquals(1, physical.reserveCalls)
        assertEquals(1, physical.commitCalls)
        assertEquals(0L, physical.restored)
        assertAccounting(200, 200, 2, 1)
    }

    @Test fun quoteInsertFailureRollsBackTheCanonicalPreparedOperation() {
        val request = personalRequest()
        val mutation = GuildGoldMutation(
            request.transactionId, guild, actor, GuildGoldRoute.PERSONAL_ACCOUNT,
            GuildGoldDirection.CREDIT, request.amount, 0, request.description,
        )
        rejectInsert("guild_bank_xp_deliveries")
        assertFailsWith<SQLException> { gold.prepareDeposit(mutation, quote) }
        assertNull(gold.findOperation(request.transactionId))
        assertNull(deliveryStatus(request.transactionId))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM guild_gold_external_attempts"))
        assertAccounting(0, null, 0, 0)
        allowDeliveryWrites()

        restart()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        assertAward(request.transactionId, 1)
        assertEquals(1, personal.debitCalls)
        assertAccounting(100, 100, 1, 1)
    }

    @Test fun personalSettlementAndDeliveryActivationRollBackAndRecoverTogether() {
        val request = personalRequest()
        rejectDeliveryStatus("READY")
        assertIs<GuildGoldResult.Failed>(service.depositPersonal(request))
        assertEquals(GuildGoldOperationStatus.PREPARED, gold.findOperation(request.transactionId)?.status)
        assertEquals("PREPARED", deliveryStatus(request.transactionId))
        assertEquals(100L, personal.debited)
        assertEquals(0L, personal.credited)
        assertTrue(delivery.pending(10).isEmpty())
        assertAccounting(0, null, 0, 0)

        // Confirmed-credit recovery has the same atomic boundary, not a weaker startup path.
        restart()
        assertFailsWith<SQLException> { gold.recoverConfirmedCredit(request.transactionId, 100_000) }
        assertEquals(GuildGoldOperationStatus.PREPARED, gold.findOperation(request.transactionId)?.status)
        assertAccounting(0, null, 0, 0)
        allowDeliveryWrites()

        restart()
        assertEquals(1, service.reconcilePending(Long.MAX_VALUE))
        assertEquals(GuildGoldOperationStatus.APPLIED, gold.findOperation(request.transactionId)?.status)
        assertEquals("READY", deliveryStatus(request.transactionId))
        assertAward(request.transactionId, 1)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        assertEquals(1, personal.debitCalls)
        assertEquals(0L, personal.credited)
        assertAccounting(100, 100, 1, 1)
    }

    @Test fun physicalFinalizationRetainsHeldCreditUntilDeliveryActivationCanCommit() {
        val request = physicalRequest()
        rejectDeliveryStatus("READY")
        assertIs<GuildGoldResult.Failed>(service.depositPhysical(request))
        assertEquals(GuildGoldOperationStatus.BALANCE_APPLIED, gold.findOperation(request.transactionId)?.status)
        assertEquals("PREPARED", deliveryStatus(request.transactionId))
        assertEquals(100L, physical.consumed)
        assertTrue(delivery.pending(10).isEmpty())
        assertAccounting(100, null, 0, 0)

        restart()
        assertFailsWith<SQLException> { gold.recoverConfirmedCredit(request.transactionId, 100_000) }
        assertEquals(GuildGoldOperationStatus.BALANCE_APPLIED, gold.findOperation(request.transactionId)?.status)
        assertAccounting(100, null, 0, 0)
        allowDeliveryWrites()

        restart()
        assertEquals(1, service.reconcilePending(Long.MAX_VALUE))
        assertEquals(GuildGoldOperationStatus.APPLIED, gold.findOperation(request.transactionId)?.status)
        assertAward(request.transactionId, 1)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        assertEquals(100L, physical.consumed)
        assertEquals(1, physical.reserveCalls)
        assertEquals(0L, physical.restored)
        assertAccounting(100, 100, 1, 1)
    }

    @Test fun auditFailureLeavesDeliveryAndAllXpAccountingRetryable() {
        val request = personalRequest(amount = 200)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        rejectInsert("experience_transactions")
        assertFailsWith<SQLException> { delivery.complete(request.transactionId) }
        assertEquals("READY", deliveryStatus(request.transactionId))
        assertTrue(delivery.pendingCache(10).isEmpty())
        assertAccounting(200, null, 0, 0)
        allowDeliveryWrites()

        restart()
        assertAward(request.transactionId, 2)
        assertReplay(request.transactionId, BankDepositXpStatus.AWARDED, 2)
        assertAccounting(200, 200, 2, 1)
    }

    @Test fun terminalReceiptFailureRollsBackAlreadyWrittenXpAndHighWater() {
        val request = personalRequest(amount = 200)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        rejectDeliveryStatus("AWARDED")
        assertFailsWith<SQLException> { delivery.complete(request.transactionId) }
        assertEquals("READY", deliveryStatus(request.transactionId))
        assertTrue(delivery.pendingCache(10).isEmpty())
        assertAccounting(200, null, 0, 0)
        allowDeliveryWrites()

        restart()
        assertAward(request.transactionId, 2)
        assertAccounting(200, 200, 2, 1)
    }

    @Test fun laterSettlementCannotSeedTheFirstWindowAheadOfEarlierDelivery() {
        // Timestamp and UUID ordering deliberately disagree with canonical settlement order.
        val earlier = personalRequest(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
        val later = personalRequest(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(earlier))
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(later))
        restart()

        assertEquals(listOf(earlier.transactionId), delivery.pending(10))
        assertEquals(BankDepositXpStatus.WAITING, delivery.complete(later.transactionId)?.status)
        assertEquals("READY", deliveryStatus(later.transactionId))
        assertAccounting(200, null, 0, 0)
        assertAward(earlier.transactionId, 1)
        assertEquals(listOf(later.transactionId), delivery.pending(10))
        assertAward(later.transactionId, 1)
        assertReplay(earlier.transactionId, BankDepositXpStatus.AWARDED, 1)
        assertReplay(later.transactionId, BankDepositXpStatus.AWARDED, 1)
        assertTrue(delivery.pending(10).isEmpty())
        assertAccounting(200, 200, 2, 2)
    }

    @Test fun restartUsesOriginalTimestampPoolPolicyAndWholeAwardBoost() {
        quote = quote.copy(policy = originalPolicy.copy(awardXp = 3), boostMultiplier = 1.5, valuePerUnit = 200)
        val request = personalRequest(amount = 400)
        val applied = assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        now = occurredAt.plusSeconds(86_400)
        quote = quote.copy(
            occurredAt = now, policy = originalPolicy.copy(pool = "CHANGED_BANK_POOL", awardXp = 99, capXp = 1),
            boostMultiplier = 4.0, eligibility = BankDepositXpEligibility.AFK, valuePerUnit = 1,
        )
        restart()

        assertEquals(applied, service.depositPersonal(request))
        // floor(3 * 2 * 1.5) is 9; flooring a boosted unit first would incorrectly produce 8.
        assertAward(request.transactionId, 9)
        assertEquals(occurredAt.toEpochMilli(), scalar("SELECT timestamp FROM experience_transactions"))
        val window = checkNotNull(originalPolicy.windowContaining(occurredAt))
        assertEquals(window.startInclusive.toEpochMilli(), scalar("SELECT period_start FROM guild_experience_source_usage"))
        assertEquals(window.endExclusive.toEpochMilli(), scalar("SELECT period_end FROM guild_experience_source_usage"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM guild_experience_source_usage WHERE source_pool = 'CHANGED_BANK_POOL'"))
        assertEquals(1, personal.debitCalls)
        assertAccounting(400, 400, 9, 1)
    }

    @Test fun capturedAfkGrowthCannotLeakIntoALaterEligibleDeposit() {
        seedHighWater(0)
        quote = quote.copy(eligibility = BankDepositXpEligibility.AFK)
        val request = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        quote = quote.copy(eligibility = BankDepositXpEligibility.ELIGIBLE)
        rejectDeliveryStatus("AFK")
        assertFailsWith<SQLException> { delivery.complete(request.transactionId) }
        assertEquals("READY", deliveryStatus(request.transactionId))
        assertAccounting(1_000, 0, 0, 0)
        allowDeliveryWrites()
        restart()

        assertReplay(request.transactionId, BankDepositXpStatus.AFK)
        assertAccounting(1_000, 1_000, 0, 0)
        val eligible = personalRequest()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(eligible))
        assertAward(eligible.transactionId, 1)
        assertReplay(request.transactionId, BankDepositXpStatus.AFK)
        assertAccounting(1_100, 1_100, 1, 1)
    }

    @Test fun capturedDisabledPolicyCannotBeEnabledRetroactively() {
        quote = quote.copy(
            policy = originalPolicy.copy(enabled = false, awardXp = 0),
            eligibility = BankDepositXpEligibility.DISABLED,
        )
        val request = personalRequest()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        quote = quote.copy(policy = originalPolicy, eligibility = BankDepositXpEligibility.ELIGIBLE)
        restart()

        assertReplay(request.transactionId, BankDepositXpStatus.DISABLED)
        assertReplay(request.transactionId, BankDepositXpStatus.DISABLED)
        assertAccounting(100, 100, 0, 0)
    }

    @Test fun cappedAndNonGrowthDeliveriesRemainConsumedAfterPolicyChange() {
        quote = quote.copy(policy = originalPolicy.copy(capXp = 2))
        val clipped = personalRequest(amount = 300)
        val capped = personalRequest()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(clipped))
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(capped))
        assertAward(clipped.transactionId, 2)
        assertReplay(capped.transactionId, BankDepositXpStatus.CAPPED)
        assertAccounting(400, 400, 2, 1)

        quote = quote.copy(policy = originalPolicy)
        restart()
        assertReplay(capped.transactionId, BankDepositXpStatus.CAPPED)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest()))
        val redeposit = personalRequest()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(redeposit))
        assertReplay(redeposit.transactionId, BankDepositXpStatus.NO_UNITS)
        assertAccounting(400, 400, 2, 1)
    }

    @Test fun stalePrePrestigeGrowthCannotBeEarnedAgainBySpendingAndRedepositing() {
        seedHighWater(0)
        val request = personalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        execute("UPDATE guild_reward_accounts SET prestige_count = 1 WHERE guild_id = ?", guild.toString())
        assertIs<GuildGoldResult.Applied>(service.debitSystem(UUID.randomUUID(), guild, actor, 100, "Prestige fee"))
        restart()
        assertReplay(request.transactionId, BankDepositXpStatus.STALE_RUN)
        assertAccounting(900, 1_000, 0, 0)
        val later = personalRequest()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(later))
        assertReplay(later.transactionId, BankDepositXpStatus.NO_UNITS)
        execute("UPDATE guild_reward_accounts SET prestige_count = 0 WHERE guild_id = ?", guild.toString())
        assertReplay(request.transactionId, BankDepositXpStatus.STALE_RUN)
        assertAccounting(1_000, 1_000, 0, 0)
    }

    @Test fun chapterIdentityChangeCannotDeliverIntoTheNextChapter() {
        val request = personalRequest()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        execute("UPDATE chapter_lifecycle SET phase = 'COMPLETE'")
        execute("INSERT INTO chapter_lifecycle VALUES ('chapter-3', 'SCHEDULED', 1, ?)", Long.MAX_VALUE)
        restart()
        assertReplay(request.transactionId, BankDepositXpStatus.STALE_RUN)
        assertAccounting(100, 100, 0, 0)
    }

    @Test fun chapterFreezeDecisionRemainsTerminalAfterReopening() {
        val request = personalRequest()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        execute("UPDATE chapter_lifecycle SET phase = 'FROZEN'")
        restart()
        assertReplay(request.transactionId, BankDepositXpStatus.CHAPTER_FROZEN)
        execute("UPDATE chapter_lifecycle SET phase = 'SCHEDULED'")
        assertReplay(request.transactionId, BankDepositXpStatus.CHAPTER_FROZEN)
        assertAccounting(100, 100, 0, 0)
    }

    @Test fun deletedGuildCannotBeRecreatedByDelayedDelivery() {
        val request = personalRequest()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        execute("DELETE FROM guilds WHERE id = ?", guild.toString())
        restart()
        assertReplay(request.transactionId, BankDepositXpStatus.STALE_RUN)
        assertEquals(0L, scalar("SELECT COUNT(*) FROM guilds WHERE id = ?", guild.toString()))
        assertAccounting(100, null, 0, 0)
    }

    @Test fun retainedAwardSurvivesAuditPruningAndKeepsCacheRecoveryWithoutAnotherLevelEvent() {
        quote = quote.copy(policy = originalPolicy.copy(awardXp = 5_000, capXp = 10_000))
        val request = personalRequest()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        val first = assertAward(request.transactionId, 5_000)
        assertNotNull(first.leveledUpTo)
        assertTrue(first.cachePending)
        assertEquals(listOf(request.transactionId), delivery.pendingCache(10))
        execute("DELETE FROM experience_transactions WHERE guild_id = ?", guild.toString())

        restart()
        val replay = assertReplay(request.transactionId, BankDepositXpStatus.AWARDED, 5_000)
        assertTrue(replay.cachePending)
        assertEquals(listOf(request.transactionId), delivery.pendingCache(10))
        assertAccounting(100, 100, 5_000, 0)
        // This proves durable refresh scheduling only; an async Bukkit cache callback is not run here.
        delivery.acknowledgeCache(request.transactionId)
        restart()
        assertTrue(delivery.pendingCache(10).isEmpty())
        assertFalse(assertReplay(request.transactionId, BankDepositXpStatus.AWARDED, 5_000).cachePending)
        assertAccounting(100, 100, 5_000, 0)
    }

    @Test fun unresolvedPhysicalCreditBlocksLaterExternalDebitAcrossRepositoryInstances() {
        physical.commitResult = PhysicalCommitResult.Unknown
        physical.consumeBeforeUnknown = true
        val earlier = physicalRequest()
        assertIs<GuildGoldResult.Failed>(service.depositPhysical(earlier))
        val later = personalRequest()
        assertIs<GuildGoldResult.Failed>(newService().depositPersonal(later))
        assertNull(gold.findOperation(later.transactionId))
        assertNull(deliveryStatus(later.transactionId))
        assertEquals(0, personal.debitCalls)
        assertTrue(delivery.pending(10).isEmpty())
        assertAccounting(100, null, 0, 0)

        physical.commitResult = PhysicalCommitResult.Committed
        restart()
        assertEquals(1, service.reconcilePending(Long.MAX_VALUE))
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(later))
        assertEquals(BankDepositXpStatus.WAITING, delivery.complete(later.transactionId)?.status)
        assertAward(earlier.transactionId, 1)
        assertAward(later.transactionId, 1)
        assertEquals(100L, physical.consumed)
        assertEquals(1, physical.reserveCalls)
        assertEquals(1, personal.debitCalls)
        assertAccounting(200, 200, 2, 2)
    }

    @Test fun confirmedPhysicalReversalNeverBecomesAnXpDelivery() {
        physical.commitResult = PhysicalCommitResult.NotConsumed
        val request = physicalRequest()
        assertEquals(GuildGoldResult.Failed(request.transactionId, true), service.depositPhysical(request))
        assertEquals(GuildGoldOperationStatus.COMPENSATED, gold.findOperation(request.transactionId)?.status)
        restart()
        assertTrue(delivery.pending(10).isEmpty())
        assertReplay(request.transactionId, BankDepositXpStatus.CANCELLED)
        assertEquals(GuildGoldResult.Failed(request.transactionId, true), service.depositPhysical(request))
        assertEquals(0L, physical.consumed)
        assertEquals(100L, physical.restored)
        assertEquals(1, physical.reserveCalls)
        assertAccounting(0, null, 0, 0)
    }

    @Test fun admissionSystemInterestWithdrawalAndLegacyIdsNeverBecomeNewDepositDeliveries() {
        assertIs<GuildGoldResult.Applied>(service.creditSystem(UUID.randomUUID(), guild, actor, 500, GuildGoldRoute.SYSTEM, "Seed"))
        assertIs<GuildGoldResult.Applied>(service.creditInterest(guild, occurredAt.toEpochMilli(), 0.1))
        val legacy = personalRequest()
        val legacyApplied = assertIs<GuildGoldResult.Applied>(newService(captureQuote = false).depositPersonal(legacy))
        assertIs<GuildGoldResult.Applied>(service.collectJoinFee(personalRequest(), false, object : PaidGuildAdmission {
            override fun isEligible() = true
            override fun complete() = true
        }))
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest()))
        restart()

        assertEquals(legacyApplied, service.depositPersonal(legacy))
        assertNull(delivery.complete(legacy.transactionId))
        assertTrue(delivery.pending(10).isEmpty())
        assertTrue(delivery.pendingCache(10).isEmpty())
        assertEquals(0L, scalar("SELECT COUNT(*) FROM guild_bank_xp_deliveries"))
        assertAccounting(650, null, 0, 0)
    }

    @Test fun competingConsumersCommitOnlyOneAwardAndRetainedReceipt() {
        val request = personalRequest()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        val other = newConsumer()
        val start = CountDownLatch(1)
        val futures = listOf(delivery, other).map { consumer ->
            CompletableFuture.supplyAsync {
                start.await()
                checkNotNull(consumer.complete(request.transactionId))
            }
        }
        start.countDown()
        val results = futures.map { it.get(10, TimeUnit.SECONDS) }
        assertEquals(listOf(BankDepositXpStatus.AWARDED, BankDepositXpStatus.AWARDED), results.map { it.status })
        assertEquals(1, results.count { it.awardedNow })
        assertEquals(listOf(1, 1), results.map { it.acceptedXp })
        assertAccounting(100, 100, 1, 1)
    }

    @Test fun failedGuildHeadYieldsTheBoundedPageAfterRollbackAndRestart() {
        val head = personalRequest()
        val follower = personalRequest()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(head))
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(follower))
        val otherGuild = UUID.randomUUID()
        execute("INSERT INTO guilds VALUES (?, 1)", otherGuild.toString())
        execute("INSERT INTO guild_reward_accounts VALUES (?, 0)", otherGuild.toString())
        val other = PersonalGoldRequest(UUID.randomUUID(), otherGuild, actor, 100, "Independent guild deposit")
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(other))
        // Fix initial priority without relying on UUID collation or wall-clock resolution.
        execute("UPDATE guild_bank_xp_deliveries SET last_attempt_at = 1 WHERE transaction_id = ?", other.transactionId.toString())
        assertEquals(listOf(head.transactionId), delivery.pending(1))
        assertEquals(listOf(head.transactionId, other.transactionId), delivery.pending(10))

        rejectInsert("experience_transactions")
        assertFailsWith<SQLException> { delivery.complete(head.transactionId) }
        assertAccounting(200, null, 0, 0)
        restart()

        assertEquals("READY", deliveryStatus(head.transactionId))
        assertEquals(listOf(other.transactionId), delivery.pending(1))
        assertEquals(listOf(other.transactionId, head.transactionId), delivery.pending(10))
        assertTrue(delivery.pendingCache(10).isEmpty())
        allowDeliveryWrites()
        val awarded = assertNotNull(delivery.complete(other.transactionId))
        assertEquals(otherGuild, awarded.guildId)
        assertEquals(BankDepositXpStatus.AWARDED, awarded.status)
        assertEquals(1, awarded.acceptedXp)
        assertEquals(1L, scalar("SELECT total_experience FROM guild_progression WHERE guild_id = ?", otherGuild.toString()))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM experience_transactions WHERE guild_id = ?", otherGuild.toString()))
        // The failed guild's follower cannot crowd either bounded queue.
        assertEquals(listOf(head.transactionId), delivery.pending(10))
        assertEquals(listOf(other.transactionId), delivery.pendingCache(10))
        assertAccounting(200, null, 0, 0)
    }

    @Test fun alreadyAbsentGuildCannotPrepareOrChargeANewPlayerDeposit() {
        execute("DELETE FROM guilds WHERE id = ?", guild.toString())
        assertFailsWith<IllegalStateException> { service.depositPersonal(personalRequest()) }
        assertFailsWith<IllegalStateException> { service.depositPhysical(physicalRequest()) }

        assertEquals(0, personal.debitCalls)
        assertEquals(0L, personal.debited)
        assertEquals(0, physical.reserveCalls)
        assertEquals(0L, physical.consumed)
        assertEquals(0L, scalar("SELECT COUNT(*) FROM guild_gold_operations"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM guild_gold_external_attempts"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM guild_bank_xp_deliveries"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM guild_bank_xp_delivery_sequences"))
        assertAccounting(0, null, 0, 0)
    }

    private fun assertAward(id: UUID, xp: Int): BankDepositXpCompletion {
        val result = assertNotNull(delivery.complete(id))
        assertEquals(id, result.transactionId)
        assertEquals(guild, result.guildId)
        assertEquals(BankDepositXpStatus.AWARDED, result.status)
        assertEquals(xp, result.acceptedXp)
        assertTrue(result.awardedNow)
        return result
    }

    private fun assertReplay(id: UUID, status: BankDepositXpStatus, xp: Int = 0): BankDepositXpCompletion {
        val result = assertNotNull(delivery.complete(id))
        assertEquals(id, result.transactionId)
        assertEquals(guild, result.guildId)
        assertEquals(status, result.status)
        assertEquals(xp, result.acceptedXp)
        assertFalse(result.awardedNow)
        assertNull(result.leveledUpTo)
        return result
    }
}
