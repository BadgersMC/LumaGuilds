package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class RankClaimPermissionProfileRepositorySQLTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: VirtualThreadSQLiteStorage

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        storage.connection.executeUpdate(
            "CREATE TABLE ranks (id TEXT PRIMARY KEY)"
        )
    }
    @AfterEach
    fun tearDown() {
        storage.connection.close()
    }

    @Test
    fun `profile identity stays bound to rank UUID across rename and reload`() {
        val rankId = UUID.randomUUID()
        storage.connection.executeUpdate(
            "INSERT INTO ranks (id) VALUES (?)",
            rankId.toString(),
        )

        val repository = RankClaimPermissionProfileRepositorySQL(storage)
        assertEquals("Member", repository.getOrCreate(rankId, "Member"))
        assertEquals("Member", repository.getOrCreate(rankId, "Veteran"))

        val reloaded = RankClaimPermissionProfileRepositorySQL(storage)
        assertEquals("Member", reloaded.get(rankId))
        assertEquals("Member", reloaded.getOrCreate(rankId, "Officer"))
    }

    @Test
    fun `remove clears persisted and cached profile`() {
        val rankId = UUID.randomUUID()
        storage.connection.executeUpdate(
            "INSERT INTO ranks (id) VALUES (?)",
            rankId.toString(),
        )

        val repository = RankClaimPermissionProfileRepositorySQL(storage)
        repository.getOrCreate(rankId, "Member")

        assertTrue(repository.remove(rankId))
        assertNull(repository.get(rankId))

        val reloaded = RankClaimPermissionProfileRepositorySQL(storage)
        assertNull(reloaded.get(rankId))
    }
}
