package net.lumalyte.lg.infrastructure.persistence.guilds

import io.mockk.mockk
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.config.GuildConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.services.GuildCreationVaultJournal
import net.lumalyte.lg.infrastructure.services.VaultGuildCreationPaymentAdapter
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** Real SQL ledger (SQLite or opt-in disposable MariaDB), no simulated in-memory journal. */
class VaultGuildCreationPaymentSQLTest : RewardSqlTestFixture() {
    private fun service(port: GuildCreationPaymentPort) = GuildCostService(
        { MainConfig(chapterTwoGoldCostsEnabled = true, guild = GuildConfig(createGuildCost = 10)) },
        port,
        mockk(relaxed = true),
    )

    private fun guild() = Guild(UUID.randomUUID(), "Founded", createdAt = Instant.now())

    private fun port(personal: PersonalEconomyPort, journal: GuildCreationVaultJournal) =
        VaultGuildCreationPaymentAdapter(personal, journal)

    @Test fun appliedDebitCommitsAndReleasesGuardWithoutDiscardingAudit() {
        val storage = openStorage()
        val journal = GuildCreationVaultJournal(storage)
        val economy = FakePersonal(100)
        val player = UUID.randomUUID()
        val tx = UUID.randomUUID()

        val result = assertIs<GuildCreationCostResult.Applied>(
            service(port(economy, journal)).createGuild(tx, player) { guild() }
        )
        assertEquals(10L, result.cost)
        assertEquals(90L, economy.money)
        assertEquals(1, economy.debits.get())
        assertNull(journal.pending(player))
        assertEquals("COMMITTED", journal.get(tx)?.phase)
        assertEquals(10L, journal.get(tx)?.payment?.value)
    }

    @Test fun missingProviderAndInsufficientBalanceNeverCreatePaymentIntent() {
        val storage = openStorage()
        val journal = GuildCreationVaultJournal(storage)
        val economy = FakePersonal(9)
        val player = UUID.randomUUID()
        val service = service(port(economy, journal))
        assertEquals(GuildCreationCostResult.InsufficientGold,
            service.createGuild(UUID.randomUUID(), player) { fail("must not found") })
        economy.available = false
        assertEquals(GuildCreationCostResult.PaymentUnavailable,
            service.createGuild(UUID.randomUUID(), player) { fail("must not found") })
        assertNull(journal.pending(player))
        assertEquals(0, economy.debits.get())
    }

    @Test fun provenCreationFailureCreditsOnceThenAllowsRetry() {
        val storage = openStorage()
        val journal = GuildCreationVaultJournal(storage)
        val economy = FakePersonal(100)
        val player = UUID.randomUUID()
        val tx = UUID.randomUUID()

        assertEquals(GuildCreationCostResult.CreationFailed(true),
            service(port(economy, journal)).createGuild(tx, player) { null })
        assertEquals(100L, economy.money)
        assertEquals(1, economy.debits.get())
        assertEquals(1, economy.credits.get())
        assertEquals("RESTORED", journal.get(tx)?.phase)
        assertNull(journal.pending(player))
        assertIs<GuildCreationCostResult.Applied>(
            service(port(economy, journal)).createGuild(UUID.randomUUID(), player) { guild() }
        )
        assertEquals(2, economy.debits.get())
    }

    @Test fun uncertainDebitHoldsSameReceiptAcrossNewAdapterAndRestart() {
        val storage = openStorage()
        val firstJournal = GuildCreationVaultJournal(storage)
        val economy = FakePersonal(100)
        economy.debitEffect = { amount ->
            economy.money -= amount
            ExternalTransferResult.Failed("provider applied then lost acknowledgement")
        }
        val player = UUID.randomUUID()
        val tx = UUID.randomUUID()

        assertEquals(GuildCreationCostResult.Uncertain(null, tx),
            service(port(economy, firstJournal)).createGuild(tx, player) { fail("must not found") })
        assertEquals("DEBITING", firstJournal.get(tx)?.phase)
        assertEquals(90L, economy.money)
        assertEquals(0, economy.credits.get())

        // Simulates reopening the plugin's journal and a new command transaction.
        val resumedJournal = GuildCreationVaultJournal(storage)
        assertEquals(GuildCreationCostResult.PendingReview(tx),
            service(port(economy, resumedJournal)).createGuild(UUID.randomUUID(), player) {
                fail("unresolved payment must prevent a second founding")
            })
        assertEquals(1, economy.debits.get())
        assertEquals(tx, resumedJournal.pending(player))
    }

