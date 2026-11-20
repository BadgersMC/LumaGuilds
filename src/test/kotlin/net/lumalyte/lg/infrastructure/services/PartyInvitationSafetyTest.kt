package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PartyRequestRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyRequest
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class PartyInvitationSafetyTest {
    @Test fun `accepting a legacy guild invite never adds an outsider to an internal chat channel`() {
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val inviter = UUID.randomUUID()
        val accepter = UUID.randomUUID()
        val internal = Party(name = "Guild_Chat", id = UUID.randomUUID(), guildIds = setOf(from), leaderId = inviter, createdAt = Instant.now())
        val request = PartyRequest.create(from, to, inviter)
        val parties = mockk<PartyRepository>(relaxed = true)
        val requests = mockk<PartyRequestRepository>(relaxed = true)
        val members = mockk<MemberService>(relaxed = true)
        every { members.hasPermission(accepter, to, RankPermission.MANAGE_RELATIONS) } returns true
        every { requests.getById(request.id) } returns request
        every { requests.update(any()) } returns true
        every { parties.getActivePartiesByGuild(from) } returns setOf(internal)
        every { parties.update(any()) } returns true
        every { parties.add(any()) } returns true
        val service = PartyServiceBukkit(parties, requests, members, mockk<GuildService>(relaxed = true), mockk<LangService>(relaxed = true))
        val accepted = assertNotNull(service.acceptPartyRequest(request.id, to, accepter))
        assertNotEquals(internal.id, accepted.id, "an internal channel must never become a multi-guild party")
        verify(exactly = 0) { parties.update(match { it.id == internal.id }) }
    }
    @Test fun `bound invitation accepts only into its original party despite other channels`() {
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val inviter = UUID.randomUUID()
        val accepter = UUID.randomUUID()
        val channel = Party(id = UUID.randomUUID(), name = "Guild_Chat", guildIds = setOf(from), leaderId = inviter, createdAt = Instant.now())
        val intended = Party(id = UUID.randomUUID(), name = "Expedition", guildIds = setOf(from), leaderId = inviter, createdAt = Instant.now(), shared = true)
        val request = PartyRequest.create(from, to, inviter).copy(partyId = intended.id)
        val parties = mockk<PartyRepository>(relaxed = true)
        val requests = mockk<PartyRequestRepository>(relaxed = true)
        val members = mockk<MemberService>(relaxed = true)
        every { members.hasPermission(accepter, to, RankPermission.MANAGE_RELATIONS) } returns true
        every { requests.getById(request.id) } returns request
        every { requests.update(any()) } returns true
        every { parties.getById(intended.id) } returns intended
        every { parties.getActivePartiesByGuild(from) } returns linkedSetOf(channel, intended)
        every { parties.getActivePartiesByGuild(to) } returns emptySet()
        every { parties.update(any()) } returns true
        val service = PartyServiceBukkit(parties, requests, members, mockk<GuildService>(relaxed = true), mockk<LangService>(relaxed = true))
        val accepted = assertNotNull(service.acceptPartyRequest(request.id, to, accepter))
        assertEquals(intended.id, accepted.id)
        assertEquals(setOf(from, to), accepted.guildIds)
        verify(exactly = 0) { parties.update(match { it.id == channel.id }) }
    }

    @Test fun `bound invitation fails closed when the intended party is missing`() {
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val accepter = UUID.randomUUID()
        val request = PartyRequest.create(from, to, UUID.randomUUID()).copy(partyId = UUID.randomUUID())
        val parties = mockk<PartyRepository>(relaxed = true)
        val requests = mockk<PartyRequestRepository>(relaxed = true)
        val members = mockk<MemberService>(relaxed = true)
        every { members.hasPermission(accepter, to, RankPermission.MANAGE_RELATIONS) } returns true
        every { requests.getById(request.id) } returns request
        every { parties.getById(request.partyId!!) } returns null
        val service = PartyServiceBukkit(parties, requests, members, mockk<GuildService>(relaxed = true), mockk<LangService>(relaxed = true))
        assertNull(service.acceptPartyRequest(request.id, to, accepter))
        verify(exactly = 0) { parties.add(any()) }
        verify(exactly = 0) { parties.update(any()) }
        verify(exactly = 0) { requests.update(any()) }
    }
    @Test fun `an internal channel cannot be used as an invitation destination`() {
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val inviter = UUID.randomUUID()
        val channel = Party(id = UUID.randomUUID(), name = "Guild_Chat", guildIds = setOf(from), leaderId = inviter, createdAt = Instant.now())
        val parties = mockk<PartyRepository>(relaxed = true)
        val requests = mockk<PartyRequestRepository>(relaxed = true)
        val members = mockk<MemberService>(relaxed = true)
        every { members.hasPermission(inviter, from, RankPermission.MANAGE_RELATIONS) } returns true
        every { parties.getById(channel.id) } returns channel
        val service = PartyServiceBukkit(parties, requests, members, mockk<GuildService>(relaxed = true), mockk<LangService>(relaxed = true))
        assertNull(service.inviteToParty(channel.id, from, to, inviter))
        verify(exactly = 0) { requests.add(any()) }
    }

    @Test fun `status write failure can be retried without adding membership twice`() {
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val inviter = UUID.randomUUID()
        val accepter = UUID.randomUUID()
        var party = Party(id = UUID.randomUUID(), guildIds = setOf(from), leaderId = inviter, createdAt = Instant.now(), shared = true)
        val request = PartyRequest.create(from, to, inviter).copy(partyId = party.id)
        val parties = mockk<PartyRepository>(relaxed = true)
        val requests = mockk<PartyRequestRepository>(relaxed = true)
        val members = mockk<MemberService>(relaxed = true)
        every { members.hasPermission(accepter, to, RankPermission.MANAGE_RELATIONS) } returns true
        every { members.hasPermission(inviter, from, RankPermission.MANAGE_RELATIONS) } returns true
        every { requests.getById(request.id) } returns request
        every { requests.update(any()) } returnsMany listOf(false, true)
        every { parties.getById(party.id) } answers { party }
        every { parties.getActivePartiesByGuild(from) } answers { setOf(party) }
        every { parties.getActivePartiesByGuild(to) } answers { if (to in party.guildIds) setOf(party) else emptySet() }
        every { parties.update(any()) } answers { party = firstArg(); true }
        val service = PartyServiceBukkit(parties, requests, members, mockk<GuildService>(relaxed = true), mockk<LangService>(relaxed = true))
        assertNull(service.acceptPartyRequest(request.id, to, accepter))
        assertEquals(setOf(from, to), party.guildIds)
        assertEquals(false, service.rejectPartyRequest(request.id, to, accepter))
        assertEquals(false, service.cancelPartyRequest(request.id, from, inviter))
        assertNotNull(service.acceptPartyRequest(request.id, to, accepter))
        assertEquals(setOf(from, to), party.guildIds)
        verify(exactly = 2) { requests.update(any()) }
        verify(exactly = 0) { requests.remove(any()) }
    }
    @Test fun `invitation remains pending and records exact shared party`() {
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val inviter = UUID.randomUUID()
        val party = Party(id = UUID.randomUUID(), guildIds = setOf(from), leaderId = inviter, createdAt = Instant.now(), shared = true)
        val parties = mockk<PartyRepository>(relaxed = true)
        val requests = mockk<PartyRequestRepository>(relaxed = true)
        val members = mockk<MemberService>(relaxed = true)
        every { members.hasPermission(inviter, from, RankPermission.MANAGE_RELATIONS) } returns true
        every { parties.getById(party.id) } returns party
        every { requests.findActiveRequestBetweenGuilds(from, to) } returns null
        every { parties.findPartyByGuilds(setOf(from, to)) } returns null
        every { requests.add(any()) } returns true
        val guilds = mockk<GuildService>(relaxed = true)
        every { guilds.getGuild(any()) } returns null
        val service = PartyServiceBukkit(parties, requests, members, guilds, mockk<LangService>(relaxed = true))
        val sent = assertNotNull(service.inviteToParty(party.id, from, to, inviter))
        assertEquals(party.id, sent.partyId)
        assertEquals(net.lumalyte.lg.domain.entities.PartyRequestStatus.PENDING, sent.status)
        assertEquals(setOf(from), party.guildIds)
        verify(exactly = 0) { parties.update(any()) }
        verify(exactly = 1) { requests.add(match { it.partyId == party.id && it.toGuildId == to }) }
    }
    @Test fun `applied acceptance cannot be expired while status reconciliation fails`() {
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val party = Party(id = UUID.randomUUID(), guildIds = setOf(from, to), leaderId = actor, createdAt = Instant.now(), shared = true)
        val now = Instant.now()
        val request = PartyRequest.create(from, to, actor).copy(partyId = party.id, createdAt = now.minusSeconds(7200), expiresAt = now.minusSeconds(3600))
        val parties = mockk<PartyRepository>(relaxed = true)
        val requests = mockk<PartyRequestRepository>(relaxed = true)
        every { parties.getById(party.id) } returns party
        every { requests.getRequestsByStatus(net.lumalyte.lg.domain.entities.PartyRequestStatus.PENDING) } returns setOf(request)
        every { requests.getExpiredRequests() } returns setOf(request)
        every { requests.update(any()) } returns false
        val service = PartyServiceBukkit(parties, requests, mockk<MemberService>(relaxed = true), mockk<GuildService>(relaxed = true), mockk<LangService>(relaxed = true))
        service.processExpiredItems()
        verify(exactly = 0) { requests.update(match { it.status == net.lumalyte.lg.domain.entities.PartyRequestStatus.EXPIRED }) }
    }
}
