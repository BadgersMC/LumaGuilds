package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.results.OwnerRecoveryPreviewResult
import net.lumalyte.lg.application.results.OwnerRecoveryRejection
import net.lumalyte.lg.application.results.OwnerRecoveryResult
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The shared recovery fixture keeps each distinct rejection and rollback contract independently runnable. */
// Scenario SQL stays beside its assertions so fault injection and validation cases can be reviewed independently.
@Suppress("TooManyFunctions", "StringLiteralDuplication")
internal class MemberOwnerRecoveryTest {
    // JUnit injects a fresh directory before each test; it is not a runtime nullable dependency.
    @Suppress("LateinitUsage")
    @TempDir
    lateinit var directory: Path

    @Test
    @DisplayName("Preview reads current database state without writing")
    fun previewIsReadOnly() =
        fixture { f ->
            f.storage.connection.executeUpdate(
                "UPDATE members SET rank_id = ? WHERE player_id = ?",
                f.thirdRank.toString(),
                f.target.playerId.toString(),
            )
            for (table in listOf("guilds", "ranks", "members")) {
                f.storage.connection.executeUpdate(
                    "CREATE TRIGGER reject_${table}_write BEFORE UPDATE ON $table " +
                        "BEGIN SELECT RAISE(ABORT, 'preview must not write'); END",
                )
            }
            val preview = f.preview()
            assertEquals(f.thirdRank, preview.member.rankId)
            assertEquals(f.lowerRank, f.repo.getRankId(f.target.playerId, f.guild))
            assertEquals(0, f.ownerCount())
            f.assertProgressUnchanged()
        }

    @Test
    @DisplayName("Recovery preserves membership dates and guild progress")
    fun recoveryPreservesData() =
        fixture { f ->
            val joinedBefore = f.storedJoinedAt(f.target)
            val preview = f.preview()
            assertEquals("Founder", preview.ownerRankName)
            assertEquals(0, f.ownerCount())
            assertIs<OwnerRecoveryResult.Recovered>(f.repo.recoverOwnership(preview))
            assertEquals(f.ownerRank, f.storedRank(f.target))
            assertEquals(f.ownerRank, f.repo.getRankId(f.target.playerId, f.guild))
            assertEquals(f.thirdRank, f.storedRank(f.other))
            assertEquals(joinedBefore, f.storedJoinedAt(f.target))
            assertEquals(2, f.repo.getMemberCount(f.guild))
            assertEquals(1, f.ownerCount())
            f.assertProgressUnchanged()
            assertEquals(f.ownerRank, MemberRepositorySQLite(f.storage).getRankId(f.target.playerId, f.guild))
            assertRejected(f.repo.recoverOwnership(preview), OwnerRecoveryRejection.ALREADY_OWNED)
        }

    @Test
    @DisplayName("Recovery does not touch guild or rank rows")
    fun onlyTargetMemberIsUpdated() =
        fixture { f ->
            val preview = f.preview()
            for (table in listOf("guilds", "ranks")) {
                f.storage.connection.executeUpdate(
                    "CREATE TRIGGER reject_${table}_write BEFORE UPDATE ON $table " +
                        "BEGIN SELECT RAISE(ABORT, 'unrelated row modified'); END",
                )
            }
            f.storage.connection.executeUpdate(
                "CREATE TRIGGER reject_noop BEFORE UPDATE ON members WHEN NEW.rank_id = OLD.rank_id " +
                    "BEGIN SELECT RAISE(ABORT, 'reservation must match no rows'); END",
            )
            assertIs<OwnerRecoveryResult.Recovered>(f.repo.recoverOwnership(preview))
            assertEquals(1, f.ownerCount())
            f.assertProgressUnchanged()
        }

    @Test
    @DisplayName("Missing guilds and nonmember targets are rejected")
    fun missingRecordsRejected() =
        fixture { f ->
            assertPreviewRejected(
                f.repo.previewOwnerRecovery(UUID.randomUUID(), f.target.playerId),
                OwnerRecoveryRejection.GUILD_NOT_FOUND,
            )
            val foreign = f.other.copy(playerId = UUID.randomUUID(), guildId = UUID.randomUUID())
            f.insertMember(foreign)
            for (id in listOf(UUID.randomUUID(), foreign.playerId)) {
                assertPreviewRejected(f.repo.previewOwnerRecovery(f.guild, id), OwnerRecoveryRejection.MEMBER_NOT_FOUND)
            }
            assertEquals(0, f.ownerCount())
        }

    @Test
    @DisplayName("An existing Owner is detected despite stale cached membership")
    fun existingOwnerDetected() =
        fixture { f ->
            val preview = f.preview()
            f.insertMember(f.other.copy(playerId = UUID.randomUUID(), rankId = f.ownerRank))
            assertPreviewRejected(
                f.repo.previewOwnerRecovery(f.guild, f.target.playerId),
                OwnerRecoveryRejection.ALREADY_OWNED,
            )
            assertRejected(f.repo.recoverOwnership(preview), OwnerRecoveryRejection.ALREADY_OWNED)
            assertEquals(f.lowerRank, f.storedRank(f.target))
        }

