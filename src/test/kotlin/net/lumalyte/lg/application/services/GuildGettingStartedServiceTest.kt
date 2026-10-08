package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.entities.GuildHomes
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.Position3D
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuildGettingStartedServiceTest {
    private val guilds = mockk<GuildService>(relaxed = true)
    private val members = mockk<MemberService>(relaxed = true)
    private val homes = mockk<GuildHomeActivationService>(relaxed = true)
    private val guild = Guild(UUID.randomUUID(), "New guild", createdAt = Instant.now())
    private val player = UUID.randomUUID()
    private val service = GuildGettingStartedService(guilds, members, homes)
    init {
        every { guilds.getGuild(guild.id) } returns guild
        every { members.getMember(player, guild.id) } returns mockk()
        every { members.getMemberCount(guild.id) } returns 1
        every { members.getMemberLimit(guild.id) } returns 5
    }
    private fun step(topic: GuildStartTopic) = service.snapshot(player, guild.id)!!.steps.first { it.topic == topic }
    @Test fun `nonmember and removed guild cannot read progress`() {
        every { members.getMember(player, guild.id) } returns null
        assertNull(service.snapshot(player, guild.id))
        every { guilds.getGuild(guild.id) } returns null
        assertNull(service.snapshot(player, guild.id))
    }
    @Test fun `no guild offers joining while management stays locked`() {
        every { guilds.getPlayerGuilds(player) } returns emptySet()
        val result = service.snapshot(player, null)!!
        assertEquals(GuildStartState.AVAILABLE, result.steps.first().state)
        assertEquals(8, result.steps.count { it.action == null })
    }
    @Test fun `member guidance never grants rank or invite management`() {
        assertNull(step(GuildStartTopic.RANKS).action)
        assertNull(step(GuildStartTopic.INVITES).action)
        assertEquals(GuildStartTopic.CHAT, step(GuildStartTopic.CHAT).action)
    }
    @Test fun `invites recheck permission and actual capacity`() {
        every { members.hasPermission(player, guild.id, RankPermission.MANAGE_MEMBERS) } returns true
        assertEquals(GuildStartTopic.INVITES, step(GuildStartTopic.INVITES).action)
        every { members.getMemberLimit(guild.id) } returns 1
        assertNull(step(GuildStartTopic.INVITES).action)
    }
    @Test fun `locked home leads permitted manager to real progression`() {
        every { members.hasPermission(player, guild.id, RankPermission.MANAGE_HOME) } returns true
        val step = step(GuildStartTopic.HOME)
        assertEquals(GuildStartState.LOCKED, step.state)
        assertEquals(GuildStartTopic.PROGRESSION, step.action)
    }
    @Test fun `saved inactive home is not falsely completed`() {
        val home = GuildHome(UUID.randomUUID(), Position3D(1, 2, 3))
        every { guilds.getGuild(guild.id) } returns guild.copy(homes = GuildHomes(mapOf("main" to home)))
        every { members.hasPermission(player, guild.id, RankPermission.MANAGE_HOME) } returns true
        every { guilds.getAvailableHomeSlots(guild.id) } returns 1
        assertEquals(GuildStartState.AVAILABLE, step(GuildStartTopic.HOME).state)
        every { homes.isActive(guild.id, "main") } returns true
        assertEquals(GuildStartState.COMPLETED, step(GuildStartTopic.HOME).state)
    }
    @Test fun `fresh milestone snapshot follows actual members and level`() {
        every { members.getMemberCount(guild.id) } returns 2
        every { guilds.getGuild(guild.id) } returns guild.copy(level = 2)
        assertEquals(GuildStartState.COMPLETED, step(GuildStartTopic.INVITES).state)
        assertEquals(GuildStartState.COMPLETED, step(GuildStartTopic.PROGRESSION).state)
        assertEquals(GuildStartState.AVAILABLE, step(GuildStartTopic.QUESTS).state)
    }
}
