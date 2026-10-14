package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.persistence.InviteBlockRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.GuildInvitation
import net.lumalyte.lg.domain.entities.Member
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InviteBlockServiceImplTest {
    private val me = UUID.randomUUID()
    private val blocked = UUID.randomUUID()
    private val guildmate = UUID.randomUUID()
    private val stranger = UUID.randomUUID()
    private val theirGuild = UUID.randomUUID()
    private val otherGuild = UUID.randomUUID()

    private val store = object : InviteBlockRepository {
        val blocks = mutableMapOf<UUID, MutableSet<UUID>>()
        override var isAvailable = true
        override fun isBlocked(ownerId: UUID, targetId: UUID) = blocks[ownerId]?.contains(targetId) == true
        override fun blockedBy(ownerId: UUID) = blocks[ownerId].orEmpty().toSet()
        override fun setBlocked(ownerId: UUID, targetId: UUID, blocked: Boolean): Boolean {
            if (!isAvailable) return false
            if (blocked) blocks.getOrPut(ownerId) { mutableSetOf() }.add(targetId) else blocks[ownerId]?.remove(targetId)
            return true
        }
    }
    private val members = mockk<MemberService> {
        every { getPlayerGuilds(blocked) } returns setOf(theirGuild)
        every { getGuildMembers(theirGuild) } returns setOf(member(blocked, theirGuild), member(guildmate, theirGuild))
        every { getGuildMembers(otherGuild) } returns setOf(member(stranger, otherGuild))
    }
    private val invitations = mockk<GuildInvitationRepository>(relaxed = true)
    private val service = InviteBlockServiceImpl(store, members, invitations)

    private fun member(player: UUID, guild: UUID) = Member(player, guild, UUID.randomUUID(), Instant.EPOCH)
    private fun invite(guild: UUID, inviter: UUID) = GuildInvitation(guild, "G", me, inviter, "inviter")

    @Test
    fun `a block refuses invites from them and from anyone in their guild, but not other guilds`() {
        assertFalse(service.refusesGuildInvite(me, theirGuild, guildmate))
        assertTrue(service.setBlocked(me, blocked, true))
        assertTrue(service.refusesGuildInvite(me, theirGuild, blocked))
        assertTrue(service.refusesGuildInvite(me, theirGuild, guildmate))
        assertTrue(service.refusesGuildInvite(me, otherGuild, blocked))
        assertFalse(service.refusesGuildInvite(me, otherGuild, stranger))
        // Only the player who blocked is affected.
        assertFalse(service.refusesGuildInvite(stranger, theirGuild, guildmate))
    }

    @Test
    fun `blocking deletes the pending invites it covers and nothing else`() {
        every { invitations.getByPlayer(me) } returns listOf(invite(theirGuild, guildmate), invite(otherGuild, blocked), invite(UUID.randomUUID(), stranger))
        assertTrue(service.setBlocked(me, blocked, true))
        verify(exactly = 1) { invitations.remove(me, theirGuild) }
        verify(exactly = 1) { invitations.remove(me, otherGuild) }
        verify(exactly = 2) { invitations.remove(me, any()) }
    }

    @Test
    fun `unknown blocks fail closed for invites, and chat hides only the blocked sender`() {
        service.setBlocked(me, blocked, true)
        assertTrue(service.hidesChat(me, blocked))
        assertFalse(service.hidesChat(me, guildmate))
        assertFalse(service.hidesChat(blocked, me))
        store.isAvailable = false
        assertTrue(service.refusesGuildInvite(stranger, otherGuild, stranger))
        assertFalse(service.setBlocked(me, guildmate, true))
    }
}
