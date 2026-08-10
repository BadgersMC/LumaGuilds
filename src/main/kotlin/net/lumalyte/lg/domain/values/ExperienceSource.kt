package net.lumalyte.lg.domain.values

/**
 * Sources of experience for guilds.
 */
enum class ExperienceSource {
    // Guild Activities
    BANK_DEPOSIT,
    MEMBER_JOINED,
    WAR_WON,
    WAR_LOST,
    
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
    
    // Claims (if enabled)
    CLAIM_CREATED,
    CLAIM_DESTROYED,
    
    // System
    WEEKLY_ACTIVITY,
    ADMIN_BONUS
}
