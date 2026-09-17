package net.lumalyte.lg.domain.gold

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GuildGoldPolicyTest {
    private val policy = GuildGoldPolicy(
        minDeposit = 1,
        maxDeposit = 100_000,
        withdrawalPercent = 0.5,
        dailyWithdrawalLimit = 50_000,
        depositFeePercent = 0.01,
        withdrawalFeePercent = 0.02,
        maxDepositFee = 128,
        maxWithdrawalFee = 15,
        globalCapacity = 1_000_000,
        suspiciousThreshold = 50_000,
        autoFreezeSuspicious = false
    )

    @Test
    fun `effective capacity uses tier plus permanent benefit when below global ceiling`() {
        assertEquals(
            25_000,
            GuildGoldCalculator.effectiveCapacity(
                policy = policy,
                capacity = GuildGoldCapacity(tier = 20_000, permanent = 5_000)
            )
        )
    }

    @Test
    fun `effective capacity never crosses global ceiling`() {
        assertEquals(
            1_000_000,
            GuildGoldCalculator.effectiveCapacity(
                policy = policy,
                capacity = GuildGoldCapacity(tier = 990_000, permanent = 50_000)
            )
        )
    }

    @Test
    fun `fees round down and respect configured caps`() {
        assertEquals(128, GuildGoldCalculator.depositFee(policy, 100_000))
        assertEquals(15, GuildGoldCalculator.withdrawalFee(policy, 10_000))
        assertEquals(1, GuildGoldCalculator.depositFee(policy, 199))
    }

    @Test
    fun `maximum withdrawal uses the smallest policy allowance`() {
        assertEquals(
            5_000,
            GuildGoldCalculator.maximumWithdrawal(
                policy = policy,
                balance = 80_000,
                withdrawnToday = 45_000
            )
        )
    }

    @Test
    fun `capacity addition saturates before applying global ceiling`() {
        assertEquals(
            1_000_000,
            GuildGoldCalculator.effectiveCapacity(
                policy = policy,
                capacity = GuildGoldCapacity(tier = Long.MAX_VALUE, permanent = 1)
            )
        )
    }

    @Test
    fun `policy rejects percentages outside zero through one`() {
        assertThrows(IllegalArgumentException::class.java) {
            policy.copy(withdrawalPercent = 1.01)
        }
    }
}