    @Test
    @DisplayName("A guild must have exactly one Owner rank")
    fun ownerRankMustBeUnique() {
        fixture { f ->
            f.storage.connection.executeUpdate("DELETE FROM ranks WHERE id = ?", f.ownerRank.toString())
            assertPreviewRejected(
                f.repo.previewOwnerRecovery(f.guild, f.target.playerId),
                OwnerRecoveryRejection.INVALID_OWNER_RANKS,
            )
        }
        fixture { f ->
            f.insertRank(UUID.randomUUID(), 0)
            assertPreviewRejected(
                f.repo.previewOwnerRecovery(f.guild, f.target.playerId),
                OwnerRecoveryRejection.INVALID_OWNER_RANKS,
            )
        }
    }

    @Test
    @DisplayName("Malformed ranks and foreign member ranks fail closed")
    fun invalidRanksRejected() {
        for (badPriority in listOf<Any?>(null, -1, "invalid")) fixture { f ->
            f.storage.connection.executeUpdate(
                "UPDATE ranks SET priority = ? WHERE id = ?",
                badPriority,
                f.ownerRank.toString(),
            )
            assertPreviewRejected(
                f.repo.previewOwnerRecovery(f.guild, f.target.playerId),
                OwnerRecoveryRejection.INVALID_OWNER_RANKS,
            )
        }
        fixture { f ->
            val foreignRank = UUID.randomUUID()
            f.insertRank(foreignRank, 0, guildId = UUID.randomUUID())
            f.storage.connection.executeUpdate(
                "UPDATE members SET rank_id = ? WHERE player_id = ?",
                foreignRank.toString(),
                f.other.playerId.toString(),
            )
            assertPreviewRejected(
                f.repo.previewOwnerRecovery(f.guild, f.target.playerId),
                OwnerRecoveryRejection.INVALID_MEMBER_RANKS,
            )
        }
        fixture { f ->
            f.storage.connection.executeUpdate(
                "UPDATE members SET rank_id = ? WHERE player_id = ?",
                UUID.randomUUID().toString(),
                f.target.playerId.toString(),
            )
            assertPreviewRejected(
                f.repo.previewOwnerRecovery(f.guild, f.target.playerId),
                OwnerRecoveryRejection.INVALID_MEMBER_RANKS,
            )
        }
    }

    @Test
    @DisplayName("Changed ranks or membership timestamps invalidate the preview")
    fun stalePreviewRejected() {
        fixture { f ->
            val preview = f.preview()
            f.storage.connection.executeUpdate(
                "UPDATE members SET rank_id = ? WHERE player_id = ?",
                f.thirdRank.toString(),
                f.target.playerId.toString(),
            )
            assertRejected(f.repo.recoverOwnership(preview), OwnerRecoveryRejection.STALE_PREVIEW)
            assertEquals(f.thirdRank, f.storedRank(f.target))
        }
        fixture { f ->
            val preview = f.preview()
            f.storage.connection.executeUpdate(
                "UPDATE members SET joined_at = ? WHERE player_id = ?",
                f.joinedAt.plusSeconds(1).toString(),
                f.target.playerId.toString(),
            )
            assertRejected(f.repo.recoverOwnership(preview), OwnerRecoveryRejection.STALE_PREVIEW)
            assertEquals(f.lowerRank, f.storedRank(f.target))
        }
        fixture { f ->
            val preview = f.preview()
            f.storage.connection.executeUpdate(
                "UPDATE ranks SET id = ? WHERE id = ?",
                UUID.randomUUID().toString(),
                f.ownerRank.toString(),
            )
            assertRejected(f.repo.recoverOwnership(preview), OwnerRecoveryRejection.STALE_PREVIEW)
        }
    }

    @Test
    @DisplayName("Confirmation cannot recreate a vanished membership")
    fun missingMemberNotRecreated() =
        fixture { f ->
            val preview = f.preview()
            assertTrue(f.repo.remove(f.target.playerId, f.guild))
            assertRejected(f.repo.recoverOwnership(preview), OwnerRecoveryRejection.MEMBER_NOT_FOUND)
            assertNull(f.storedRank(f.target))
            assertNull(f.repo.getRankId(f.target.playerId, f.guild))
        }

    @Test
    @DisplayName("A failed write leaves database and cache unchanged")
    fun writeFailurePreservesState() =
        fixture { f ->
            val preview = f.preview()
            f.storage.connection.executeUpdate(
                "CREATE TRIGGER reject_recovery BEFORE UPDATE ON members " +
                    "BEGIN SELECT RAISE(ABORT, 'test fault'); END",
            )
            assertRejected(f.repo.recoverOwnership(preview), OwnerRecoveryRejection.STORAGE_FAILURE)
            assertEquals(f.lowerRank, f.storedRank(f.target))
            assertEquals(f.lowerRank, f.repo.getRankId(f.target.playerId, f.guild))
            assertEquals(0, f.ownerCount())
            f.storage.connection.executeUpdate("DROP TRIGGER reject_recovery")
            assertIs<OwnerRecoveryResult.Recovered>(f.repo.recoverOwnership(preview))
        }

