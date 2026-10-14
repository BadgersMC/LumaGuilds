package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.persistence.InviteBlockRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.GuildInvitation
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression coverage for personal invite blocking and compatibility boundaries. */
internal class InviteBlockServiceImplTest {
    private val me = UUID.randomUUID()
    private val blocked = UUID.randomUUID()
    private val guildmate = UUID.randomUUID()
    private val stranger = UUID.randomUUID()
    private val theirGuild = UUID.randomUUID()
    private val otherGuild = UUID.randomUUID()

    private val store =
        object : InviteBlockRepository {
            val BLOCKED_PLAYERS = mutableMapOf<UUID, MutableSet<UUID>>()
            override var isAvailable = true

            /** Reads the fixture directional block without SQL. */
            override fun isBlocked(ownerId: UUID, targetId: UUID) = BLOCKED_PLAYERS[ownerId]?.contains(targetId) == true

            /** Returns a detached fixture snapshot for the owner. */
            override fun blockedBy(ownerId: UUID) = BLOCKED_PLAYERS[ownerId].orEmpty().toSet()

            /** Simulates durable-save availability before mutating fixture blocks. */
            override fun setBlocked(ownerId: UUID, targetId: UUID, blocked: Boolean): Boolean {
                if (!isAvailable) return false
                if (blocked) {
                    BLOCKED_PLAYERS.getOrPut(
                        ownerId,
                    ) { mutableSetOf() }.add(targetId)
                } else {
                    BLOCKED_PLAYERS[ownerId]?.remove(targetId)
                }
                return true
            }
        }
    private val members =
        mockk<MemberService> {
            every { getPlayerGuilds(blocked) } returns setOf(theirGuild)
            every { isPlayerInGuild(blocked, theirGuild) } returns true
            every { isPlayerInGuild(blocked, otherGuild) } returns false
        }
    private val invitations = mockk<GuildInvitationRepository>(relaxed = true)
    private val service = InviteBlockServiceImpl(store, members, invitations)

    /** Creates a pending invitation for this fixture blocking player. */
    private fun invite(guild: UUID, inviter: UUID) = GuildInvitation(guild, "G", me, inviter, "inviter")

    /** Verifies a block refuses invites from them and from anyone in their guild, but not other guilds. */
    @Test
    fun blocksCoverCurrentGuild() {
        assertFalse(service.refusesGuildInvite(me, theirGuild, guildmate))
        assertTrue(service.setBlocked(me, blocked, true))
        assertTrue(service.refusesGuildInvite(me, theirGuild, blocked))
        assertTrue(service.refusesGuildInvite(me, theirGuild, guildmate))
        assertTrue(service.refusesGuildInvite(me, otherGuild, blocked))
        assertFalse(service.refusesGuildInvite(me, otherGuild, stranger))
        // Only the player who blocked is affected.
        assertFalse(service.refusesGuildInvite(stranger, theirGuild, guildmate))
    }

    /** Verifies blocking deletes the pending invites it covers and nothing else. */
    @Test
    fun coveredInvitesRemoved() {
        every {
            invitations.getByPlayer(me)
        } returns
            listOf(
                invite(theirGuild, guildmate),
                invite(otherGuild, blocked),
                invite(UUID.randomUUID(), stranger),
            )
        assertTrue(service.setBlocked(me, blocked, true))
        verify(exactly = 1) { invitations.remove(me, theirGuild) }
        verify(exactly = 1) { invitations.remove(me, otherGuild) }
        verify(exactly = 2) { invitations.remove(me, any()) }
    }

    /** Verifies unknown blocks fail closed for invites, and chat hides only the blocked sender. */
    @Test
    fun unavailableBlocksFailClosed() {
        service.setBlocked(me, blocked, true)
        assertTrue(service.hidesChat(me, blocked))
        assertFalse(service.hidesChat(me, guildmate))
        assertFalse(service.hidesChat(blocked, me))
        store.isAvailable = false
        assertTrue(service.refusesGuildInvite(stranger, otherGuild, stranger))
        assertFalse(service.setBlocked(me, guildmate, true))
    }
}
