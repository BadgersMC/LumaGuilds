package net.lumalyte.lg.application.services

import net.lumalyte.lg.config.PrestigeFanfareConfig
import net.lumalyte.lg.domain.rewards.FanfareCue
import net.lumalyte.lg.domain.rewards.PrestigeCompletion
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-164: who hears what, and when. */
class PrestigeFanfareTest {
    private val guild = UUID.randomUUID()
    private val memberA = UUID.randomUUID()
    private val memberB = UUID.randomUUID()
    private val offlineMember = UUID.randomUUID()
    private val outsider = UUID.randomUUID()

    private class Stage(val onlinePlayers: MutableSet<UUID>, val animatedPlayers: Set<UUID> = emptySet()) : FanfareStage {
        val frames = mutableListOf<Pair<UUID, Triple<Int, Int, String?>>>()
        var lastFrames = 0
        override fun animated(player: UUID) = player in animatedPlayers
        override fun frame(player: UUID, tier: Int, frame: Int, guildName: String?, last: Boolean) {
            frames += player to Triple(tier, frame, guildName); if (last) lastFrames++
        }
        val tasks = sortedMapOf<Int, MutableList<() -> Unit>>()
        val played = mutableListOf<Pair<UUID, FanfareCue>>()
        val titles = mutableListOf<Triple<UUID, Int, String>>()
        override fun online() = onlinePlayers.toList()
        override fun isOnline(player: UUID) = player in onlinePlayers
        override fun later(ticks: Int, task: () -> Unit) { tasks.getOrPut(ticks) { mutableListOf() }.add(task) }
        override fun play(player: UUID, cue: FanfareCue) { played += player to cue }
        override fun title(player: UUID, tier: Int, guildName: String) { titles += Triple(player, tier, guildName) }
        fun runUntil(tick: Int) { tasks.filterKeys { it <= tick }.values.flatten().forEach { it() }; tasks.keys.removeIf { it <= tick } }
        fun runAll() = runUntil(Int.MAX_VALUE)
    }

    private fun completion(tier: Int) =
        PrestigeCompletion(UUID.randomUUID(), guild, memberA, tier, "bank-1", Instant.now(), false)

    private fun fanfare(stage: Stage, config: PrestigeFanfareConfig = PrestigeFanfareConfig()) = PrestigeFanfare(
        settings = { config },
        members = { if (it == guild) setOf(memberA, memberB, offlineMember) else emptySet() },
        guildName = { if (it == guild) "Everlights" else null },
        stage = stage,
    )

    @Test
    fun `members hear the fanfare and see a title, everyone else hears the short version`() {
        val stage = Stage(mutableSetOf(memberA, memberB, outsider))
        fanfare(stage).prestiged(completion(2))
        stage.runAll()

        assertEquals(5, stage.played.count { it.first == memberA })
        assertEquals(5, stage.played.count { it.first == memberB })
        assertEquals(5, stage.played.count { it.first == outsider })
        assertTrue(stage.played.filter { it.first == outsider }.all { it.second.volume <= 0.3f })
        assertTrue(stage.played.none { it.first == offlineMember })
        assertEquals(setOf(Triple(memberA, 2, "Everlights"), Triple(memberB, 2, "Everlights")), stage.titles.toSet())
        assertEquals(0.849f, stage.played.first { it.first == memberA }.second.pitch)
    }

    @Test
    fun `cues land on their ticks and a player who leaves stops hearing them`() {
        val stage = Stage(mutableSetOf(memberA, memberB))
        fanfare(stage).prestiged(completion(1))
        stage.runUntil(4)
        val beforeLeaving = stage.played.count { it.first == memberB }
        stage.onlinePlayers.remove(memberB)
        stage.runAll()
        assertEquals(beforeLeaving, stage.played.count { it.first == memberB })
        assertEquals(2, beforeLeaving)
        assertTrue(stage.played.filter { it.first == memberA }.all { it.second.tick in 0..9 })
        assertEquals(5, stage.played.count { it.first == memberA })
    }

    @Test
    fun `switches turn parts off and a failure never escapes`() {
        val quiet = Stage(mutableSetOf(memberA, outsider))
        fanfare(quiet, PrestigeFanfareConfig(enabled = false)).prestiged(completion(1))
        quiet.runAll()
        assertTrue(quiet.played.isEmpty() && quiet.titles.isEmpty())

        val membersOnly = Stage(mutableSetOf(memberA, outsider))
        fanfare(membersOnly, PrestigeFanfareConfig(serverWide = false, memberTitle = false)).prestiged(completion(1))
        membersOnly.runAll()
        assertTrue(membersOnly.played.none { it.first == outsider })
        assertTrue(membersOnly.titles.isEmpty())

        val broken = object : FanfareStage by Stage(mutableSetOf(memberA)) {
            override fun online(): Collection<UUID> = error("server gone")
        }
        PrestigeFanfare({ PrestigeFanfareConfig() }, { setOf(memberA) }, { "X" }, broken).prestiged(completion(1))
    }

    @Test
    fun `members who can see it get the rank-up animation instead of the plain title`() {
        val stage = Stage(mutableSetOf(memberA, memberB, outsider), animatedPlayers = setOf(memberA))
        fanfare(stage).prestiged(completion(3))
        stage.runAll()

        val framesA = stage.frames.filter { it.first == memberA }.map { it.second }
        assertEquals((0 until 64).toList(), framesA.map { it.second })
        assertTrue(framesA.all { it.first == 3 })
        assertTrue(framesA.filter { it.second < 36 }.all { it.third == null })
        assertTrue(framesA.filter { it.second >= 36 }.all { it.third == "Everlights" })
        assertEquals(1, stage.lastFrames)
        assertEquals(setOf(Triple(memberB, 3, "Everlights")), stage.titles.toSet())
        assertTrue(stage.frames.none { it.first != memberA })
        // the animated member hears the shatter and the same fanfare 7 ticks later
        assertEquals(6, stage.played.count { it.first == memberA })
        assertEquals(5, stage.played.count { it.first == memberB })
    }

    @Test
    fun `animation timing puts the chime on the new badge`() {
        val stage = Stage(mutableSetOf(memberA), animatedPlayers = setOf(memberA))
        fanfare(stage).prestiged(completion(2))
        stage.runUntil(15)
        assertTrue(stage.played.any { it.second.sound == "block.amethyst_cluster.break" })
        assertTrue(stage.played.none { it.second.sound == "ui.toast.challenge_complete" })
        stage.runUntil(16)
        assertTrue(stage.played.any { it.second.sound == "ui.toast.challenge_complete" })
    }

    @Test
    fun `prestige one has no old badge to shatter and the animation can be switched off`() {
        val one = Stage(mutableSetOf(memberA), animatedPlayers = setOf(memberA))
        fanfare(one).prestiged(completion(1))
        one.runAll()
        assertTrue(one.played.none { it.second.sound == "block.amethyst_cluster.break" })
        assertTrue(one.frames.filter { it.second.second >= 26 }.all { it.second.third == "Everlights" })

        val off = Stage(mutableSetOf(memberA), animatedPlayers = setOf(memberA))
        fanfare(off, PrestigeFanfareConfig(memberAnimation = false)).prestiged(completion(2))
        off.runAll()
        assertTrue(off.frames.isEmpty())
        assertEquals(listOf(Triple(memberA, 2, "Everlights")), off.titles)
    }
}
