package net.lumalyte.lg.domain.rewards

import org.junit.jupiter.api.Test
import kotlin.test.*

class RewardEntitlementsTest {
    private val catalog = RewardCatalog.chapterTwo()
    private val resolver = RewardEntitlementResolver(catalog)

    @Test
    fun `level unlocks offers but grants no purchased effects`() {
        val result = resolver.resolve(100, RewardOwnership())
        assertEquals(47_500L, result.bankCapacity)
        assertEquals(1, result.homeCapacity)
        assertEquals(50, result.memberCapacity)
        assertEquals(1.0, result.homeCooldownMultiplier)
        assertEquals(1.0, result.withdrawalFeeMultiplier)
        assertFalse(result.allyHomes)
        assertTrue(result.offers.filterNot { it.reward.effect is RewardEffect.HomeCapacity }
            .all { it.status == RewardOfferStatus.AVAILABLE })
        assertTrue(result.offers.filter { it.reward.effect is RewardEffect.HomeCapacity }
            .all { it.status == RewardOfferStatus.LOCKED })
        val start = resolver.resolve(1, RewardOwnership())
        assertEquals(8_000L, start.bankCapacity)
        assertTrue(start.offers.all { it.status == RewardOfferStatus.LOCKED })
    }

    @Test
    fun `legacy six slots accrue at exact level tiers and survive prestige`() {
        val expected = mapOf(1 to 1, 6 to 1, 7 to 2, 14 to 2, 15 to 3,
            19 to 3, 20 to 4, 29 to 4, 30 to 6, 100 to 6)
        expected.forEach { (level, count) ->
            assertEquals(count, earnedBaseHomeSlotsAtLevel(level))
        }
        val p5 = RewardOwnership(
            currentRun = setOf("bank-6"),
            permanent = (1..5).map { "bank-$it" }.toSet(),
            prestigeCount = 5, baseHomeSlotsEarned = 6,
            activeOutpostsByPrestige = (1..5).associateWith { "home-$it" }
        )
        assertEquals(16, resolver.resolve(100, p5).homeCapacity)
        val p6 = p5.copy(currentRun = emptySet(), prestigeCount = 6,
            permanent = p5.permanent + "bank-6")
        resolver.validateTransition(p5, p6)
        assertEquals(16, resolver.resolve(1, p6).homeCapacity)
        assertEquals(RewardOfferStatus.NO_IMPROVEMENT, resolver.resolve(100, p6).offer("home-6").status)
    }

    @Test
    fun `paired purchases are independent and higher tiers require no earlier purchase`() {
        val state = RewardOwnership(currentRun = setOf("cooldown-5"))
        val result = resolver.resolve(50, state)
        assertEquals(0.5, result.homeCooldownMultiplier)
        assertEquals(RewardOfferStatus.AVAILABLE, result.offer("ally-homes").status)
        assertEquals(RewardOfferStatus.PURCHASED, result.offer("cooldown-5").status)
        assertEquals(RewardOfferStatus.NO_IMPROVEMENT, result.offer("cooldown-1").status)
        assertEquals(RewardOfferStatus.LOCKED, result.offer("fee-5").status)
    }

    @Test
    fun `all purchases reach approved totals without multiplying tiers together`() {
        val state = RewardOwnership(
            currentRun = catalog.rewards.filterNot { it.permanentOnPurchase }.map { it.id }.toSet(),
            permanent = catalog.rewards.filter { it.permanentOnPurchase }.map { it.id }.toSet()
        )
        val result = resolver.resolve(100, state)
        assertEquals(57_500L, result.bankCapacity)
        assertEquals(1, result.homeCapacity) // Nine historical permanent Outposts do not create new paid grants.
        assertEquals(0.5, result.homeCooldownMultiplier)
        assertEquals(0.5, result.withdrawalFeeMultiplier)
        assertTrue(result.allyHomes)
    }

    @Test
    fun `permanent bank identity is counted once even if also present in the run`() {
        val state = RewardOwnership(currentRun = setOf("bank-1"), permanent = setOf("bank-1"), prestigeCount = 1)
        val result = resolver.resolve(5, state)
        assertEquals(10_500L, result.bankCapacity)
        assertEquals(1_000L, result.permanentBankCapacity)
        assertEquals(9_500L, result.currentRunBankCapacity)
        assertEquals(RewardOfferStatus.PERMANENT, result.offer("bank-1").status)
    }

