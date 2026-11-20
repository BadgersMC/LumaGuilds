package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildBanRepository
import net.lumalyte.lg.domain.entities.GuildBan
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-194: guild ban rules. */
class GuildBanServiceTest {
    private val guild = UUID.randomUUID()
    private val officer = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val outsider = UUID.randomUUID()
    private val now = Instant.parse("2026-11-10T06:00:00Z")
    private val saved = mutableSetOf<UUID>()
    private val repo = object : GuildBanRepository {
        var failReads = false
        override fun isBanned(guildId: UUID, playerId: UUID): Boolean {
            check(!failReads) { "db down" }
            return guildId == guild && playerId in saved
        }
        override fun bans(guildId: UUID) = saved.map { GuildBan(guild, it, officer, now) }
        override fun add(ban: GuildBan) = saved.add(ban.playerId)
        override fun remove(guildId: UUID, playerId: UUID) = saved.remove(playerId)
        override fun removeGuild(guildId: UUID) = saved.clear()
    }
    private val priorities = mutableMapOf(owner to 0, officer to 2, member to 5)
    private val members = mockk<MemberService>()
    private val removedInvites = mutableListOf<UUID>()

    private fun service(): GuildBanService {
        every { members.hasPermission(any(), guild, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_MEMBERS) } answers {
            firstArg<UUID>() == officer || firstArg<UUID>() == owner
        }
        every { members.removeMember(any(), guild, any()) } answers { priorities.remove(firstArg()) != null }
        return GuildBanServiceImpl(
            repo, members,
            rankPriority = { p, g -> if (g == guild) priorities[p] else null },
            removeInvites = { p, _ -> removedInvites += p },
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `banning a member removes them, saves the ban and clears invites`() {
        val s = service()
        assertEquals(BanResult.Banned(wasMember = true), s.ban(officer, guild, member))
        verify { members.removeMember(member, guild, officer) }
        assertTrue(s.isBanned(guild, member))
        assertEquals(listOf(member), removedInvites)
    }

    @Test
    fun `banning someone outside the guild just saves the ban`() {
        val s = service()
        assertEquals(BanResult.Banned(wasMember = false), s.ban(officer, guild, outsider))
        verify(exactly = 0) { members.removeMember(outsider, guild, any()) }
        assertTrue(s.isBanned(guild, outsider))
    }

    @Test
    fun `refusals change nothing`() {
        val s = service()
        assertEquals(BanResult.NoPermission, s.ban(member, guild, outsider))
        assertEquals(BanResult.Self, s.ban(officer, guild, officer))
        assertEquals(BanResult.Owner, s.ban(officer, guild, owner))
        priorities[UUID(0, 1)] = 2
        assertEquals(BanResult.NotOutranked, s.ban(officer, guild, UUID(0, 1)))
        assertTrue(saved.isEmpty())
        assertTrue(removedInvites.isEmpty())
        s.ban(officer, guild, outsider)
        assertEquals(BanResult.AlreadyBanned, s.ban(officer, guild, outsider))
    }

    @Test
    fun `check previews without changing anything`() {
        val s = service()
        assertEquals(BanResult.Banned(wasMember = true), s.check(officer, guild, member))
        assertFalse(s.isBanned(guild, member))
        verify(exactly = 0) { members.removeMember(any(), any(), any()) }
    }

    @Test
    fun `a member is not removed when the ban cannot be saved`() {
        val failing = object : GuildBanRepository by repo {
            override fun add(ban: GuildBan): Boolean = error("db down")
        }
        every { members.hasPermission(officer, guild, any()) } returns true
        val s = GuildBanServiceImpl(failing, members, { p, _ -> priorities[p] }, { _, _ -> })
        assertEquals(BanResult.Failed, s.ban(officer, guild, member))
        verify(exactly = 0) { members.removeMember(any(), any(), any()) }
    }

    @Test
    fun `unban needs manage members`() {
        val s = service()
        s.ban(officer, guild, outsider)
        assertEquals(UnbanResult.NO_PERMISSION, s.unban(member, guild, outsider))
        assertEquals(UnbanResult.UNBANNED, s.unban(officer, guild, outsider))
        assertEquals(UnbanResult.NOT_BANNED, s.unban(officer, guild, outsider))
        assertFalse(s.isBanned(guild, outsider))
    }

    @Test
    fun `an unreadable ban list counts as banned`() {
        val s = service()
        repo.failReads = true
        assertTrue(s.isBanned(guild, outsider))
    }
}
