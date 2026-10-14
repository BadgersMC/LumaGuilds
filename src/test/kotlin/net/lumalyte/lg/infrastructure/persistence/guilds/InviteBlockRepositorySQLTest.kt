package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.infrastructure.persistence.migrations.InviteBlockSchema
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression coverage for personal invite blocking and compatibility boundaries. */
internal class InviteBlockRepositorySQLTest {
    /** Temporary database directory injected by JUnit. */
    @TempDir
    var directory: Path? = null

    private var currentStorage: VirtualThreadSQLiteStorage? = null
    private var storage: VirtualThreadSQLiteStorage
        get() = requireNotNull(currentStorage)
        set(value) {
            currentStorage = value
        }
    private var currentRepository: InviteBlockRepositorySQL? = null
    private var repository: InviteBlockRepositorySQL
        get() = requireNotNull(currentRepository)
        set(value) {
            currentRepository = value
        }
    private val owner = UUID.randomUUID()
    private val target = UUID.randomUUID()

    /** Generic constraint failures must not masquerade as successful duplicate inserts. */
    @Test
    fun constraintFailureNotSaved() {
        storage.connection.connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    "CREATE TRIGGER reject_blocks BEFORE INSERT ON ${InviteBlockSchema.TABLE} " +
                        "BEGIN SELECT RAISE(ABORT, 'rejected'); END",
                )
            }
        }
        assertFalse(repository.setBlocked(owner, target, true))
        assertFalse(repository.isBlocked(owner, target))
        assertFalse(InviteBlockRepositorySQL(storage).isBlocked(owner, target))
    }

    /** Initializes the isolated test fixture. */
    @BeforeEach
    fun setup() {
        storage = VirtualThreadSQLiteStorage(requireNotNull(directory).toFile())
        storage.connection.connection.use { InviteBlockSchema.create(it, mariaDb = false) }
        repository = InviteBlockRepositorySQL(storage)
    }

    /** Releases the test database or mocked server. */
    @AfterEach
    fun cleanup() {
        storage.connection.close()
    }

    /** Verifies failed writes never publish unsaved changes. */
    @Test
    fun failedWritesKeepCache() {
        assertTrue(repository.setBlocked(owner, target, true))
        storage.connection.connection.use { connection ->
            connection.createStatement().use { it.execute("DROP TABLE ${InviteBlockSchema.TABLE}") }
        }
        assertFalse(repository.setBlocked(owner, target, false))
        assertTrue(repository.isBlocked(owner, target))
        val another = UUID.randomUUID()
        assertFalse(repository.setBlocked(owner, another, true))
        assertFalse(repository.isBlocked(owner, another))
    }

    /** Verifies malformed stored identities leave invite protection unavailable. */
    @Test
    fun malformedBlocksFailClosed() {
        storage.connection.connection.use { connection ->
            connection.prepareStatement("INSERT INTO ${InviteBlockSchema.TABLE} VALUES (?, ?, ?)").use {
                it.setString(1, owner.toString())
                it.setString(2, "invalid-uuid")
                it.setLong(CREATED_AT_COLUMN, 1)
                it.executeUpdate()
            }
        }
        assertFalse(InviteBlockRepositorySQL(storage).isAvailable)
    }

    /** Verifies blocks are saved, idempotent, one-way, and survive a restart. */
    @Test
    fun blocksSurviveRestart() {
        assertTrue(repository.isAvailable)
        assertFalse(repository.isBlocked(owner, target))
        assertTrue(repository.setBlocked(owner, target, true))
        assertTrue(repository.setBlocked(owner, target, true))
        assertTrue(repository.isBlocked(owner, target))
        assertFalse(repository.isBlocked(target, owner))
        assertFalse(repository.setBlocked(owner, owner, true))

        storage.connection.close()
        storage = VirtualThreadSQLiteStorage(requireNotNull(directory).toFile())
        repository = InviteBlockRepositorySQL(storage)
        assertEquals(setOf(target), repository.blockedBy(owner))

        assertTrue(repository.setBlocked(owner, target, false))
        assertTrue(repository.setBlocked(owner, target, false))
        assertFalse(repository.isBlocked(owner, target))
    }

    /** Verifies without its table the repository is unavailable and saves nothing. */
    @Test
    fun missingTableUnavailable() {
        storage.connection.connection.use {
            it.createStatement().use {
                    s ->
                s.execute("DROP TABLE ${InviteBlockSchema.TABLE}")
            }
        }
        val broken = InviteBlockRepositorySQL(storage)
        assertFalse(broken.isAvailable)
        assertFalse(broken.setBlocked(owner, target, true))
        assertFalse(broken.isBlocked(owner, target))
    }

    private companion object {
        const val CREATED_AT_COLUMN = 3
    }
}