    @Test
    fun `retained perks work below their unlock and homes are never reduced by reset`() {
        val state = RewardOwnership(
            permanent = setOf("bank-10", "cooldown-5", "ally-homes", "home-1", "home-9"),
            prestigeCount = 3, initialHomeCapacity = 8, baseHomeSlotsEarned = 6
        )
        val result = resolver.resolve(1, state)
        assertEquals(9_000L, result.bankCapacity)
        assertEquals(9, result.homeCapacity)
        assertEquals(0.5, result.homeCooldownMultiplier)
        assertTrue(result.allyHomes)
        assertEquals(50, result.memberCapacity)
    }

    @Test
    fun `safety ceiling and configured fixed membership are respected`() {
        val state = RewardOwnership(currentRun = setOf("bank-1"))
        val result = resolver.resolve(100, state, globalBankCeiling = 20_000L, memberCapacity = 73)
        assertEquals(20_000L, result.bankCapacity)
        assertEquals(73, result.memberCapacity)
        assertEquals(73, resolver.resolve(1, RewardOwnership(), memberCapacity = 73).memberCapacity)
    }

    @Test
    fun `prestige selection requires owned eligible nonpermanent reward and vacant family`() {
        val state = RewardOwnership(
            currentRun = setOf("bank-1", "cooldown-4", "fee-5", "ally-homes"),
            permanent = setOf("home-1", "cooldown-1"), prestigeCount = 1
        )
        assertEquals(setOf("bank-1", "fee-5", "ally-homes"), resolver.resolve(100, state).prestigeChoices.map { it.id }.toSet())
        assertTrue(resolver.resolve(99, state.copy(currentRun = state.currentRun - "fee-5")).prestigeChoices.isEmpty())
        assertFalse(resolver.resolve(100, state).prestigeChoices.any { it.id == "cooldown-1" })
    }

    @Test
    fun `six selections allow no further prestige choice`() {
        val state = RewardOwnership(
            currentRun = setOf("bank-7"),
            permanent = setOf("bank-1", "bank-2", "bank-3", "bank-4", "bank-5", "bank-6"),
            prestigeCount = 6,
        )
        assertTrue(resolver.resolve(100, state).prestigeChoices.isEmpty())
        assertEquals(14_000L, resolver.resolve(1, state.copy(currentRun = emptySet())).bankCapacity)
    }

    @Test
    fun `temporary stronger multiplier supersedes retained tier only for that run`() {
        val state = RewardOwnership(currentRun = setOf("cooldown-5"), permanent = setOf("cooldown-1"), prestigeCount = 1)
        assertEquals(0.5, resolver.resolve(100, state).homeCooldownMultiplier)
        assertEquals(0.9, resolver.resolve(1, state.copy(currentRun = emptySet())).homeCooldownMultiplier)
    }

    @Test
    fun `outpost purchase is limited to current prestige and historical ownership is not consumed`() {
        val before = RewardOwnership(
            permanent = setOf("bank-1", "home-1"), prestigeCount = 1, baseHomeSlotsEarned = 6
        )
        assertEquals(RewardOfferStatus.AVAILABLE, resolver.resolve(100, before).offer("home-1").status)
        val homes = resolver.recordPurchase(100, before, "home-1")
        assertEquals(before.permanent, homes.permanent)
        assertEquals(mapOf(1 to "home-1"), homes.activeOutpostsByPrestige)
        assertEquals(8, resolver.resolve(1, homes).homeCapacity)
        assertFailsWith<IllegalArgumentException> { resolver.recordPurchase(100, homes, "home-2") }
        assertFailsWith<IllegalArgumentException> { resolver.recordPurchase(100, RewardOwnership(), "home-1") }
        val p2 = homes.copy(permanent = homes.permanent + "bank-2", prestigeCount = 2)
        assertEquals(RewardOfferStatus.AVAILABLE, resolver.resolve(100, p2).offer("home-2").status)
        val second = resolver.recordPurchase(100, p2, "home-2")
        assertEquals(10, resolver.resolve(1, second).homeCapacity)
        val upgraded = resolver.recordPurchase(50, RewardOwnership(), "cooldown-5")
        assertFailsWith<IllegalArgumentException> { resolver.recordPurchase(50, upgraded, "cooldown-1") }
        assertFailsWith<IllegalArgumentException> { resolver.recordPurchase(100, before, "missing") }
    }

