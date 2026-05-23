package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PartyRequestRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyStatus
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Behaviour specification for [PartyServiceBukkit.removeGuildFromPartyAsSystem].
 *
 * The system removal exists so automated cleanup (guild disbandment) can detach a
 * guild from its parties without being blocked by the actor's permissions, and
 * without destroying parties that other guilds still rely on.
 */
internal class PartyServiceSystemRemovalTest {
    private val partyRepository = mockk<PartyRepository>(relaxed = true)
    private val memberService = mockk<MemberService>(relaxed = true)
    private val service = PartyServiceBukkit(
        partyRepository = partyRepository,
        partyRequestRepository = mockk<PartyRequestRepository>(relaxed = true),
        memberService = memberService,
        guildService = mockk<GuildService>(relaxed = true),
    )

    private fun party(vararg guildIds: UUID, status: PartyStatus = PartyStatus.ACTIVE) =
        Party(
            id = UUID.randomUUID(),
            name = "TestParty",
            guildIds = guildIds.toSet(),
            leaderId = UUID.randomUUID(),
            status = status,
            createdAt = Instant.now()
        )

    /** A multi-guild party must survive when one guild leaves. */
    @Test
    fun removeGuildKeepsPartyActive() {
        val g1 = UUID.randomUUID()
        val g2 = UUID.randomUUID()
        val g3 = UUID.randomUUID()
        val p = party(g1, g2, g3)
        every { partyRepository.getById(p.id) } returns p
        val saved = slot<Party>()
        every { partyRepository.update(capture(saved)) } returns true

        val result = service.removeGuildFromPartyAsSystem(p.id, g1)

        assertNotNull(result, "a multi-guild party must survive when one guild leaves")
        assertEquals(setOf(g2, g3), result!!.guildIds)
        assertEquals(PartyStatus.ACTIVE, result.status)
        assertEquals(setOf(g2, g3), saved.captured.guildIds)
        assertEquals(PartyStatus.ACTIVE, saved.captured.status)
    }

    /** A party with fewer than two guilds must be dissolved. */
    @Test
    fun removeGuildDissolvesParty() {
        val g1 = UUID.randomUUID()
        val g2 = UUID.randomUUID()
        val p = party(g1, g2)
        every { partyRepository.getById(p.id) } returns p
        val saved = slot<Party>()
        every { partyRepository.update(capture(saved)) } returns true

        val result = service.removeGuildFromPartyAsSystem(p.id, g1)

        assertNull(result, "a party with fewer than two guilds must be dissolved")
        assertEquals(PartyStatus.DISSOLVED, saved.captured.status)
    }

    /** A single-guild party must be dissolved when that guild leaves. */
    @Test
    fun removeOnlyGuildDissolves() {
        val g1 = UUID.randomUUID()
        val p = party(g1)
        every { partyRepository.getById(p.id) } returns p
        val saved = slot<Party>()
        every { partyRepository.update(capture(saved)) } returns true

        val result = service.removeGuildFromPartyAsSystem(p.id, g1)

        assertNull(result)
        assertEquals(PartyStatus.DISSOLVED, saved.captured.status)
    }

    /** System removal ignores actor permissions. */
    @Test
    fun systemRemovalIgnoresPermission() {
        val g1 = UUID.randomUUID()
        val g2 = UUID.randomUUID()
        val p = party(g1, g2)
        val unprivilegedActor = UUID.randomUUID() // not the party leader
        every { partyRepository.getById(p.id) } returns p
        every { partyRepository.update(any()) } returns true
        every { memberService.hasPermission(any(), any(), RankPermission.MANAGE_RELATIONS) } returns false

        // The permissioned path is blocked for an actor without MANAGE_RELATIONS...
        assertFalse(service.dissolveParty(p.id, unprivilegedActor))
        // ...but the system path still performs the cleanup.
        assertNull(service.removeGuildFromPartyAsSystem(p.id, g1))
        verify { partyRepository.update(match { it.status == PartyStatus.DISSOLVED }) }
    }

    /** Removing a guild not in the party must not change anything. */
    @Test
    fun removeMissingGuildNoChange() {
        val g1 = UUID.randomUUID()
        val g2 = UUID.randomUUID()
        val outsider = UUID.randomUUID()
        val p = party(g1, g2)
        every { partyRepository.getById(p.id) } returns p

        val result = service.removeGuildFromPartyAsSystem(p.id, outsider)

        assertNull(result)
        verify(exactly = 0) { partyRepository.update(any()) }
    }

    /** Removing from a dissolved party must not change anything. */
    @Test
    fun removeFromDissolvedNoChange() {
        val g1 = UUID.randomUUID()
        val g2 = UUID.randomUUID()
        val p = party(g1, g2, status = PartyStatus.DISSOLVED)
        every { partyRepository.getById(p.id) } returns p

        val result = service.removeGuildFromPartyAsSystem(p.id, g1)

        assertNull(result)
        verify(exactly = 0) { partyRepository.update(any()) }
    }
}
