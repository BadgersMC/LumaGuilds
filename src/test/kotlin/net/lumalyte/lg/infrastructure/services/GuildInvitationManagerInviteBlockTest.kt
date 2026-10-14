package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.domain.entities.GuildInvitation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GuildInvitationManagerInviteBlockTest {
    private val invited = UUID.randomUUID()
    private val inviter = UUID.randomUUID()
    private val guild = UUID.randomUUID()
    private val repository = mockk<GuildInvitationRepository>(relaxed = true)
    private val blocks = mockk<InviteBlockService>()

    @AfterEach
    fun reset() = GuildInvitationManager.initialize(mockk(relaxed = true))

    @Test
    fun `a refused invite is never stored`() {
        every { blocks.refusesGuildInvite(invited, guild, inviter) } returns true
        GuildInvitationManager.initialize(repository, blocks)
        assertFalse(GuildInvitationManager.addInvite(guild, "Wardens", invited, inviter, "Inviter"))
        verify(exactly = 0) { repository.add(any()) }
    }

    @Test
    fun `an invite that existed before the block cannot be used to join`() {
        every { repository.getByPlayer(invited) } returns listOf(GuildInvitation(guild, "Wardens", invited, inviter, "Inviter"))
        every { blocks.refusesGuildInvite(invited, guild, inviter) } returns false
        GuildInvitationManager.initialize(repository, blocks)
        assertEquals(guild to "Wardens", GuildInvitationManager.getInviteByGuildName(invited, "wardens"))
        every { blocks.refusesGuildInvite(invited, guild, inviter) } returns true
        assertNull(GuildInvitationManager.getInviteByGuildName(invited, "wardens"))
        assertEquals(emptyList(), GuildInvitationManager.getInvites(invited))
    }
}
