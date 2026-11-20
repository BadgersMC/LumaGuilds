package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.entities.QuestTargetRarity

/**
 * REQ-176: which discovered blocks and mobs may become weekly quest targets.
 * Allowlists by name, so a new Minecraft version never adds an odd target
 * (a new pet, a technical entity, a decorative block variant) by itself.
 */
internal object QuestTargetPool {
    private val COMMON_MOBS = setOf(
        // Overworld hostiles
        "ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "SKELETON", "STRAY", "BOGGED", "SPIDER", "CAVE_SPIDER",
        "CREEPER", "ENDERMAN", "SLIME", "WITCH", "PILLAGER", "VINDICATOR", "SILVERFISH", "GUARDIAN",
        // Nether and End
        "BLAZE", "GHAST", "MAGMA_CUBE", "ZOMBIFIED_PIGLIN", "PIGLIN", "HOGLIN", "WITHER_SKELETON", "SHULKER",
        // Farm animals
        "COW", "PIG", "SHEEP", "CHICKEN", "RABBIT", "SQUID",
    )
    private val RARE_MOBS = setOf(
        "PHANTOM", "BREEZE", "CREAKING", "EVOKER", "PIGLIN_BRUTE", "WARDEN", "ELDER_GUARDIAN", "RAVAGER",
    )
    private val PRECIOUS_MOBS = setOf("ENDER_DRAGON", "WITHER")

    /** Rarity for a kill-quest mob, or null when the mob must never be a target. */
    fun mobRarity(entity: String): QuestTargetRarity? = when (entity) {
        in PRECIOUS_MOBS -> QuestTargetRarity.PRECIOUS
        in RARE_MOBS -> QuestTargetRarity.RARE
        in COMMON_MOBS -> QuestTargetRarity.COMMON
        else -> null
    }

    /** Age-based crops count only when fully grown; the rest are growth-timer plants (see GrownPlantHarvest). */
    private val HARVEST_TARGETS = setOf(
        "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA", "SWEET_BERRY_BUSH",
        "TORCHFLOWER_CROP", "PITCHER_CROP", "SUGAR_CANE", "CACTUS", "KELP", "BAMBOO",
    )

    fun isHarvestTarget(block: String): Boolean = block in HARVEST_TARGETS

    private val PLACE_EXACT = setOf(
        "STONE", "COBBLESTONE", "MOSSY_COBBLESTONE", "SMOOTH_STONE", "BRICKS", "GLASS", "TERRACOTTA",
        "DIRT", "SAND", "RED_SAND", "GRAVEL", "SANDSTONE", "SMOOTH_SANDSTONE", "RED_SANDSTONE",
        "SMOOTH_RED_SANDSTONE", "ANDESITE", "DIORITE", "GRANITE", "POLISHED_ANDESITE", "POLISHED_DIORITE",
        "POLISHED_GRANITE", "DEEPSLATE", "COBBLED_DEEPSLATE", "POLISHED_DEEPSLATE", "TUFF", "POLISHED_TUFF",
        "CALCITE", "BLACKSTONE", "POLISHED_BLACKSTONE", "QUARTZ_BLOCK", "SMOOTH_QUARTZ", "PRISMARINE",
        "NETHERRACK", "END_STONE", "MUD", "PACKED_MUD", "CLAY", "SNOW_BLOCK", "HAY_BLOCK", "BASALT",
        "SMOOTH_BASALT", "POLISHED_BASALT", "BOOKSHELF",
    )
    private val PLACE_SUFFIXES = listOf("_PLANKS", "_LOG", "_WOOD", "_STEM", "_HYPHAE", "_BRICKS", "_CONCRETE",
        "_WOOL", "_TERRACOTTA", "_STAINED_GLASS")
    private val PLACE_EXCLUDED = listOf("_STAIRS", "_SLAB", "_WALL", "_FENCE", "_PANE", "_DOOR", "_BUTTON",
        "_PRESSURE_PLATE", "_SIGN", "_CARPET", "WAXED", "EXPOSED", "WEATHERED", "OXIDIZED", "INFESTED",
        "CRACKED", "CHISELED", "POWDER", "MELON_STEM", "PUMPKIN_STEM")

    /** Full building blocks players place in bulk; never stairs, slabs, panes or other decorative variants. */
    fun isPlaceTarget(block: String): Boolean =
        PLACE_EXCLUDED.none { it in block } &&
            (block in PLACE_EXACT || PLACE_SUFFIXES.any { block.endsWith(it) })

    private val MINE_EXACT = setOf(
        "STONE", "DEEPSLATE", "ANDESITE", "DIORITE", "GRANITE", "TUFF", "CALCITE", "DIRT",
        "COARSE_DIRT", "GRASS_BLOCK", "PODZOL", "MYCELIUM", "MUD", "CLAY", "SAND", "RED_SAND", "GRAVEL",
        "SANDSTONE", "RED_SANDSTONE", "TERRACOTTA", "ICE", "PACKED_ICE", "BLUE_ICE", "SNOW_BLOCK", "OBSIDIAN",
        "NETHERRACK", "BLACKSTONE", "BASALT", "SOUL_SAND", "SOUL_SOIL", "MAGMA_BLOCK", "GLOWSTONE",
        "NETHER_WART_BLOCK", "WARPED_WART_BLOCK", "SHROOMLIGHT", "END_STONE", "ANCIENT_DEBRIS",
        "AMETHYST_BLOCK", "DRIPSTONE_BLOCK", "MOSS_BLOCK", "SCULK", "MELON", "PUMPKIN", "PRISMARINE",
    )

    /** Blocks that generate in the world; never wall-mounted, potted or placed-only variants. */
    fun isMineTarget(block: String): Boolean =
        "WALL" !in block && "POTTED" !in block && !block.startsWith("STRIPPED_") &&
            (block in MINE_EXACT || block.endsWith("_ORE") ||
                (block.endsWith("_LOG") || block.endsWith("_STEM")) && block !in setOf("MELON_STEM", "PUMPKIN_STEM"))

    private val SMELT_TARGETS = setOf(
        "IRON_INGOT", "GOLD_INGOT", "COPPER_INGOT", "IRON_NUGGET", "GOLD_NUGGET", "NETHERITE_SCRAP", "GLASS",
        "STONE", "SMOOTH_STONE", "DEEPSLATE", "BRICK", "NETHER_BRICK", "TERRACOTTA", "CHARCOAL", "DRIED_KELP",
        "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN", "COOKED_MUTTON", "COOKED_RABBIT", "COOKED_COD",
        "COOKED_SALMON", "BAKED_POTATO", "SMOOTH_SANDSTONE", "SMOOTH_QUARTZ", "CRACKED_STONE_BRICKS",
    )

    /** Everyday furnace goods; not glazed terracotta, dyes or other one-off smelts. */
    fun isSmeltTarget(item: String): Boolean = item in SMELT_TARGETS

    private val CRAFT_EXCLUDED = listOf("WAXED", "EXPOSED", "WEATHERED", "OXIDIZED")

    /** Any crafting-table item except copper's waxed and weathered variants. */
    fun isCraftTarget(item: String): Boolean = CRAFT_EXCLUDED.none { it in item }
}
