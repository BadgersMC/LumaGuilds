package net.lumalyte.lg.domain.values

/**
 * Types of perks that can be unlocked.
 */
enum class PerkType {
    // Claim perks (if claims enabled)
    INCREASED_CLAIM_BLOCKS,
    INCREASED_CLAIM_COUNT,
    FASTER_CLAIM_REGEN,

    // Bank perks
    HIGHER_BANK_BALANCE,
    BANK_INTEREST,
    INCREASED_BANK_LIMIT,
    REDUCED_WITHDRAWAL_FEES,

    // Home perks
    ADDITIONAL_HOMES,
    TELEPORT_COOLDOWN_REDUCTION,
    HOME_TELEPORT_SOUND_EFFECTS,
    ALLY_HOME_ACCESS,

    // Audio/Visual perks (always unlocked)
    CUSTOM_BANNER_COLORS,
    ANIMATED_EMOJIS,
    SPECIAL_PARTICLES,
    ANNOUNCEMENT_SOUND_EFFECTS,
    WAR_DECLARATION_SOUND_EFFECTS,

    // System perks
    // (No system perks currently defined)
}
