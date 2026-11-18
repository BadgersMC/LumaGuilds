package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.values.SystemAward
import io.mockk.*
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID
import kotlin.test.*

internal class GuildAchievementRuntimeTest {
    @AfterEach fun cleanup() { unmockkAll(); MockBukkit.unmock() }

    @Test fun `initial projection waits until after the vanilla join reset`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        val guild = UUID.randomUUID()
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        every { members.getByGuild(guild) } returns emptySet()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(guild) } returns null
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        every { stalls.read(guild, player.uniqueId) } returns java.util.concurrent.CompletableFuture.completedFuture(
            net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        val runtime = GuildAchievementRuntime(plugin, members, progression, stalls)
        runtime.initializeAfterJoin(player)
        assertTrue(runtime.read(player)!!.progress.isEmpty())
        server.scheduler.performTicks(19L)
        assertTrue(runtime.read(player)!!.progress.isEmpty())
        server.scheduler.performTicks(2L)
        assertEquals(1, runtime.read(player)!!.progress["guilds_root"])
        verify(exactly = 0) { player.giveExp(any<Int>()) }
    }

    @Test fun `existing owned stall qualifies once but unavailable unowned and stale results do not`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        mockkStatic(org.bukkit.Bukkit::class)
        every { org.bukkit.Bukkit.getPlayer(player.uniqueId) } returns player
        val guild = UUID.randomUUID()
        var memberships = setOf(guild)
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } answers { memberships }
        every { members.getByGuild(guild) } returns setOf(net.lumalyte.lg.domain.entities.Member(
            player.uniqueId, guild, UUID.randomUUID(), java.time.Instant.EPOCH))
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        var next = java.util.concurrent.CompletableFuture<net.lumalyte.lg.application.services.StallReadResult>()
        every { stalls.read(guild, player.uniqueId) } answers { next }
        val runtime = GuildAchievementRuntime(plugin, members, mockk(), stalls)
        fun result(state: String) = net.lumalyte.lg.application.services.StallReadResult.Available(listOf(
            net.lumalyte.lg.application.services.GuildStallInfo("stall", "region", "world", state, 1, 60, null, null, null, emptyList())))
        fun complete(value: net.lumalyte.lg.application.services.StallReadResult) {
            runtime.refreshStallOwnership(player, guild)
            next.complete(value)
            server.scheduler.performOneTick()
            next = java.util.concurrent.CompletableFuture()
        }
        complete(net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        complete(result("UNOWNED"))
        assertNull(runtime.read(player)!!.progress["prime_real_estate"])
        runtime.refreshStallOwnership(player, guild)
        memberships = emptySet()
        next.complete(result("OWNED"))
        server.scheduler.performOneTick()
        assertNull(runtime.read(player)!!.progress["prime_real_estate"])
        memberships = setOf(guild)
        next = java.util.concurrent.CompletableFuture()
        complete(result("GRACE"))
        complete(result("OWNED"))
        assertEquals(1, runtime.read(player)!!.progress["prime_real_estate"])
        assertTrue("prime_real_estate" in runtime.read(player)!!.rewarded)
        verify(exactly = 0) { player.giveExp(any<Int>()) }
    }

    @Test fun `completion and track survive a new runtime without paying again`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers {
            javaClass.classLoader.getResourceAsStream("guild-achievements.json")
        }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        var paid = 0
        every { player.giveExp(any<Int>()) } answers { paid += firstArg<Int>() }
        val guild = UUID.randomUUID()
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        val progress = mockk<ProgressionRepository>()
        val first = GuildAchievementRuntime(plugin, members, progress)
        first.signal(player, "GUILD_WAR_WON", receipt = "war-123")
        assertEquals(300, paid)
        assertTrue(first.track(player, "to_the_victor"))
        val reopened = GuildAchievementRuntime(plugin, members, progress)
        assertEquals("to_the_victor", reopened.read(player)!!.tracked)
        reopened.signal(player, "GUILD_WAR_WON", receipt = "war-123")
        reopened.signal(player, "GUILD_WAR_WON", receipt = "war-456")
        assertEquals(300, paid)
        assertEquals(reopened.engine.definitions.size, reopened.engine.definitions.map { it.x to it.y }.distinct().size,
            "Vanilla advancement frames must not overlap")
    }

    @Test fun `malformed saved data is preserved and awards fail closed`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        val key = NamespacedKey(plugin, "guild_achievements_v1")
        player.persistentDataContainer.set(key, PersistentDataType.STRING, "invalid retained data")
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(UUID.randomUUID())
        val subject = GuildAchievementRuntime(plugin, members, mockk())
        subject.signal(player, "GUILD_WAR_WON")
        assertNull(subject.read(player))
        assertEquals("invalid retained data", player.persistentDataContainer.get(key, PersistentDataType.STRING))
        verify(exactly = 0) { player.giveExp(any<Int>()) }
    }

    @Test fun `joining checks member count and guild age`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        val guild = UUID.randomUUID()
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        every { members.getByGuild(guild) } returns (1..26).map {
            net.lumalyte.lg.domain.entities.Member(UUID.randomUUID(), guild, UUID.randomUUID(), java.time.Instant.EPOCH)
        }.toSet()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(guild) } returns null
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        every { stalls.read(guild, player.uniqueId) } returns java.util.concurrent.CompletableFuture.completedFuture(
            net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        val now = java.time.Instant.parse("2026-11-13T00:00:00Z")
        val runtime = GuildAchievementRuntime(plugin, members, progression, stalls,
            createdAt = { if (it == guild) now.minus(java.time.Duration.ofDays(200)).plusSeconds(60) else null }, clock = { now })
        runtime.initialize(player)
        val record = runtime.read(player)!!
        val byGoal = { req: String -> runtime.engine.definitions.filter { it.requirement == req }.associate { it.goal to it.id } }
        val count = byGoal("GUILD_MEMBER_COUNT")
        assertTrue(count.getValue(10) in record.rewarded && count.getValue(25) in record.rewarded)
        assertFalse(count.getValue(50) in record.rewarded)
        assertEquals(26, record.progress[count.getValue(50)])
        val age = byGoal("GUILD_AGE_DAYS")
        assertTrue(age.getValue(30) in record.rewarded && age.getValue(180) in record.rewarded)
        assertEquals(199, record.progress[age.getValue(365)])
    }

    @Test fun `combined playtime uses stored values for offline members and never reads their stats`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        // Bukkit.getPlayer returns the server's own player, so the statistic is set before spying.
        val player = spyk(server.addPlayer().apply { setStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE, 72_000 * 400) })
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        val guild = UUID.randomUUID()
        val offline = UUID.randomUUID()
        val leaver = UUID.randomUUID()
        val roster = mutableSetOf(player.uniqueId, offline, leaver)
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        every { members.getByGuild(guild) } answers { roster.map {
            net.lumalyte.lg.domain.entities.Member(it, guild, UUID.randomUUID(), java.time.Instant.EPOCH)
        }.toSet() }
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(guild) } returns null
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        every { stalls.read(any(), any()) } returns java.util.concurrent.CompletableFuture.completedFuture(
            net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        val store = net.lumalyte.lg.domain.values.MemberPlaytimeStore.InMemory(
            mutableMapOf(offline to 72_000L * 700, leaver to 72_000L * 5_000))
        mockkStatic(org.bukkit.Bukkit::class)
        val runtime = GuildAchievementRuntime(plugin, members, progression, stalls, playtimeStore = store)
        val goal = runtime.engine.definitions.first { it.requirement == "GUILD_COMBINED_PLAYTIME" && it.goal == 10_000 }
        runtime.initialize(player)
        assertEquals(6_100, runtime.read(player)!!.progress[goal.id])
        // Progress keeps its best value; the guild's total itself follows the roster.
        roster -= leaver
        assertEquals(1_100, GuildAchievementRuntime.playHours(runtime.playtime.memberTicks(guild)!!))
        verify(exactly = 0) { org.bukkit.Bukkit.getOfflinePlayer(any<UUID>()) }
        assertEquals(72_000L * 400, store.loadAll()[player.uniqueId])
    }

    @Test fun `combined play hours have no 1000 hour ceiling`() {
        assertEquals(100_001, GuildAchievementRuntime.playHours(listOf(100_001L * 72_000L)))
        assertEquals(Int.MAX_VALUE, GuildAchievementRuntime.playHours(listOf(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2)))
    }

    private data class Paid(val guild: UUID, val amount: Int, val tx: UUID)

    private fun xpRuntime(server: org.mockbukkit.mockbukkit.ServerMock, guild: UUID, players: List<org.bukkit.entity.Player>,
                          level: Int?, paid: MutableList<Paid>, accept: () -> Boolean = { true }): GuildAchievementRuntime {
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val members = mockk<MemberRepository>()
        players.forEach { every { members.getGuildsByPlayer(it.uniqueId) } returns setOf(guild) }
        every { members.getByGuild(guild) } returns emptySet()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(guild) } returns level?.let {
            mockk<net.lumalyte.lg.domain.entities.GuildProgression> { every { currentLevel } returns it }
        }
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        every { stalls.read(any(), any()) } returns java.util.concurrent.CompletableFuture.completedFuture(
            net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        return GuildAchievementRuntime(plugin, members, progression, stalls,
            guildXp = { g, amount, tx -> paid += Paid(g, amount, tx); outcome(accept()) })
    }

    private fun outcome(accepted: Boolean): SystemAward = SystemAward.FAILED.takeUnless { accepted } ?: SystemAward.PAID_NOW

    private fun onlinePlayer(server: org.mockbukkit.mockbukkit.ServerMock) = spyk(server.addPlayer()).also {
        every { it.isOnline } returns true
        every { it.saveData() } just Runs
    }

    @Test fun `a completed advancement pays its guild XP with one payout ID per guild`() {
        val server = MockBukkit.mock()
        val guild = UUID.randomUUID()
        val a = onlinePlayer(server); val b = onlinePlayer(server)
        val paid = mutableListOf<Paid>()
        val runtime = xpRuntime(server, guild, listOf(a, b), null, paid)
        runtime.signal(a, "GUILD_WAR_WON", receipt = "war-1")
        runtime.signal(b, "GUILD_WAR_WON", receipt = "war-1")
        val victor = runtime.engine.definitions.first { it.id == "to_the_victor" }
        val tx = GuildAchievementRuntime.payoutId(guild, "to_the_victor")
        // The second member's completion is skipped in memory; across restarts the XP ledger rejects the same ID.
        assertEquals(listOf(Paid(guild, victor.guildXp, tx)), paid)
        assertEquals(tx, GuildAchievementRuntime.payoutId(guild, "to_the_victor"))
        assertNotEquals(GuildAchievementRuntime.payoutId(UUID.randomUUID(), "to_the_victor"), tx)
        verify(exactly = 0) { a.giveExp(0) }
    }

    @Test fun `guilds already at a level are paid once even when members had already completed it`() {
        val server = MockBukkit.mock()
        val guild = UUID.randomUUID()
        val player = onlinePlayer(server)
        val paid = mutableListOf<Paid>()
        val runtime = xpRuntime(server, guild, listOf(player), 30, paid)
        // An older record that completed level 10 before guild XP rewards existed.
        player.persistentDataContainer.set(NamespacedKey("lumaguilds", "guild_achievements_v1"), PersistentDataType.STRING,
            """{"schema":1,"progress":{"expanding_the_empire":1},"rewarded":["expanding_the_empire"],"receipts":[],"tracked":null}""")
        runtime.initialize(player)
        runtime.initialize(player)
        val ids = paid.map { it.tx }
        assertTrue(GuildAchievementRuntime.payoutId(guild, "expanding_the_empire") in ids)
        assertTrue(GuildAchievementRuntime.payoutId(guild, "rising_banner") in ids)
        assertFalse(GuildAchievementRuntime.payoutId(guild, "seasoned_house") in ids)
        assertEquals(ids.size, ids.distinct().size, "Each guild-wide payout is attempted once per runtime")
    }

    @Test fun `a refused payout is retried later`() {
        val server = MockBukkit.mock()
        val guild = UUID.randomUUID()
        val player = onlinePlayer(server)
        val paid = mutableListOf<Paid>()
        var accept = false
        val runtime = xpRuntime(server, guild, listOf(player), 10, paid) { accept }
        val tx = GuildAchievementRuntime.payoutId(guild, "expanding_the_empire")
        runtime.initialize(player)
        val refused = paid.count { it.tx == tx }
        assertTrue(refused >= 1)
        accept = true
        runtime.initialize(player)
        assertEquals(refused + 1, paid.count { it.tx == tx })
        runtime.initialize(player)
        assertEquals(refused + 1, paid.count { it.tx == tx }, "A confirmed payout is not attempted again")
    }

    @Test fun `only the guild's first completion is announced to the server`() {
        assertTrue(GuildAchievementRuntime.broadcasts(SystemAward.PAID_NOW, joining = true))
        assertTrue(GuildAchievementRuntime.broadcasts(SystemAward.PAID_NOW, joining = false))
        assertFalse(GuildAchievementRuntime.broadcasts(SystemAward.ALREADY_PAID, joining = false))
        assertFalse(GuildAchievementRuntime.broadcasts(SystemAward.ALREADY_PAID, joining = true))
        // No reward to tell first from later: quiet on join, announced during play.
        assertFalse(GuildAchievementRuntime.broadcasts(null, joining = true))
        assertTrue(GuildAchievementRuntime.broadcasts(null, joining = false))
        // A ledger failure outside join must never hide a genuine first.
        assertTrue(GuildAchievementRuntime.broadcasts(SystemAward.FAILED, joining = false))
        assertFalse(GuildAchievementRuntime.broadcasts(SystemAward.FAILED, joining = true))
    }

    @Test fun `a member catching up on join is told privately that the guild earned it earlier`() {
        val server = MockBukkit.mock()
        val guild = UUID.randomUUID()
        val player = onlinePlayer(server)
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        every { members.getByGuild(guild) } returns emptySet()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(guild) } returns
            mockk<net.lumalyte.lg.domain.entities.GuildProgression> { every { currentLevel } returns 10 }
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        every { stalls.read(any(), any()) } returns java.util.concurrent.CompletableFuture.completedFuture(
            net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        val runtime = GuildAchievementRuntime(plugin, members, progression, stalls,
            guildXp = { _, _, _ -> SystemAward.ALREADY_PAID })
        runtime.initialize(player)
        val said = generateSequence { (player as org.mockbukkit.mockbukkit.entity.PlayerMock).nextComponentMessage() }
            .map { net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it) }.toList()
        assertTrue(said.any { "Expanding the Empire" in it && "earlier" in it }, said.toString())
    }

    @Test fun `old milestones a guild already qualifies for stay private for a newly joining member`() {
        val server = MockBukkit.mock()
        val guild = UUID.randomUUID()
        val player = onlinePlayer(server)
        val ledger = mutableSetOf<UUID>()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        every { members.getByGuild(guild) } returns emptySet()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(guild) } returns
            mockk<net.lumalyte.lg.domain.entities.GuildProgression> { every { currentLevel } returns 10 }
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        every { stalls.read(any(), any()) } returns java.util.concurrent.CompletableFuture.completedFuture(
            net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        // A real-ledger fake: the first attempt for an ID pays, later attempts are duplicates.
        val runtime = GuildAchievementRuntime(plugin, members, progression, stalls,
            guildXp = { _, _, id -> if (ledger.add(id)) SystemAward.PAID_NOW else SystemAward.ALREADY_PAID })
        runtime.initialize(player)
        val said = generateSequence { (player as org.mockbukkit.mockbukkit.entity.PlayerMock).nextComponentMessage() }
            .map { net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it) }.toList()
        assertTrue(said.any { "Expanding the Empire" in it && "earlier" in it }, said.toString())
        assertTrue(GuildAchievementRuntime.payoutId(guild, "expanding_the_empire") in ledger)
    }
}
