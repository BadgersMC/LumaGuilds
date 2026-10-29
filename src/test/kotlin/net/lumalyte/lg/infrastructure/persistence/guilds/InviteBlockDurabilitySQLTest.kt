package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import net.lumalyte.lg.api.GuildInviteBlocksV2Impl
import net.lumalyte.lg.application.persistence.InviteBlockWriteOutcome
import net.lumalyte.lg.infrastructure.persistence.migrations.InviteBlockSchema
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.InviteBlockServiceImpl
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The same real database contracts run on SQLite and the opt-in disposable MariaDB reward profile. */
internal class InviteBlockDurabilitySQLTest : RewardSqlTestFixture() {
    private val owner = UUID.randomUUID()
    private val target = UUID.randomUUID()

    @Test
    fun legacyCleanupDeletesStoredBlockAbsentFromCache() {
        val storage = database()
        val repository = InviteBlockRepositorySQL(storage)
        insertDirectly(storage)
        assertFalse(repository.isBlocked(owner, target))

        assertTrue(repository.setBlocked(owner, target, false))

        assertFalse(stored(storage))
        assertFalse(InviteBlockRepositorySQL(storage).isBlocked(owner, target))
    }

    @Test
    fun legacyAlreadyBlockedRepairsMissingStoredRow() {
        val storage = database()
        val repository = InviteBlockRepositorySQL(storage)
        assertTrue(repository.setBlocked(owner, target, true))
        storage.connection.executeUpdate("DELETE FROM ${InviteBlockSchema.TABLE}")
        assertTrue(repository.isBlocked(owner, target))

        assertTrue(repository.setBlocked(owner, target, true))

        assertTrue(stored(storage))
    }

    @Test
    fun authoritativeReadsReconcileBothStaleCacheDirections() {
        val storage = database()
        val repository = InviteBlockRepositorySQL(storage)
        insertDirectly(storage)
        assertTrue(repository.isBlockedDurable(owner, target))
        assertTrue(repository.isBlocked(owner, target))
        storage.connection.executeUpdate("DELETE FROM ${InviteBlockSchema.TABLE}")
        assertFalse(repository.isBlockedDurable(owner, target))
        assertFalse(repository.isBlocked(owner, target))
    }

    @Test
    fun postCommitLogicalCloseErrorPreservesCommittedLegacySuccess() {
        val storage = database()
        val faults = InviteBlockJdbcFaults(storage)
        val repository = InviteBlockRepositorySQL(faults.storage)
        faults.next = InviteBlockJdbcFaults.Fault.CLOSE_AFTER_COMMIT

        assertTrue(repository.setBlocked(owner, target, true))

        assertTrue(repository.isBlocked(owner, target))
        assertTrue(stored(storage))
        assertEquals(1, faults.logicalCloseCalls)
    }

    @Test
    fun statementCloseFailureRollsBackBeforeCommitAndKeepsCache() {
        val storage = database()
        val faults = InviteBlockJdbcFaults(storage)
        val repository = InviteBlockRepositorySQL(faults.storage)
        faults.next = InviteBlockJdbcFaults.Fault.STATEMENT_CLOSE

        val result = repository.setBlockedDurable(owner, target, true)

        assertEquals(InviteBlockWriteOutcome.NOT_APPLIED, result.outcome)
        assertFalse(result.succeeded)
        assertFalse(repository.isBlocked(owner, target))
        assertFalse(stored(storage))
        assertEquals(1, faults.autoCommitResets)
    }

    @Test
    fun postCommitResetFailurePreservesCommitAndClosesPhysicalDelegate() {
        val storage = database()
        val faults = InviteBlockJdbcFaults(storage)
        val repository = InviteBlockRepositorySQL(faults.storage)
        faults.next = InviteBlockJdbcFaults.Fault.RESET_AFTER_COMMIT

        val result = repository.setBlockedDurable(owner, target, true)

        assertEquals(InviteBlockWriteOutcome.COMMITTED, result.outcome)
        assertTrue(result.succeeded)
        assertTrue(repository.isBlocked(owner, target))
        assertTrue(stored(storage))
        assertTrue(assertNotNull(faults.physical).isClosed)
        assertEquals(0, faults.logicalCloseCalls)
        assertEquals(1, faults.autoCommitResets)
    }

    @Test
    fun thrownCommitRemainsUnknownEvenWhenFollowingRollbackSucceeds() {
        val storage = database()
        val faults = InviteBlockJdbcFaults(storage)
        val repository = InviteBlockRepositorySQL(faults.storage)
        faults.next = InviteBlockJdbcFaults.Fault.COMMIT_BEFORE_ACK

        val result = repository.setBlockedDurable(owner, target, true)

        assertEquals(InviteBlockWriteOutcome.UNKNOWN, result.outcome)
        assertFalse(stored(storage))
        assertQuarantined(faults)
    }

