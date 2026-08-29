package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.domain.values.ExperienceSource
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.EntityType

/** Pure event classification; persistence and awarding remain outside this class. */
class ProgressionActivityClassifier(
    materialPools: Map<String, Set<String>>,
    entityPools: Map<String, Set<String>>
) {
    private val materials = materialPools.mapValues { (_, values) -> values.map(String::uppercase).toSet() }
    private val entities = entityPools.mapValues { (_, values) -> values.map(String::uppercase).toSet() }

    fun isEligible(gameMode: GameMode): Boolean = gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR

    fun sourceForBreak(material: Material, playerPlaced: Boolean, matureCrop: Boolean): ExperienceSource? {
        if (playerPlaced) return null
        ORE_SOURCES[material.name]?.let { return it }
        if (material.name in CROP_MATERIALS) return ExperienceSource.CROP_BREAK.takeIf { matureCrop }
        return ExperienceSource.BLOCK_BREAK.takeIf { material.name in materials["common_break"].orEmpty() }
    }

    fun sourceForPlace(material: Material): ExperienceSource? =
        ExperienceSource.BLOCK_PLACE.takeIf { material.name in materials["common_place"].orEmpty() }

    fun sourceForKill(type: EntityType): ExperienceSource? = when (type) {
        EntityType.ENDER_DRAGON -> ExperienceSource.ENDER_DRAGON_KILL
        EntityType.WITHER -> ExperienceSource.WITHER_KILL
        EntityType.ELDER_GUARDIAN -> ExperienceSource.ELDER_GUARDIAN_KILL
        EntityType.WARDEN -> ExperienceSource.WARDEN_KILL
        else -> ExperienceSource.MOB_KILL.takeIf { type.name in entities["normal_mobs"].orEmpty() }
    }

    fun sourceForCraft(material: Material): ExperienceSource? = when (material.name) {
        in materials["common_craft"].orEmpty() -> ExperienceSource.CRAFT_COMMON
        in materials["utility_craft"].orEmpty() -> ExperienceSource.CRAFT_UTILITY
        in materials["equipment_craft"].orEmpty() -> ExperienceSource.CRAFT_EQUIPMENT
        in materials["rare_craft"].orEmpty() -> ExperienceSource.CRAFT_RARE
        else -> null
    }

    fun isBrewedPotion(material: Material): Boolean = material == Material.POTION ||
        material == Material.SPLASH_POTION || material == Material.LINGERING_POTION

    fun isExplorationMilestone(namespace: String, key: String): Boolean =
        namespace == "minecraft" && key.startsWith("adventure/")

    companion object {
        private val CROP_MATERIALS = setOf(
            "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART",
            "COCOA", "SWEET_BERRY_BUSH", "PITCHER_CROP", "TORCHFLOWER_CROP"
        )

        private val ORE_SOURCES = mapOf(
            "COAL_ORE" to ExperienceSource.COAL_ORE,
            "DEEPSLATE_COAL_ORE" to ExperienceSource.COAL_ORE,
            "COPPER_ORE" to ExperienceSource.COPPER_ORE,
            "DEEPSLATE_COPPER_ORE" to ExperienceSource.COPPER_ORE,
            "IRON_ORE" to ExperienceSource.IRON_ORE,
            "DEEPSLATE_IRON_ORE" to ExperienceSource.IRON_ORE,
            "LAPIS_ORE" to ExperienceSource.LAPIS_ORE,
            "DEEPSLATE_LAPIS_ORE" to ExperienceSource.LAPIS_ORE,
            "REDSTONE_ORE" to ExperienceSource.REDSTONE_ORE,
            "DEEPSLATE_REDSTONE_ORE" to ExperienceSource.REDSTONE_ORE,
            "GOLD_ORE" to ExperienceSource.GOLD_ORE,
            "DEEPSLATE_GOLD_ORE" to ExperienceSource.GOLD_ORE,
            "NETHER_GOLD_ORE" to ExperienceSource.GOLD_ORE,
            "NETHER_QUARTZ_ORE" to ExperienceSource.NETHER_QUARTZ_ORE,
            "DIAMOND_ORE" to ExperienceSource.DIAMOND_ORE,
            "DEEPSLATE_DIAMOND_ORE" to ExperienceSource.DIAMOND_ORE,
            "EMERALD_ORE" to ExperienceSource.EMERALD_ORE,
            "DEEPSLATE_EMERALD_ORE" to ExperienceSource.EMERALD_ORE,
            "ANCIENT_DEBRIS" to ExperienceSource.ANCIENT_DEBRIS
        )
    }
}
