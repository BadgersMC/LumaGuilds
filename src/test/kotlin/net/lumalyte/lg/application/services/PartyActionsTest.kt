package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyRequest
import net.lumalyte.lg.domain.entities.PartyStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** REQ-211 to REQ-215: what /party does once the command has been parsed. */
@Suppress("FunctionNaming")
internal class PartyActionsTest {
    private val parties = mockk<PartyService>(relaxed = true)
    private val guilds = mockk<GuildService>(relaxed = true)
    private val actions = PartyActions(parties, guilds) { Duration.ofHours(24) }

    private val actor = UUID.randomUUID()
    private val own = Guild(UUID.randomUUID(), "Own", createdAt = Instant.EPOCH)
    private val other = Guild(UUID.randomUUID(), "Other", createdAt = Instant.EPOCH)
    private val third = Guild(UUID.randomUUID(), "Third", createdAt = Instant.EPOCH)

    private fun party(vararg ids: UUID, leader: UUID = actor) =
        Party(UUID.randomUUID(), "Raid", ids.toSet(), leader, PartyStatus.ACTIVE, Instant.now(), shared = true)

    private fun setUp(manage: Boolean = true, current: Party? = null) {
        every { guilds.getPlayerGuilds(actor) } returns setOf(own)
        every { guilds.getGuildByName("Other") } returns other
        every { guilds.getGuildByName("Own") } returns own
        every { guilds.getGuildByName("Nope") } returns null
        every { guilds.getGuild(other.id) } returns other
        every { guilds.getGuild(own.id) } returns own
        every { parties.canManageParties(actor, own.id) } returns manage
        every { parties.getActivePartiesForGuild(own.id) } returns setOfNotNull(current)
    }

    // ── invite (REQ-211) ──

    @Test fun `invite creates a waiting party when the guild has none`() {
        setUp()
        every { parties.createParty(any(), any()) } answers { firstArg() }
        every { parties.inviteToParty(any(), own.id, other.id, actor) } returns PartyRequest.create(own.id, other.id, actor)

        val result = actions.invite(actor, "Other")

        assertTrue(result is PartyResult.Done && result.created && result.guild == other, "$result")
        verify { parties.createParty(match { it.shared && it.guildIds == setOf(own.id) && it.leaderId == actor && it.expiresAt != null }, any()) }
    }

    @Test fun `invite uses the existing party`() {
        val current = party(own.id, third.id)
        setUp(current = current)
        every { parties.inviteToParty(current.id, own.id, other.id, actor) } returns PartyRequest.create(own.id, other.id, actor)

        val result = actions.invite(actor, "Other")

        assertTrue(result is PartyResult.Done && !result.created, "$result")
        verify(exactly = 0) { parties.createParty(any(), any()) }
    }

    @Test fun `invite refusals`() {
        setUp()
        assertEquals(PartyResult.UnknownGuild("Nope"), actions.invite(actor, "Nope"))
        assertEquals(PartyResult.OwnGuild, actions.invite(actor, "Own"))
        setUp(current = party(own.id, other.id))
        assertEquals(PartyResult.AlreadyInParty, actions.invite(actor, "Other"))
        setUp(manage = false)
        assertEquals(PartyResult.NoPermission, actions.invite(actor, "Other"))
        every { guilds.getPlayerGuilds(actor) } returns emptySet()
        assertEquals(PartyResult.NoGuild, actions.invite(actor, "Other"))
    }

    @Test fun `invite to a guild already invited is refused`() {
        setUp()
        every { parties.getPendingRequestsFromGuild(own.id) } returns setOf(PartyRequest.create(own.id, other.id, actor))
        assertEquals(PartyResult.AlreadyInvited, actions.invite(actor, "Other"))
    }

    // ── requests (REQ-212) ──

