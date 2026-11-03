package net.lumalyte.lg.interaction.menus.guild

import io.mockk.*
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class GuildLeaderboardSnapshotTest {
    private val guilds = mockk<GuildService>()
    private val members = mockk<MemberService>()
    private val quests = mockk<QuestService>()
    private val viewer = mockk<Player>()
    private fun guild(name: String, level: Int = 1) = Guild(UUID.randomUUID(), name, createdAt = Instant.EPOCH).copy(level = level)
    @AfterEach fun cleanup() = unmockkAll()

    @Test fun scoresUseDescendingOrderNameTiesAndClampedPages() {
        val all = (1..6).map { guild("Guild $it", if (it <= 2) 50 else 10 - it) }
        every { guilds.getAllGuilds() } returns all.reversed().toSet()
        every { members.getMemberCount(any()) } returns 4
        val first = GuildLeaderboardSource(guilds, members, quests).snapshot(viewer, 0, -9)
        assertEquals(all.take(4).map { it.id }, first.rows.map { it.guild.id })
        val last = GuildLeaderboardSource(guilds, members, quests).snapshot(viewer, 0, 999)
        assertEquals(1, last.page)
        assertEquals(2, last.pages)
        assertEquals(listOf(5, 6), last.rows.map { it.rank })
        verify(exactly = 0) { quests.recordedWins() }
    }

    @Test fun questWinsRefreshAndEmptyDirectoryHasSafeBounds() {
        val a = guild("A"); val b = guild("B")
        every { guilds.getAllGuilds() } returns setOf(a, b)
        every { members.getMemberCount(any()) } returns 1
        every { quests.recordedWins() } returnsMany listOf(mapOf(a.id to 3), mapOf(b.id to 8))
        assertEquals(a.id, GuildLeaderboardSource(guilds, members, quests).snapshot(viewer, 1, 0).rows.first().guild.id)
        assertEquals(b.id, GuildLeaderboardSource(guilds, members, quests).snapshot(viewer, 1, 0).rows.first().guild.id)
        every { guilds.getAllGuilds() } returns emptySet()
        val empty = GuildLeaderboardSource(guilds, members, quests).snapshot(viewer, 0, 999)
        assertEquals(0, empty.page); assertEquals(1, empty.pages); assertTrue(empty.rows.isEmpty())
    }

    @Test fun onlineRankingsDoNotRevealHiddenOrOfflineMembers() {
        mockkStatic(Bukkit::class)
        val guild = guild("Private")
        val ids = List(3) { UUID.randomUUID() }
        every { guilds.getAllGuilds() } returns setOf(guild)
        every { members.getMemberCount(guild.id) } returns 3
        every { members.getGuildMembers(guild.id) } returns ids.map {
            Member(it, guild.id, UUID.randomUUID(), Instant.EPOCH)
        }.toSet()
        ids.forEachIndexed { index, id ->
            val player = mockk<Player>()
            every { Bukkit.getPlayer(id) } returns player
            every { player.isOnline } returns (index != 2)
            every { viewer.canSee(player) } returns (index == 0)
        }
        assertEquals(1, GuildLeaderboardSource(guilds, members, quests).snapshot(viewer, 2, 0).rows.single().score)
    }
}
