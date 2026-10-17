package net.lumalyte.lg.domain.values

// This config DTO follows existing public MainConfig value semantics; companion APIs expose only JDK types.

/**
 * A completed outside-customer SELL trade earns one award, irrespective of its size.
 * @property enabled Whether newly prepared sales can award XP.
 * @property xpPerSale Flat award; zero disables awards.
 * @property guildDailyCap Shared UTC-day guild allowance; zero prevents awards.
 * @property buyerDailyCap UTC-day allowance shared by this buyer across the guild's shops.
 * @property pairCooldownSeconds Minimum interval between this buyer's awards to the guild.
 */
@Suppress("LibraryEntitiesShouldNotBePublic", "ForbiddenPublicDataClass")
data class GuildShopXpPolicy(
    val enabled: Boolean = false,
    val xpPerSale: Int = 5,
    val guildDailyCap: Int = 500,
    val buyerDailyCap: Int = 50,
    val pairCooldownSeconds: Long = 300,
) {
    init {
        require(xpPerSale >= 0 && guildDailyCap >= 0 && buyerDailyCap >= 0)
        require(pairCooldownSeconds in 0..Long.MAX_VALUE / MILLIS_PER_SECOND)
    }
    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
