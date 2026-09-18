package net.lumalyte.lg.domain.rewards

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class RewardCatalogTest {
    private val catalog = RewardCatalog.chapterTwo()

    @Test
    fun `executable catalog agrees with every row in the operator approved document`() {
        val rows = Files.readAllLines(Path.of("docs/plans/2026-09-17-lg-1202-reward-catalog-proposal.md"))
            .filter { Regex("^\\| \\d+ \\|").containsMatchIn(it) }
        assertEquals(100, rows.size)
        rows.forEach { row ->
            val cells = row.split('|').map(String::trim)
            val tier = catalog.level(cells[1].toInt())
            assertEquals(cells[3].replace(",", "").toLong(), tier.automaticBankCapacity)
            val expected = listOf(cells[4], cells[5]).filter { it != "—" }
            assertEquals(expected.size, tier.rewards.size)
            expected.zip(tier.rewards).forEach { (cell, reward) ->
                assertEquals(cell.substringBefore(':'), reward.id)
                assertEquals(cell.substringAfterLast("; ").toLong(), reward.price)
                val effect = cell.substringAfter(": ").substringBefore(';')
                when (val actual = reward.effect) {
                    is RewardEffect.BankCapacity -> {
                        assertEquals("+1,000 capacity", effect)
                        assertEquals(1_000L, actual.amount)
                    }
                    is RewardEffect.HomeCapacity -> {
                        assertEquals("+1 slot", effect)
                        assertEquals(1, actual.amount)
                    }
                    is RewardEffect.HomeCooldown -> assertEquals(effect.removeSuffix("x").toDouble(), actual.multiplier)
                    is RewardEffect.WithdrawalFee -> assertEquals(effect.removeSuffix("x").toDouble(), actual.multiplier)
                    RewardEffect.AllyHomes -> assertEquals("reciprocal access", effect)
                }
            }
        }
        assertEquals(48_500L, catalog.rewards.sumOf { it.price })
        assertEquals(30, catalog.rewards.map { it.id }.toSet().size)
        assertEquals(9, catalog.rewards.count { it.effect is RewardEffect.HomeCapacity })
        assertEquals(10_000L, catalog.rewards.mapNotNull { (it.effect as? RewardEffect.BankCapacity)?.amount }.sum())
    }

    @Test
    fun `checkpoint purchases are positive affordable and have the approved persistence`() {
        (1..100).forEach { level ->
            val tier = catalog.level(level)
            assertEquals(if (level % 10 == 0) 2 else if (level % 5 == 0) 1 else 0, tier.rewards.size)
            tier.rewards.forEach { reward ->
                assertTrue(reward.price > 0 && reward.price <= tier.automaticBankCapacity)
                assertEquals(level, reward.level)
                assertEquals(reward.effect is RewardEffect.HomeCapacity, reward.permanentOnPurchase)
                assertEquals(!reward.permanentOnPurchase, reward.prestigeEligible)
            }
        }
        assertEquals(8_000L, catalog.level(1).automaticBankCapacity)
        assertEquals(47_500L, catalog.level(100).automaticBankCapacity)
        assertFailsWith<IllegalArgumentException> { catalog.level(0) }
        assertFailsWith<IllegalArgumentException> { catalog.level(101) }
        assertNull(catalog.find("unknown"))
    }

    @Test
    fun `invalid catalog data fails closed instead of granting malformed effects`() {
        val levels = (1..100).map(catalog::level)
        assertFailsWith<IllegalArgumentException> { RewardCatalog(levels.dropLast(1)) }
        assertFailsWith<IllegalArgumentException> { RewardCatalog(levels + levels.last()) }
        val level5 = levels[4]
        val numeric = level5.rewards.single()
        assertFailsWith<IllegalArgumentException> { numeric.copy(price = 0) }
        assertFailsWith<IllegalArgumentException> { numeric.copy(id = "") }
        assertFailsWith<IllegalArgumentException> { numeric.copy(id = "a".repeat(65)) }
        assertFailsWith<IllegalArgumentException> { RewardEffect.BankCapacity(0) }
        assertFailsWith<IllegalArgumentException> { RewardEffect.HomeCapacity(0) }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, 1.1).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { RewardEffect.HomeCooldown(invalid) }
            assertFailsWith<IllegalArgumentException> { RewardEffect.WithdrawalFee(invalid) }
        }
        assertFailsWith<IllegalArgumentException> {
            numeric.copy(effect = RewardEffect.HomeCapacity(1), prestigeEligible = true)
        }
        assertFailsWith<IllegalArgumentException> {
            RewardCatalog(levels.map { if (it.level == 15) it.copy(rewards = listOf(numeric.copy(level = 15))) else it })
        }
        assertFailsWith<IllegalArgumentException> {
            RewardCatalog(levels.map { if (it.level == 5) it.copy(rewards = emptyList()) else it })
        }
        assertFailsWith<IllegalArgumentException> {
            RewardCatalog(levels.map { if (it.level == 6) it.copy(automaticBankCapacity = 1) else it })
        }
    }
}
