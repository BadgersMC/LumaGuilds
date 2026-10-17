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

/** Regression coverage for personal invite blocking and compatibility boundaries. */
internal class GuildInvitationManagerInviteBlockTest {
    private val invited = UUID.randomUUID()
    private val inviter = UUID.randomUUID()
    private val guild = UUID.randomUUID()
    private val repository = mockk<GuildInvitationRepository>(relaxed = true)
    private val blocks = mockk<InviteBlockService>()

    /** Clears singleton invitation state between tests. */
    @AfterEach
    fun reset() = GuildInvitationManager.initialize(mockk(relaxed = true))

    /** Verifies a refused invite is never stored. */
    @Test
    fun refusedInviteNotSaved() {
        every { blocks.refusesGuildInvite(invited, guild, inviter) } returns true
        GuildInvitationManager.initialize(repository, blocks)
        assertFalse(GuildInvitationManager.addInvite(guild, GUILD_NAME, invited, inviter, INVITER_NAME))
        verify(exactly = 0) { repository.add(any()) }
    }

    /** Verifies an invite that existed before the block cannot be used to join. */
    @Test
    fun olderInviteHidden() {
        every {
            repository.getByPlayer(invited)
        } returns listOf(GuildInvitation(guild, GUILD_NAME, invited, inviter, INVITER_NAME))
        every { blocks.refusesGuildInvite(invited, guild, inviter) } returns false
        GuildInvitationManager.initialize(repository, blocks)
        assertEquals(guild to GUILD_NAME, GuildInvitationManager.getInviteByGuildName(invited, "wardens"))
        every { blocks.refusesGuildInvite(invited, guild, inviter) } returns true
        assertNull(GuildInvitationManager.getInviteByGuildName(invited, "wardens"))
        assertEquals(emptyList(), GuildInvitationManager.getInvites(invited))
        every { repository.hasInvitation(invited, guild) } returns true
        every { repository.getInvitationCount(invited) } returns 1
        assertFalse(GuildInvitationManager.hasInvite(invited, guild))
        assertEquals(0, GuildInvitationManager.getInviteCount(invited))
    }

    private companion object {
        const val GUILD_NAME = "Wardens"
        const val INVITER_NAME = "Inviter"
    }
}
