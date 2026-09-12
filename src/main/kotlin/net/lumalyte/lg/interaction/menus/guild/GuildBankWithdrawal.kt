package net.lumalyte.lg.interaction.menus.guild

/** Withdrawal decisions shared by the menu's quick and custom amount actions. */
internal class GuildBankWithdrawal(
    private val withdrawalFee: (Int) -> Int = { 0 },
    private val maximumAmount: () -> Int = { Int.MAX_VALUE },
    private val currentBalance: () -> Long,
) {
    fun resolveAmount(requested: Int): Int {
        if (requested != -1) return requested
        val balance = currentBalance().coerceAtLeast(0L)
        var low = 0
        var high = minOf(balance, maximumAmount().coerceAtLeast(0).toLong()).toInt()
        // Configured proportional/capped fees are monotonic. Find the largest affordable payout.
        while (low < high) {
            val candidate = low + ((high.toLong() - low + 1) / 2).toInt()
            val fee = withdrawalFee(candidate)
            if (fee < 0) return 0
            if (candidate.toLong() + fee <= balance) low = candidate else high = candidate - 1
        }
        return low
    }

    fun execute(amount: Int, onEmpty: () -> Unit, transfer: (Int) -> Boolean): Boolean {
        if (amount <= 0) {
            onEmpty()
            return false
        }
        return transfer(amount)
    }
}
