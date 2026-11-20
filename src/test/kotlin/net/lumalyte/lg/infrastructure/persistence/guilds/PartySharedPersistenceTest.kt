package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class PartySharedPersistenceTest {
    @TempDir lateinit var directory: Path
    @Test fun `legacy channels remain private and shared pending parties remain shared after reopening`() {
        val storage = VirtualThreadSQLiteStorage(directory.toFile())
        try {
            storage.connection.executeUpdate("""CREATE TABLE parties (
              id TEXT PRIMARY KEY, name TEXT, guild_ids TEXT NOT NULL, leader_id TEXT NOT NULL,
              status TEXT NOT NULL, created_at TEXT NOT NULL, expires_at TEXT, restricted_roles TEXT)""")
            val guild = UUID.randomUUID()
            val leader = UUID.randomUUID()
            val oldId = UUID.randomUUID()
            storage.connection.executeUpdate("INSERT INTO parties VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                oldId.toString(), "Guild_Chat", guild.toString(), leader.toString(), "ACTIVE", Instant.now().toString(), null, null)
            val repo = PartyRepositorySQLite(storage)
            assertTrue(repo.getById(oldId)!!.isPrivateParty())
            val shared = Party(id = UUID.randomUUID(), guildIds = setOf(guild), leaderId = leader, createdAt = Instant.now(), shared = true)
            assertTrue(repo.add(shared))
            val reopened = PartyRepositorySQLite(storage)
            assertTrue(reopened.getById(oldId)!!.isPrivateParty())
            assertFalse(reopened.getById(shared.id)!!.isPrivateParty())
            assertEquals(true, reopened.getById(shared.id)!!.shared)
        } finally { storage.connection.close() }
    }
}