    @Test fun `accept, deny and cancel find the request by guild name`() {
        setUp()
        val incoming = PartyRequest.create(other.id, own.id, UUID.randomUUID())
        val outgoing = PartyRequest.create(own.id, other.id, actor)
        every { parties.getPendingRequestsForGuild(own.id) } returns setOf(incoming)
        every { parties.getPendingRequestsFromGuild(own.id) } returns setOf(outgoing)
        every { parties.acceptPartyRequest(incoming.id, own.id, actor) } returns party(own.id, other.id)
        every { parties.rejectPartyRequest(incoming.id, own.id, actor) } returns true
        every { parties.cancelPartyRequest(outgoing.id, own.id, actor) } returns true

        assertTrue(actions.accept(actor, "Other") is PartyResult.Done)
        assertTrue(actions.deny(actor, "Other") is PartyResult.Done)
        assertTrue(actions.cancel(actor, "Other") is PartyResult.Done)
    }

    @Test fun `accept without a request says so`() {
        setUp()
        every { parties.getPendingRequestsForGuild(own.id) } returns emptySet()
        assertEquals(PartyResult.NoRequest(other), actions.accept(actor, "Other"))
    }

    // ── leave and disband (REQ-214) ──

    @Test fun `leave succeeds when the guild is no longer in the party`() {
        val current = party(own.id, other.id)
        setUp(current = current)
        every { parties.leaveParty(current.id, own.id, actor) } returns null
        every { parties.getParty(current.id) } returns current.copy(status = PartyStatus.DISSOLVED)

        assertTrue(actions.leave(actor) is PartyResult.Done)
    }

    @Test fun `leave without a party says so`() {
        setUp()
        assertEquals(PartyResult.NoParty, actions.leave(actor))
    }

    @Test fun `only the leader's guild can disband`() {
        val leader = UUID.randomUUID()
        val current = party(own.id, other.id, leader = leader)
        setUp(current = current)
        every { guilds.getPlayerGuilds(leader) } returns setOf(other)

        assertEquals(PartyResult.NoPermission, actions.disband(actor))
        verify(exactly = 0) { parties.dissolveParty(any(), any()) }

        every { guilds.getPlayerGuilds(leader) } returns setOf(own)
        every { parties.dissolveParty(current.id, actor) } returns true
        assertTrue(actions.disband(actor) is PartyResult.Done)
    }

    // ── rename (REQ-215) ──

    @Test fun `rename checks the name`() {
        val current = party(own.id, other.id)
        setUp(current = current)
        every { parties.setPartyName(current.id, "Raid_Night", actor) } returns true

        assertEquals(PartyResult.BadName, actions.rename(actor, "Raid Night"))
        assertEquals(PartyResult.BadName, actions.rename(actor, "x".repeat(33)))
        assertTrue(actions.rename(actor, "Raid_Night") is PartyResult.Done)
    }

    // ── info and list (REQ-213) ──

    @Test fun `list shows only parties between guilds`() {
        val alliance = party(own.id, other.id)
        val waiting = party(third.id)
        val single = Party(UUID.randomUUID(), "Guild_Chat", setOf(third.id), actor, PartyStatus.ACTIVE, Instant.now())
        every { guilds.getAllGuilds() } returns setOf(own, other, third)
        every { parties.getActivePartiesForGuild(own.id) } returns setOf(alliance)
        every { parties.getActivePartiesForGuild(other.id) } returns setOf(alliance)
        every { parties.getActivePartiesForGuild(third.id) } returns setOf(waiting, single)

        assertEquals(listOf(alliance.id), actions.list().map { it.id })
    }

    @Test fun `current party skips single-guild parties`() {
        val single = Party(UUID.randomUUID(), "Guild_Chat", setOf(own.id), actor, PartyStatus.ACTIVE, Instant.now())
        val alliance = party(own.id, other.id)
        every { parties.getActivePartiesForGuild(own.id) } returns setOf(single, alliance)
        assertEquals(alliance.id, actions.partyOf(own.id)?.id)
    }
}