    @Test
    @DisplayName("A failed commit rolls back without publishing cache state")
    fun commitFailurePreservesState() =
        fixture { f ->
            val preview = f.preview()
            val failedCommitRepo = MemberRepositorySQLite(commitFailingStorage(f.storage))
            assertRejected(failedCommitRepo.recoverOwnership(preview), OwnerRecoveryRejection.STORAGE_FAILURE)
            assertEquals(f.lowerRank, f.storedRank(f.target))
            assertEquals(f.lowerRank, failedCommitRepo.getRankId(f.target.playerId, f.guild))
            assertEquals(0, f.ownerCount())
            f.assertProgressUnchanged()
        }

    @Test
    @DisplayName("Concurrent recovery through separate adapters produces one Owner")
    fun concurrentRecoveryIsExclusive() =
        fixture { f ->
            val secondRepo = MemberRepositorySQLite(f.storage)
            val previews = listOf(f.preview(), f.preview(f.other.playerId))
            val start = CountDownLatch(1)
            val attempts =
                listOf(f.repo, secondRepo).mapIndexed { index, repo ->
                    CompletableFuture.supplyAsync {
                        check(start.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        repo.recoverOwnership(previews[index])
                    }
                }
            start.countDown()
            val results = attempts.map { it.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is OwnerRecoveryResult.Recovered })
            assertRejected(results.single { it is OwnerRecoveryResult.Rejected }, OwnerRecoveryRejection.ALREADY_OWNED)
            assertEquals(1, f.ownerCount())
            f.assertProgressUnchanged()
        }

    @Test
    @DisplayName("A stale repository cannot remove or demote the recovered Owner")
    fun staleWritesPreserveOwner() =
        fixture { f ->
            val staleRepo = MemberRepositorySQLite(f.storage)
            assertIs<OwnerRecoveryResult.Recovered>(f.repo.recoverOwnership(f.preview()))
            assertEquals(f.lowerRank, staleRepo.getRankId(f.target.playerId, f.guild))
            assertFalse(staleRepo.remove(f.target.playerId, f.guild))
            assertFalse(staleRepo.update(f.target.copy(rankId = f.thirdRank)))
            assertFalse(staleRepo.update(f.other.copy(rankId = f.ownerRank)))
            assertEquals(f.ownerRank, f.storedRank(f.target))
            assertEquals(f.thirdRank, f.storedRank(f.other))
            assertEquals(1, f.ownerCount())
        }

    @Test
    @DisplayName("Ordinary rank changes, transfer, and disband remain available")
    fun normalChangesRemainAvailable() =
        fixture { f ->
            assertTrue(f.repo.update(f.other.copy(rankId = f.lowerRank)))
            assertTrue(f.repo.update(f.other))
            val preview = f.preview()
            assertIs<OwnerRecoveryResult.Recovered>(f.repo.recoverOwnership(preview))
            val owner = preview.member.copy(rankId = f.ownerRank)
            assertTrue(f.repo.update(owner))
            assertTrue(f.repo.transferOwnership(owner, f.other, f.lowerRank))
            assertTrue(f.repo.remove(owner.playerId, f.guild))
            assertEquals(f.ownerRank, f.storedRank(f.other))
            assertTrue(f.repo.removeByGuild(f.guild))
            assertEquals(0, f.repo.getMemberCount(f.guild))
        }

    private fun fixture(block: (OwnerRecoveryFixture) -> Unit) {
        val storage = VirtualThreadSQLiteStorage(Files.createTempDirectory(directory, "recovery-").toFile())
        try {
            block(OwnerRecoveryFixture(storage))
        } finally {
            storage.connection.close(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private fun assertPreviewRejected(result: OwnerRecoveryPreviewResult, reason: OwnerRecoveryRejection) {
        assertEquals(reason, assertIs<OwnerRecoveryPreviewResult.Rejected>(result).reason)
    }

    private fun assertRejected(result: OwnerRecoveryResult, reason: OwnerRecoveryRejection) {
        assertEquals(reason, assertIs<OwnerRecoveryResult.Rejected>(result).reason)
    }

    private fun commitFailingStorage(original: Storage<Database>): Storage<Database> {
        val database =
            proxy<Database>(original.connection) { method, result ->
                if (method == "getConnection") {
                    proxy<Connection>(result as Connection) { _, value -> value }
                } else {
                    result
                }
            }
        return object : Storage<Database> {
            override val connection = database
            override val dialect = original.dialect
        }
    }

    // JDBC failure injection forwards Java reflection varargs; these small arrays are test-only.
    @Suppress("SpreadOperator")
    private inline fun <reified T> proxy(original: T, crossinline transform: (String, Any?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments ->
            if (T::class.java == Connection::class.java && method.name == "commit") {
                throw SQLException("test commit failure")
            }
            val forwardedArguments = arguments ?: emptyArray()
            val result =
                try {
                    method.invoke(original, *forwardedArguments)
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                }
            transform(method.name, result)
        } as T

    private companion object {
        const val START_TIMEOUT_SECONDS = 5L
        const val RESULT_TIMEOUT_SECONDS = 10L
        const val CLOSE_TIMEOUT_SECONDS = 5L
    }
}