    @Test
    fun `invalid persisted state fails instead of silently discarding permanent assets`() {
        val invalid = listOf(
            RewardOwnership(currentRun = setOf("unknown")),
            RewardOwnership(permanent = setOf("unknown")),
            RewardOwnership(currentRun = setOf("home-1")),
            RewardOwnership(permanent = setOf("bank-1")),
            RewardOwnership(prestigeCount = 1),
            RewardOwnership(permanent = setOf("cooldown-1", "cooldown-2"), prestigeCount = 2)
        )
        invalid.forEach { state -> assertFailsWith<IllegalArgumentException> { resolver.resolve(100, state) } }
        assertFailsWith<IllegalArgumentException> { resolver.resolve(1, RewardOwnership(currentRun = setOf("bank-1"))) }
        assertFailsWith<IllegalArgumentException> { RewardOwnership(prestigeCount = 7) }
        assertFailsWith<IllegalArgumentException> { RewardOwnership(initialHomeCapacity = 0) }
        assertFailsWith<IllegalArgumentException> { RewardOwnership(baseHomeSlotsEarned = 7) }
        assertFailsWith<IllegalArgumentException> {
            RewardOwnership(prestigeCount = 1, activeOutpostsByPrestige = mapOf(2 to "home-1"))
        }
        assertFailsWith<IllegalArgumentException> { resolver.resolve(0, RewardOwnership()) }
        assertFailsWith<IllegalArgumentException> { resolver.resolve(101, RewardOwnership()) }
        assertFailsWith<IllegalArgumentException> { resolver.resolve(1, RewardOwnership(), globalBankCeiling = 0) }
        assertFailsWith<IllegalArgumentException> { resolver.resolve(1, RewardOwnership(), memberCapacity = 0) }
    }

    @Test
    fun `caller mutations cannot change captured ownership or catalog snapshots`() {
        val purchased = mutableSetOf("bank-1")
        val permanent = mutableSetOf("home-1")
        val state = RewardOwnership(purchased, permanent)
        purchased.clear()
        permanent.clear()
        val result = resolver.resolve(100, state)
        assertEquals(48_500L, result.bankCapacity)
        assertEquals(1, result.homeCapacity)
        val mutableRewards = catalog.level(5).rewards.toMutableList()
        val levels = (1..100).map { if (it == 5) catalog.level(it).copy(rewards = mutableRewards) else catalog.level(it) }.toMutableList()
        val snapshot = RewardCatalog(levels)
        mutableRewards.clear()
        levels.clear()
        assertEquals("bank-1", snapshot.level(5).rewards.single().id)
    }

    @Test
    fun `grandfathered home locations do not inflate earned home capacity`() {
        val nineOutposts = (1..9).map { "home-$it" }.toSet()
        val once = RewardOwnership(
            permanent = nineOutposts + "bank-1",
            initialHomeCapacity = 15,
            prestigeCount = 1, baseHomeSlotsEarned = 6,
        )
        // Fifteen saved homes remain intact, but nine historical Outposts give no free access.
        assertEquals(7, resolver.resolve(1, once).homeCapacity)
        assertEquals(15, once.initialHomeCapacity) // Evidence is immutable, not an entitlement.

        val sixth = RewardOwnership(
            permanent = nineOutposts + (1..6).map { "bank-$it" },
            initialHomeCapacity = 15,
            prestigeCount = 6, baseHomeSlotsEarned = 6,
            activeOutpostsByPrestige = (1..5).associateWith { "home-$it" },
        )
        assertEquals(16, resolver.resolve(1, sixth).homeCapacity)
        assertEquals(RewardOfferStatus.NO_IMPROVEMENT, resolver.resolve(100, sixth).offer("home-6").status)
    }

    @Test
    fun `invalid extra capacity in a corrupted reward catalog fails closed above sixteen`() {
        val modifiedLevels = (1..100).map { level ->
            val original = catalog.level(level)
            if (level != 10) original else original.copy(rewards = original.rewards.map { reward ->
                if (reward.id == "home-1") reward.copy(effect = RewardEffect.HomeCapacity(2)) else reward
            })
        }
        val corruptedCatalog = RewardCatalog(modifiedLevels)
        val allEarned = RewardOwnership(
            permanent = (1..9).map { "home-$it" }.toSet() + (1..6).map { "bank-$it" },
            prestigeCount = 6,
        )
        assertFailsWith<IllegalArgumentException> {
            RewardEntitlementResolver(corruptedCatalog).resolve(1, allEarned)
        }
    }

    @Test
    fun `historical home count never increases the absolute sixteen slot ceiling`() {
        val earned = RewardOwnership(
            permanent = (1..9).map { "home-$it" }.toSet() + (1..6).map { "bank-$it" },
            initialHomeCapacity = Int.MAX_VALUE,
            prestigeCount = 6, baseHomeSlotsEarned = 6,
            activeOutpostsByPrestige = (1..5).associateWith { "home-$it" },
        )
        assertEquals(16, resolver.resolve(1, earned).homeCapacity)
    }
}
