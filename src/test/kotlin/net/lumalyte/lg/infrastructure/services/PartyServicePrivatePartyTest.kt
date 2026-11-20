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
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** REQ-207: a party only one guild can ever join is refused; a party waiting for its second guild is fine. */
internal class PartyServicePrivatePartyTest {
    private val partyRepository = mockk<PartyRepository>(relaxed = true)
    private val memberService = mockk<MemberService>(relaxed = true)
    private val service = PartyServiceBukkit(
        partyRepository = partyRepository,
        partyRequestRepository = mockk<PartyRequestRepository>(relaxed = true),
        memberService = memberService,
        guildService = mockk<GuildService>(relaxed = true),
        lang = mockk<LangService>(relaxed = true),
    )
    private val guildA = UUID.randomUUID()
    private val leaderId = UUID.randomUUID()

    private fun party(shared: Boolean) =
        Party(UUID.randomUUID(), "Chat", setOf(guildA), leaderId, createdAt = Instant.now(), shared = shared)

    private fun grant() {
        every { memberService.getPlayerGuilds(leaderId) } returns setOf(guildA)
        every { memberService.hasPermission(leaderId, guildA, RankPermission.MANAGE_RELATIONS) } returns true
        every { partyRepository.add(any()) } returns true
    }

    @Test
    fun refusesSingleGuildParty() {
        grant()
        assertNull(service.createParty(party(shared = false), suppressBroadcast = true))
        verify(exactly = 0) { partyRepository.add(any()) }
    }

    @Test
    fun allowsPartyWaitingForAnotherGuild() {
        grant()
        assertNotNull(service.createParty(party(shared = true), suppressBroadcast = true))
    }
}
