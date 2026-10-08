package net.lumalyte.lg.domain.values

/** A completed outside-customer SELL trade earns one award, irrespective of its size. */
data class GuildShopXpPolicy(
    val enabled: Boolean = true,
    val xpPerSale: Int = 5,
    val guildDailyCap: Int = 500,
    val buyerDailyCap: Int = 50,
    val pairCooldownSeconds: Long = 300,
) {
    init {
        require(xpPerSale >= 0 && guildDailyCap >= 0 && buyerDailyCap >= 0)
        require(pairCooldownSeconds in 0..(Long.MAX_VALUE / 1000))
    }
}
