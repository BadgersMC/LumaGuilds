package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PartyRequestRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyStatus
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Behaviour specification for multi-guild party creation in [PartyServiceBukkit.createParty].
 *
 * A guild's internal chat channels (Guild_Chat, Officer_Chat, Leader_Chat) are stored as
 * single-guild [Party] rows. They must not count against the "one multi-guild party per
 * guild" rule, otherwise no guild with default channels can ever be invited into a party.
 */
internal class PartyServiceMultiGuildCreationTest {
    private val partyRepository = mockk<PartyRepository>(relaxed = true)
    private val memberService = mockk<MemberService>(relaxed = true)
    private val service = PartyServiceBukkit(
        partyRepository = partyRepository,
        partyRequestRepository = mockk<PartyRequestRepository>(relaxed = true),
        memberService = memberService,
        guildService = mockk<GuildService>(relaxed = true),
    )

    private val guildA = UUID.randomUUID()
    private val guildB = UUID.randomUUID()
    private val leaderId = UUID.randomUUID()

    private fun internalChannel(guildId: UUID) = Party(
        id = UUID.randomUUID(),
        name = "Guild_Chat",
        guildIds = setOf(guildId),
        leaderId = UUID.randomUUID(),
        status = PartyStatus.ACTIVE,
        createdAt = Instant.now()
    )

    private fun multiGuildParty(vararg guildIds: UUID) =
        Party(
            id = UUID.randomUUID(),
            name = "AllianceParty",
            guildIds = guildIds.toSet(),
            leaderId = leaderId,
            status = PartyStatus.ACTIVE,
            createdAt = Instant.now(),
        )

    private fun grantLeaderPermission() {
        every { memberService.getPlayerGuilds(leaderId) } returns setOf(guildA)
        every { memberService.hasPermission(leaderId, guildA, RankPermission.MANAGE_RELATIONS) } returns true
    }

    /** Internal single-guild channels must not block multi-guild party invites. */
    @Test
    fun internalChannelsAllowInvite() {
        grantLeaderPermission()
        // Both guilds only have their internal single-guild channels active.
        every { partyRepository.getActivePartiesByGuild(guildA) } returns setOf(internalChannel(guildA))
        every { partyRepository.getActivePartiesByGuild(guildB) } returns setOf(internalChannel(guildB))
        every { partyRepository.add(any()) } returns true

        val result = service.createParty(multiGuildParty(guildA, guildB))

        assertNotNull(result, "internal channel-parties must not count as an existing multi-guild party")
    }

    /** A guild may only be in one multi-guild party at a time. */
    @Test
    fun multiGuildPartyBlocksJoin() {
        grantLeaderPermission()
        val guildC = UUID.randomUUID()
        // guildA is already in a genuine multi-guild party.
        every { partyRepository.getActivePartiesByGuild(guildA) } returns
            setOf(internalChannel(guildA), multiGuildParty(guildA, guildC))
        every { partyRepository.getActivePartiesByGuild(guildB) } returns setOf(internalChannel(guildB))
        every { partyRepository.add(any()) } returns true

        val result = service.createParty(multiGuildParty(guildA, guildB))

        assertNull(result, "a guild may only be in one multi-guild party at a time")
    }
}
