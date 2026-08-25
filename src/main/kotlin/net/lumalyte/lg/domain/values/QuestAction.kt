package net.lumalyte.lg.domain.values

/** Gameplay operations understood by the generic weekly quest engine. */
enum class QuestAction(val experienceSource: ExperienceSource) {
    KILL_PLAYERS(ExperienceSource.PLAYER_KILL),
    KILL_MOBS(ExperienceSource.MOB_KILL),
    HARVEST_CROPS(ExperienceSource.CROP_BREAK),
    MINE_BLOCKS(ExperienceSource.BLOCK_BREAK),
    PLACE_BLOCKS(ExperienceSource.BLOCK_PLACE),
    CRAFT_ITEMS(ExperienceSource.CRAFTING),
    SMELT_ITEMS(ExperienceSource.SMELTING),
    FISH(ExperienceSource.FISHING),
    ENCHANT_ITEMS(ExperienceSource.ENCHANTING),
    DEPOSIT_BANK(ExperienceSource.BANK_DEPOSIT),
    WIN_WARS(ExperienceSource.WAR_WON)
}
