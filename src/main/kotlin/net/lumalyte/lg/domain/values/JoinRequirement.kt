package net.lumalyte.lg.domain.values

/**
 * Represents the join requirement for a guild via LFG.
 *
 * @property amount The currency amount required to join.
 * @property isPhysicalCurrency Whether this uses physical items (true) or virtual currency (false).
 * @property currencyName Human-readable currency name (e.g., "RAW_GOLD", "Coins").
 */
data class JoinRequirement(
    val amount: Int,
    val isPhysicalCurrency: Boolean,
    val currencyName: String
) {
    init {
        require(amount >= 0) { "Join requirement amount must be non-negative." }
        require(currencyName.isNotBlank()) { "Currency name cannot be blank." }
    }
}
