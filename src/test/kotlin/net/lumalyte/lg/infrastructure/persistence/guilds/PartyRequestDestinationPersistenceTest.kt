package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.PartyRequest
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartyRequestDestinationPersistenceTest {
    @TempDir lateinit var directory: Path

    @Test fun `upgrade preserves old requests and persists party destination across reopen`() {
        val storage = VirtualThreadSQLiteStorage(directory.toFile())
        try {
            storage.connection.executeUpdate("""CREATE TABLE party_requests (
                id TEXT PRIMARY KEY, from_guild_id TEXT NOT NULL, to_guild_id TEXT NOT NULL,
                requester_id TEXT NOT NULL, message TEXT, status TEXT NOT NULL,
                created_at TEXT NOT NULL, expires_at TEXT NOT NULL)""")
            val old = PartyRequest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            storage.connection.executeUpdate("INSERT INTO party_requests VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                old.id.toString(), old.fromGuildId.toString(), old.toGuildId.toString(), old.requesterId.toString(),
                old.message, old.status.name, old.createdAt.toString(), old.expiresAt.toString())
            val repository = PartyRequestRepositorySQLite(storage)
            assertNull(repository.getById(old.id)?.partyId)
            val bound = PartyRequest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()).copy(partyId = UUID.randomUUID())
            assertTrue(repository.add(bound))
            val reopened = PartyRequestRepositorySQLite(storage)
            assertNull(reopened.getById(old.id)?.partyId)
            assertEquals(bound.partyId, reopened.getById(bound.id)?.partyId)
        } finally { storage.connection.close() }
    }
}
