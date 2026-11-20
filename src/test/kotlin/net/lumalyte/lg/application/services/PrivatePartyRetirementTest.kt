package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyStatus
import net.lumalyte.lg.domain.entities.PlayerPartyPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** REQ-207/REQ-208: single-guild parties are ended at startup and party chat pointing at them is cleared. */
internal class PrivatePartyRetirementTest {
    private val guildA = UUID.randomUUID()
    private val guildB = UUID.randomUUID()

    private fun party(name: String, guilds: Set<UUID>, shared: Boolean = false, status: PartyStatus = PartyStatus.ACTIVE) =
        Party(UUID.randomUUID(), name, guilds, UUID.randomUUID(), status, Instant.now(), shared = shared)

    @Test
    fun picksOnlyActiveSingleGuildParties() {
        val guildChat = party("Guild_Chat", setOf(guildA))
        val officer = party("Officer_Chat", setOf(guildA))
        val alliance = party("Alliance", setOf(guildA, guildB))
        val waiting = party("Waiting", setOf(guildA), shared = true)
        val old = party("Old", setOf(guildB), status = PartyStatus.DISSOLVED)

        val retired = PrivatePartyRetirement.toRetire(listOf(guildChat, officer, alliance, waiting, old))

        assertEquals(setOf(guildChat.id, officer.id), retired.map { it.id }.toSet())
        retired.forEach { assertEquals(PartyStatus.DISSOLVED, it.status) }
    }

    @Test
    fun endsPartiesWithoutDeletingAndClearsPreferences() {
        val guildChat = party("Guild_Chat", setOf(guildA))
        val alliance = party("Alliance", setOf(guildA, guildB))
        val inGuildChat = UUID.randomUUID()
        val inAlliance = UUID.randomUUID()
        val parties = mockk<PartyRepository>(relaxed = true)
        val preferences = mockk<PlayerPartyPreferenceRepository>(relaxed = true)
        every { parties.getAll() } returns setOf(guildChat, alliance)
        every { parties.update(any()) } returns true
        every { preferences.getAll() } returns setOf(
            PlayerPartyPreference(inGuildChat, guildChat.id),
            PlayerPartyPreference(inAlliance, alliance.id),
        )

        val ended = PrivatePartyRetirement(parties, preferences).run()

        assertEquals(1, ended)
        verify(exactly = 1) { parties.update(match { it.id == guildChat.id && it.status == PartyStatus.DISSOLVED }) }
        verify(exactly = 0) { parties.remove(any()) }
        verify(exactly = 1) { preferences.removeByPlayerId(inGuildChat) }
        verify(exactly = 0) { preferences.removeByPlayerId(inAlliance) }
    }
}
