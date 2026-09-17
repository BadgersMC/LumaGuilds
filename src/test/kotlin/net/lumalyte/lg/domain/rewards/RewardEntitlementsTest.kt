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
        assertTrue(result.offers.all { it.status == RewardOfferStatus.AVAILABLE })
        val start = resolver.resolve(1, RewardOwnership())
        assertEquals(8_000L, start.bankCapacity)
        assertTrue(start.offers.all { it.status == RewardOfferStatus.LOCKED })
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
        assertEquals(10, result.homeCapacity)
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
            prestigeCount = 3, initialHomeCapacity = 8
        )
        val result = resolver.resolve(1, state)
        assertEquals(9_000L, result.bankCapacity)
        assertEquals(13, result.homeCapacity)
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
    fun `three selections allow no further prestige choice`() {
        val state = RewardOwnership(currentRun = setOf("bank-4"), permanent = setOf("bank-1", "bank-2", "bank-3"), prestigeCount = 3)
        assertTrue(resolver.resolve(100, state).prestigeChoices.isEmpty())
        assertEquals(11_000L, resolver.resolve(1, state.copy(currentRun = emptySet())).bankCapacity)
    }

    @Test
    fun `temporary stronger multiplier supersedes retained tier only for that run`() {
        val state = RewardOwnership(currentRun = setOf("cooldown-5"), permanent = setOf("cooldown-1"), prestigeCount = 1)
        assertEquals(0.5, resolver.resolve(100, state).homeCooldownMultiplier)
        assertEquals(0.9, resolver.resolve(1, state.copy(currentRun = emptySet())).homeCooldownMultiplier)
    }

    @Test
    fun `ownership after purchase permanently records homes and blocks free duplicate upgrades`() {
        val before = RewardOwnership()
        val homes = resolver.recordPurchase(10, before, "home-1")
        assertEquals(setOf("home-1"), homes.permanent)
        assertTrue(homes.currentRun.isEmpty())
        assertEquals(2, resolver.resolve(1, homes).homeCapacity)
        assertFailsWith<IllegalArgumentException> { resolver.recordPurchase(10, homes, "home-1") }
        assertFailsWith<IllegalArgumentException> { resolver.recordPurchase(1, before, "home-1") }
        val upgraded = resolver.recordPurchase(50, before, "cooldown-5")
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
        assertFailsWith<IllegalArgumentException> { RewardOwnership(prestigeCount = 4) }
        assertFailsWith<IllegalArgumentException> { RewardOwnership(initialHomeCapacity = 0) }
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
        assertEquals(2, result.homeCapacity)
        val mutableRewards = catalog.level(5).rewards.toMutableList()
        val levels = (1..100).map { if (it == 5) catalog.level(it).copy(rewards = mutableRewards) else catalog.level(it) }.toMutableList()
        val snapshot = RewardCatalog(levels)
        mutableRewards.clear()
        levels.clear()
        assertEquals("bank-1", snapshot.level(5).rewards.single().id)
    }

    @Test
    fun `overflow in preserved home capacity fails rather than deleting entitlement`() {
        assertFailsWith<ArithmeticException> {
            resolver.resolve(100, RewardOwnership(permanent = setOf("home-1"), initialHomeCapacity = Int.MAX_VALUE))
        }
    }
}
