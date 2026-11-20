package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.domain.entities.GuildInvitation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** REQ-194: a banned player gets no invites from that guild. */
internal class GuildInvitationManagerBanTest {
    private val invited = UUID.randomUUID()
    private val guild = UUID.randomUUID()
    private val repository = mockk<GuildInvitationRepository>(relaxed = true)

    @AfterEach
    fun reset() {
        GuildInvitationManager.initialize(mockk(relaxed = true))
        GuildInvitationManager.guildBans = null
    }

    @Test
    fun `invites to a banned player are refused and older ones hidden`() {
        GuildInvitationManager.initialize(repository)
        GuildInvitationManager.guildBans = { g, p -> g == guild && p == invited }
        assertFalse(GuildInvitationManager.addInvite(guild, "Vibe", invited, UUID.randomUUID(), "Kaelith"))
        verify(exactly = 0) { repository.add(any()) }

        every { repository.getByPlayer(invited) } returns listOf(GuildInvitation(guild, "Vibe", invited, UUID.randomUUID(), "Kaelith"))
        assertEquals(emptyList(), GuildInvitationManager.getInvites(invited))
        assertEquals(null, GuildInvitationManager.getInviteByGuildName(invited, "Vibe"))
    }
}
