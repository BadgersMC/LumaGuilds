package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyRequest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartyInvitationMigrationSQLTest : RewardSqlTestFixture() {
    @Test fun `production schema accepts bound invitations and preserves private channels`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val parties = PartyRepositorySQLite(storage)
        val requests = PartyRequestRepositorySQLite(storage)
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val channel = Party(id = UUID.randomUUID(), name = "Guild_Chat", guildIds = setOf(from), leaderId = actor, createdAt = Instant.now())
        val shared = Party(id = UUID.randomUUID(), name = "Expedition", guildIds = setOf(from), leaderId = actor, createdAt = Instant.now(), shared = true)
        val request = PartyRequest.create(from, to, actor).copy(partyId = shared.id)
        assertTrue(parties.add(channel))
        assertTrue(parties.add(shared))
        assertTrue(requests.add(request))
        val reloadedParties = PartyRepositorySQLite(storage)
        val reloadedRequests = PartyRequestRepositorySQLite(storage)
        assertTrue(reloadedParties.getById(channel.id)!!.isPrivateParty())
        assertFalse(reloadedParties.getById(shared.id)!!.isPrivateParty())
        assertEquals(shared.id, reloadedRequests.getById(request.id)!!.partyId)
    }
    @Test fun `restart reconciles a committed membership before its request status was saved`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val parties = PartyRepositorySQLite(storage)
        val requests = PartyRequestRepositorySQLite(storage)
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val shared = Party(id = UUID.randomUUID(), guildIds = setOf(from), leaderId = actor, createdAt = Instant.now(), shared = true)
        val request = PartyRequest.create(from, to, actor).copy(partyId = shared.id)
        assertTrue(parties.add(shared))
        assertTrue(requests.add(request))
        assertTrue(parties.update(shared.copy(guildIds = setOf(from, to))))
        // Simulate a new service and repository instances after the membership commit.
        val reopenedParties = PartyRepositorySQLite(storage)
        val reopenedRequests = PartyRequestRepositorySQLite(storage)
        net.lumalyte.lg.infrastructure.services.PartyServiceBukkit(
            reopenedParties, reopenedRequests, io.mockk.mockk(relaxed = true),
            io.mockk.mockk(relaxed = true), io.mockk.mockk(relaxed = true)
        )
        assertEquals(net.lumalyte.lg.domain.entities.PartyRequestStatus.ACCEPTED, reopenedRequests.getById(request.id)!!.status)
        assertEquals(setOf(from, to), reopenedParties.getById(shared.id)!!.guildIds)
    }
}