    @Test
    fun lostCommitAcknowledgementIsUnknownAndCompensationStillTouchesSql() {
        val storage = database()
        val faults = InviteBlockJdbcFaults(storage)
        val repository = InviteBlockRepositorySQL(faults.storage)
        faults.next = InviteBlockJdbcFaults.Fault.COMMIT_AFTER_ACK

        assertFalse(repository.setBlocked(owner, target, true))
        assertTrue(stored(storage))
        assertFalse(repository.isBlocked(owner, target))
        assertQuarantined(faults)

        assertTrue(repository.setBlocked(owner, target, false))
        assertFalse(stored(storage))
    }

    @Test
    fun failedRollbackDiscardsPhysicalConnectionWithoutLaterPoolCommit() {
        val storage = database()
        val faults = InviteBlockJdbcFaults(storage)
        val repository = InviteBlockRepositorySQL(faults.storage)
        faults.next = InviteBlockJdbcFaults.Fault.ROLLBACK_FAILURE

        val result = repository.setBlockedDurable(owner, target, true)

        assertEquals(InviteBlockWriteOutcome.UNKNOWN, result.outcome)
        assertQuarantined(faults)
        assertFalse(stored(storage))
        val unrelated = UUID.randomUUID()
        assertTrue(repository.setBlocked(owner, unrelated, true))
        assertFalse(stored(storage), "An unrelated pool borrower must not commit the failed invocation")
        assertTrue(InviteBlockRepositorySQL(storage).isBlocked(owner, unrelated))
    }

    @Test
    fun unconfirmedPhysicalCloseNeverReturnsDirtyWrapperToPool() {
        val storage = database()
        val faults = InviteBlockJdbcFaults(storage)
        val repository = InviteBlockRepositorySQL(faults.storage)
        faults.next = InviteBlockJdbcFaults.Fault.PHYSICAL_CLOSE_FAILURE
        try {
            val result = repository.setBlockedDurable(owner, target, true)
            assertEquals(InviteBlockWriteOutcome.UNKNOWN, result.outcome)
            assertEquals(0, faults.logicalCloseCalls)
            assertEquals(0, faults.autoCommitResets)
            assertFalse(assertNotNull(faults.physical).isClosed)
        } finally {
            // Test-owned recovery removes the injected disposal failure; production correctly retains UNKNOWN.
            faults.physical?.let { physical ->
                try { physical.rollback() } finally { physical.close() }
            }
        }
        assertFalse(stored(storage))
    }

    @Test
    fun confirmedCommitWithFailedCachePublicationIsExternallyUnknown() {
        val storage = database()
        val cache = spyk(InviteBlockCache())
        val repository = InviteBlockRepositorySQL(storage, cache)
        val service = InviteBlockServiceImpl(repository, mockk(relaxed = true), mockk(relaxed = true))
        val api = GuildInviteBlocksV2Impl(service)
        every { cache.publish(owner, target, true) } throws IllegalStateException("Injected cache failure")

        assertFailsWith<CompletionException> { api.setBlockedDurable(owner, target, true).toCompletableFuture().join() }

        assertTrue(stored(storage))
        assertFalse(repository.isBlocked(owner, target))
    }

    @Test
    fun strictVoidCompletionContainsNullAfterSqlAndCacheAreReady() {
        val storage = database()
        val repository = InviteBlockRepositorySQL(storage)
        val service = InviteBlockServiceImpl(repository, mockk(relaxed = true), mockk(relaxed = true))
        val api = GuildInviteBlocksV2Impl(service)

        assertNull(api.setBlockedDurable(owner, target, true).toCompletableFuture().join())

        assertTrue(stored(storage))
        assertTrue(repository.isBlocked(owner, target))
        assertNull(api.setBlockedDurable(owner, target, false).toCompletableFuture().join())
        assertFalse(stored(storage))
        assertFalse(repository.isBlocked(owner, target))
    }

    private fun database(): Storage<Database> = openStorage().also { storage ->
        storage.connection.connection.use { InviteBlockSchema.create(it, storage.dialect == SqlDialect.MARIADB) }
    }

    private fun insertDirectly(storage: Storage<Database>) {
        storage.connection.executeUpdate(
            "INSERT INTO ${InviteBlockSchema.TABLE} (owner_id, target_id, created_at) VALUES (?, ?, ?)",
            owner.toString(), target.toString(), 1L,
        )
    }

    private fun stored(storage: Storage<Database>): Boolean =
        storage.connection.getFirstRow(
            "SELECT 1 FROM ${InviteBlockSchema.TABLE} WHERE owner_id = ? AND target_id = ?",
            owner.toString(), target.toString(),
        ) != null

    private fun assertQuarantined(faults: InviteBlockJdbcFaults) {
        assertTrue(assertNotNull(faults.physical).isClosed)
        assertEquals(0, faults.logicalCloseCalls)
        assertEquals(0, faults.autoCommitResets)
    }
}
