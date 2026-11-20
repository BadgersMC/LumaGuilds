package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PartyRequestRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.PartyRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** REQ-220: an expired invite is not offered anywhere, even before the cleanup task marks it. */
internal class PartyServiceExpiredRequestTest {
    private val requests = mockk<PartyRequestRepository>(relaxed = true)
    private val service = PartyServiceBukkit(
        mockk<PartyRepository>(relaxed = true), requests,
        mockk<MemberService>(relaxed = true), mockk<GuildService>(relaxed = true), mockk<LangService>(relaxed = true),
    )
    private val own = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Test
    fun expiredRequestsAreLeftOutOfBothLists() {
        val live = PartyRequest.create(other, own, UUID.randomUUID())
        val expired = live.copy(id = UUID.randomUUID(), expiresAt = Instant.now().minusSeconds(60), createdAt = Instant.now().minusSeconds(1000))
        every { requests.getPendingRequestsForGuild(own) } returns setOf(live, expired)
        every { requests.getPendingRequestsFromGuild(other) } returns setOf(live, expired)

        assertEquals(setOf(live.id), service.getPendingRequestsForGuild(own).map { it.id }.toSet())
        assertEquals(setOf(live.id), service.getPendingRequestsFromGuild(other).map { it.id }.toSet())
    }
}