    @Test fun creationPersistenceExceptionHoldsDebitedReceiptWithoutRefund() {
        val storage = openStorage()
        val journal = GuildCreationVaultJournal(storage)
        val economy = FakePersonal(100)
        val player = UUID.randomUUID()
        val tx = UUID.randomUUID()

        assertEquals(GuildCreationCostResult.Uncertain(null, tx),
            service(port(economy, journal)).createGuild(tx, player) {
                error("commit acknowledgement unknown")
            })
        assertEquals("DEBITED", journal.get(tx)?.phase)
        assertEquals(tx, journal.pending(player))
        assertEquals(90L, economy.money)
        assertEquals(0, economy.credits.get())
    }

    @Test fun uncertainRefundCannotBeRepeatedOrSilentlyReleased() {
        val storage = openStorage()
        val journal = GuildCreationVaultJournal(storage)
        val economy = FakePersonal(100)
        economy.creditEffect = { amount ->
            economy.money += amount
            ExternalTransferResult.Failed("refund applied but provider rejected acknowledgement")
        }
        val player = UUID.randomUUID()
        val tx = UUID.randomUUID()
        assertEquals(GuildCreationCostResult.Uncertain(null, tx),
            service(port(economy, journal)).createGuild(tx, player) { null })
        assertEquals("CREDITING", journal.get(tx)?.phase)
        assertEquals(100L, economy.money)
        assertEquals(tx, journal.pending(player))
        assertEquals(GuildCreationCostResult.PendingReview(tx),
            service(port(economy, GuildCreationVaultJournal(storage))).createGuild(UUID.randomUUID(), player) {
                fail("must block")
            })
        assertEquals(1, economy.credits.get())
        assertEquals(1, economy.debits.get())
    }

    @Test fun samePlayerConcurrentCommandsNeverMakeTwoExternalDebits() {
        val storage = openStorage()
        val economy = FakePersonal(100)
        val player = UUID.randomUUID()
        val journal = GuildCreationVaultJournal(storage)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val actions = (1..2).map {
                pool.submit<GuildCreationCostResult> {
                    start.await()
                    service(port(economy, GuildCreationVaultJournal(storage)))
                        .createGuild(UUID.randomUUID(), player) { guild() }
                }
            }
            start.countDown()
            actions.forEach { it.get(20, TimeUnit.SECONDS) }
            assertEquals(1, economy.debits.get(), "unique SQL guard must serialize paid founding")
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun unsafeSqlClaimFailureNeverInvokesVaultDebit() {
        val storage = openStorage()
        val journal = GuildCreationVaultJournal(storage)
        val economy = FakePersonal(100)
        // The creation journal's receipt insertion fails after the guard insert.
        // The transaction rolls both back; the external debit must never execute.
        val trigger = if (storage.dialect == net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect.MARIADB)
            "CREATE TRIGGER reject_vault_payment BEFORE INSERT ON guild_creation_vault_payments " +
                "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected journal failure'"
        else
            "CREATE TRIGGER reject_vault_payment BEFORE INSERT ON guild_creation_vault_payments " +
                "BEGIN SELECT RAISE(ABORT, 'injected journal failure'); END"
        storage.connection.executeUpdate(trigger)
        val player = UUID.randomUUID()
        val result = service(port(economy, journal)).createGuild(UUID.randomUUID(), player) {
            fail("must not create")
        }
        assertIs<GuildCreationCostResult.Uncertain>(result)
        assertEquals(0, economy.debits.get())
        assertNull(journal.pending(player))
    }

    private class FakePersonal(initial: Long) : PersonalEconomyPort {
        @Volatile var money = initial
        @Volatile var available = true
        val debits = AtomicInteger()
        val credits = AtomicInteger()
        var debitEffect: (Long) -> ExternalTransferResult = { amount ->
            synchronized(this) { money -= amount }
            ExternalTransferResult.Applied
        }
        var creditEffect: (Long) -> ExternalTransferResult = { amount ->
            synchronized(this) { money += amount }
            ExternalTransferResult.Applied
        }
        override fun isAvailable() = available
        override fun balance(playerId: UUID): Long? = if (available) money else null
        override fun debit(playerId: UUID, amount: Long): ExternalTransferResult {
            debits.incrementAndGet()
            return debitEffect(amount)
        }
        override fun credit(playerId: UUID, amount: Long): ExternalTransferResult {
            credits.incrementAndGet()
            return creditEffect(amount)
        }
    }
}
