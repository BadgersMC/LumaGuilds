package net.lumalyte.lg.infrastructure.web.handlers

import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.persistence.PrestigeAdministration
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.config.PrestigeConfig
import net.lumalyte.lg.domain.entities.MembershipHistory
import net.lumalyte.lg.domain.rewards.AppliedPrestige
import net.lumalyte.lg.domain.rewards.DisbandedPrestigeGuild
import net.lumalyte.lg.domain.rewards.HallStanding
import net.lumalyte.lg.domain.rewards.PrestigeAdjustment
import net.lumalyte.lg.domain.rewards.PrestigeRecord
import net.lumalyte.lg.domain.rewards.RewardCatalog
import net.lumalyte.lg.infrastructure.web.dto.BannerDto
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrestigeHallFeedTest {
    private val day = 86_400_000L
    private fun at(days: Int): Instant = Instant.ofEpochMilli(1_790_000_000_000L + days * day)

    private val wardens = UUID.fromString("00000000-0000-4000-8000-00000000000a")
    private val tide = UUID.fromString("00000000-0000-4000-8000-00000000000b")
    private val granted = UUID.fromString("00000000-0000-4000-8000-00000000000c")
    private val oldGuard = UUID.fromString("00000000-0000-4000-8000-00000000000d")
    private val alice = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val bob = UUID.fromString("00000000-0000-4000-8000-000000000002")
    private val cara = UUID.fromString("00000000-0000-4000-8000-000000000003")

    private fun applied(guild: UUID, tier: Int, day: Int, reward: String = "fee-5", fee: Long = 8_000L) =
        AppliedPrestige(PrestigeRecord(UUID.randomUUID(), guild, alice, tier, reward, at(day)), fee)

    private val receipts = listOf(
        applied(oldGuard, 1, 0),
        applied(wardens, 1, 2),
        applied(wardens, 2, 10, fee = 16_000L),
        applied(tide, 1, 12),
        applied(wardens, 3, 20, reward = "bank-10", fee = 24_000L),
    )

    private val history = object : PrestigeHistory {
        override fun history(guildId: UUID) = receipts.map { it.record }.filter { it.guildId == guildId }
        override fun firstToReach(tier: Int) = receipts.map { it.record }.firstOrNull { it.tier == tier }
        override fun standings() = listOf(
            HallStanding(tide, 1, at(12)),
            HallStanding(granted, 2, Instant.ofEpochMilli(Long.MAX_VALUE / 2)),
            HallStanding(wardens, 3, at(20)),
        )
        override fun applied() = receipts
    }

    private val administration = mockk<PrestigeAdministration> {
        every { adjustments(any()) } returns emptyList()
        every { adjustments(granted) } returns listOf(PrestigeAdjustment(granted, bob, 0, 2, at(15)))
    }

    private val stints = object : MembershipHistoryRepository {
        override fun getByGuild(guildId: UUID): List<MembershipHistory> = if (guildId != wardens) emptyList() else listOf(
            // Bob was there for tier I and left before tier II.
            MembershipHistory(UUID.randomUUID(), bob, wardens, at(1), at(5)),
        )
        override fun openStint(playerId: UUID, guildId: UUID) = true
        override fun closeStint(playerId: UUID, guildId: UUID, reason: net.lumalyte.lg.domain.entities.DepartureReason) = true
        override fun getByPlayer(playerId: UUID) = emptyList<MembershipHistory>()
        override fun getQualifiedUnawarded(joinedAtOrBefore: Instant) = emptyList<MembershipHistory>()
        override fun markRecruitXpAwarded(stintId: UUID, awardedAt: Instant) = true
    }

    private val guilds = object : PrestigeHallFeed.Guilds {
        val names = mapOf(wardens to "Wardens", tide to "Tidecallers", granted to "Granted")
        override fun name(guildId: UUID) = names[guildId]
        override fun tag(guildId: UUID) = if (guildId == wardens) "WRD" else null
        override fun level(guildId: UUID) = 12
        override fun members(guildId: UUID) = when (guildId) {
            // Cara joined after tier II, so she is credited only for tier III.
            wardens -> mapOf(alice to at(0), cara to at(15))
            else -> mapOf(alice to at(0))
        }
        override fun banner(guildId: UUID): BannerDto? = null
    }

    private var now = at(30)
    private fun feed(stalls: Boolean = false) = PrestigeHallFeed(
        history, administration,
        { id -> if (id == oldGuard) DisbandedPrestigeGuild(id, "Old Guard", 1, at(1)) else null },
        stints, guilds, RewardCatalog.chapterTwo(), { PrestigeConfig() }, { stalls }, { now },
    )

    @Test fun `guilds follow the Hall order and keep their own story`() {
        val snapshot = feed().build()
        assertEquals(listOf("Wardens", "Granted", "Tidecallers"), snapshot.guilds.map { it.name })
        assertEquals(listOf(1, 2, 3), snapshot.guilds.map { it.hallRank })

        val w = snapshot.guilds.first()
        assertEquals(3, w.tier)
        assertEquals(listOf(1, 2, 3), w.history.map { it.tier })
        assertEquals(listOf(false, true, true), w.history.map { it.firstOnServer })
        assertEquals("Bank Extension 10", w.history[2].keptReward?.name)
        assertEquals(listOf(8_000L, 16_000L, 24_000L), w.history.map { it.feePaid })
        assertEquals(listOf(alice, bob).map { it.toString() }, w.history[0].credited)
        assertEquals(listOf(alice.toString()), w.history[1].credited)
        assertEquals(listOf(alice, cara).map { it.toString() }, w.history[2].credited)
        assertEquals(w.history.last().reachedAt, w.reachedAt)
    }

    @Test fun `a staff grant counts as reaching the tiers it skipped, with no receipt`() {
        val g = feed().build().guilds.single { it.name == "Granted" }
        assertEquals(listOf(1, 2), g.history.map { it.tier })
        assertTrue(g.history.all { it.keptReward == null && it.feePaid == null && !it.firstOnServer })
    }

    @Test fun `server firsts survive a disband and unclaimed tiers stay open`() {
        val snapshot = feed().build()
        val first = snapshot.tiers[0].first!!
        assertEquals("Old Guard", first.name)
        assertTrue(first.disbanded)
        assertEquals("Wardens", snapshot.tiers[2].first?.name)
        assertNull(snapshot.tiers[3].first)
        assertEquals(listOf(1, 1, 1, 0, 0, 0), snapshot.tiers.map { it.guildCount })
        assertEquals(3, snapshot.totals.tiersClaimed)
        assertEquals(4, snapshot.totals.guildsPrestiged)
        assertEquals(5, snapshot.totals.prestiges)
        assertEquals(64_000L, snapshot.totals.goldSpent)
        assertEquals(listOf("Wardens", "Tidecallers", "Wardens", "Wardens", "Old Guard"), snapshot.recent.map { it.name })
        assertTrue(snapshot.recent.last().disbanded)
    }

    @Test fun `the extra stall is only listed while the market can honour it`() {
        assertEquals(listOf("second_ally_home"), feed().build().tiers[1].perks)
        assertEquals(emptyList(), feed().build().tiers[2].perks)
        assertEquals(listOf("extra_stall"), feed(stalls = true).build().tiers[2].perks)
        assertEquals(listOf("second_vault"), feed().build().tiers[5].perks)
    }

    @Test fun `revision changes with the data, not the clock`() {
        val first = feed().build()
        now = at(31)
        val later = feed().build()
        assertEquals(first.revision, later.revision)
        assertFalse(first.generatedAt == later.generatedAt)
        assertTrue(Regex("^[0-9a-f]{64}$").matches(first.revision))
    }

    @Test fun `null fields are written out, as the contract requires`() {
        val json = PrestigeHallFeed.GSON.toJson(feed().build())
        assertTrue("\"tag\":null" in json)
        assertTrue("\"banner\":null" in json)
        assertTrue("\"first\":null" in json)
        assertTrue("\"keptReward\":null" in json)
        // Kept for the schema check run against the website contract.
        File("build/prestige-feed-sample.json").apply { parentFile.mkdirs() }.writeText(json)
    }
}
