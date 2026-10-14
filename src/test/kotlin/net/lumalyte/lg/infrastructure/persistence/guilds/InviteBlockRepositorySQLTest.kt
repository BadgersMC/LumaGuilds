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

class InviteBlockRepositorySQLTest {
    @TempDir lateinit var directory: Path

    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: InviteBlockRepositorySQL
    private val owner = UUID.randomUUID()
    private val target = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        storage.connection.connection.use { InviteBlockSchema.create(it, mariaDb = false) }
        repository = InviteBlockRepositorySQL(storage)
    }

    @AfterEach
    fun cleanup() {
        storage.connection.close()
    }

    @Test
    fun `blocks are saved, idempotent, one-way, and survive a restart`() {
        assertTrue(repository.isAvailable)
        assertFalse(repository.isBlocked(owner, target))
        assertTrue(repository.setBlocked(owner, target, true))
        assertTrue(repository.setBlocked(owner, target, true))
        assertTrue(repository.isBlocked(owner, target))
        assertFalse(repository.isBlocked(target, owner))
        assertFalse(repository.setBlocked(owner, owner, true))

        storage.connection.close()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = InviteBlockRepositorySQL(storage)
        assertEquals(setOf(target), repository.blockedBy(owner))

        assertTrue(repository.setBlocked(owner, target, false))
        assertTrue(repository.setBlocked(owner, target, false))
        assertFalse(repository.isBlocked(owner, target))
    }

    @Test
    fun `without its table the repository is unavailable and saves nothing`() {
        storage.connection.connection.use { it.createStatement().use { s -> s.execute("DROP TABLE ${InviteBlockSchema.TABLE}") } }
        val broken = InviteBlockRepositorySQL(storage)
        assertFalse(broken.isAvailable)
        assertFalse(broken.setBlocked(owner, target, true))
        assertFalse(broken.isBlocked(owner, target))
    }
}
