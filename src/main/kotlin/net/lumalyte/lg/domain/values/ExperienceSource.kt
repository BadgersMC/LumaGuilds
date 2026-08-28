package net.lumalyte.lg.domain.values

/**
 * Sources of experience for guilds.
 */
enum class ExperienceSource(private val poolOverride: String? = null) {
    // Guild Activities
    BANK_DEPOSIT,
    MEMBER_JOINED,
    WAR_WON,
    WAR_LOST,
    QUALIFIED_RECRUIT,
    PRE_CAP_WAR_WIN,
    
    // Player Activities
    PLAYER_KILL,
    MOB_KILL,
    CROP_BREAK,
    BLOCK_BREAK,
    BLOCK_PLACE,
    CRAFTING,
    SMELTING,
    FISHING,
    ENCHANTING,
    BREWING,
    EXPLORATION_MILESTONE,

    // Natural ore tiers share one guild-wide pool.
    COAL_ORE("ORE"),
    COPPER_ORE("ORE"),
    IRON_ORE("ORE"),
    LAPIS_ORE("ORE"),
    REDSTONE_ORE("ORE"),
    GOLD_ORE("ORE"),
    NETHER_QUARTZ_ORE("ORE"),
    DIAMOND_ORE("ORE"),
    EMERALD_ORE("ORE"),
    ANCIENT_DEBRIS("ORE"),

    // Craft tiers share one guild-wide pool.
    CRAFT_COMMON("CRAFTING"),
    CRAFT_UTILITY("CRAFTING"),
    CRAFT_EQUIPMENT("CRAFTING"),
    CRAFT_RARE("CRAFTING"),

    // Weekly boss pools remain independently configurable.
    ENDER_DRAGON_KILL,
    WITHER_KILL,
    ELDER_GUARDIAN_KILL,
    WARDEN_KILL,
    
    // Claims (if enabled)
    CLAIM_CREATED,
    CLAIM_DESTROYED,
    
    // System
    WEEKLY_ACTIVITY,
    ADMIN_BONUS;

    val defaultPool: String
        get() = poolOverride ?: name
}
