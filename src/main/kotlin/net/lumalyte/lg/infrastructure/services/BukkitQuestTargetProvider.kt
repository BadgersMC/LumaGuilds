package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.entities.BlockProvenancePolicy
import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.QuestTargetRarity
import net.lumalyte.lg.domain.services.QuestAmountPolicy
import net.lumalyte.lg.domain.services.QuestTargetProvider
import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.CookingRecipe
import org.bukkit.inventory.Recipe
import org.bukkit.plugin.Plugin

class BukkitQuestTargetProvider(private val plugin: Plugin) : QuestTargetProvider {
    override val namespace: String = "minecraft"

    override fun discoverTargets(): Collection<QuestTarget> {
        val recipes = allRecipes()
        val compressionBlocks = compressionBlocks(recipes)
        val result = mutableListOf<QuestTarget>()

        Material.entries.asSequence()
            .filter { it.isBlock && !it.isAir }
            .filterNot { it.name in TECHNICAL_BLOCKS }
            .forEach { material ->
                val ageable = runCatching { material.createBlockData() is Ageable }.getOrDefault(false)
                val rarity = blockRarity(material, compressionBlocks)

                if (ageable) {
                    result += target(
                        id = blockId(material),
                        action = QuestAction.HARVEST_CROPS,
                        rarity = rarity,
                        provenance = BlockProvenancePolicy.ANY,
                        conditions = SPATIAL_CONDITIONS
                    )
                } else {
                    result += target(
                        id = blockId(material),
                        action = QuestAction.MINE_BLOCKS,
                        rarity = rarity,
                        provenance = BlockProvenancePolicy.NATURAL_ONLY,
                        conditions = SPATIAL_CONDITIONS
                    )
                    if (material.isItem) {
                        result += target(
                            id = blockId(material),
                            action = QuestAction.PLACE_BLOCKS,
                            rarity = rarity,
                            provenance = BlockProvenancePolicy.PLAYER_PLACED,
                            conditions = SPATIAL_CONDITIONS
                        )
                    }
                }
            }

        EntityType.entries.asSequence()
            .filter { it != EntityType.PLAYER }
            .filter { it.isSpawnable }
            .filter { type ->
                type.entityClass?.let { LivingEntity::class.java.isAssignableFrom(it) && !Player::class.java.isAssignableFrom(it) } == true
            }
            .forEach { type ->
                val rarity = when (type.name) {
                    "ENDER_DRAGON", "WITHER" -> QuestTargetRarity.PRECIOUS
                    "WARDEN", "ELDER_GUARDIAN", "RAVAGER" -> QuestTargetRarity.RARE
                    else -> QuestTargetRarity.COMMON
                }
                result += target(
                    id = entityId(type),
                    action = QuestAction.KILL_MOBS,
                    rarity = rarity,
                    conditions = SPATIAL_CONDITIONS
                )
            }

        result += target(
            id = "minecraft:player/player",
            action = QuestAction.KILL_PLAYERS,
            rarity = QuestTargetRarity.UNCOMMON,
            conditions = PLAYER_CONDITIONS,
            naturalDimensions = setOf("NORMAL", "NETHER", "THE_END")
        )

        recipes.asSequence()
            .filterNot { it is CookingRecipe<*> }
            .map(Recipe::getResult)
            .filter { !it.type.isAir }
            .distinctBy { it.type.key }
            .forEach { stack ->
                result += target(
                    id = itemId(stack.type),
                    action = QuestAction.CRAFT_ITEMS,
                    rarity = itemRarity(stack.type, compressionBlocks),
                    conditions = SPATIAL_CONDITIONS
                )
            }

        recipes.filterIsInstance<CookingRecipe<*>>()
            .map { it.result }
            .filter { !it.type.isAir }
            .distinctBy { it.type.key }
            .forEach { stack ->
                result += target(
                    id = itemId(stack.type),
                    action = QuestAction.SMELT_ITEMS,
                    rarity = itemRarity(stack.type, compressionBlocks),
                    conditions = SPATIAL_CONDITIONS
                )
            }

        result += target(
            id = "minecraft:item/any",
            action = QuestAction.FISH,
            rarity = QuestTargetRarity.COMMON,
            conditions = SPATIAL_CONDITIONS
        )

        Material.entries.asSequence()
            .filter { it.isItem && (it.maxDurability > 0 || it.name == "BOOK") }
            .forEach { material ->
                result += target(
                    id = itemId(material),
                    action = QuestAction.ENCHANT_ITEMS,
                    rarity = itemRarity(material, compressionBlocks),
                    conditions = SPATIAL_CONDITIONS
                )
            }

        result += target("lumaguilds:bank/coins", QuestAction.DEPOSIT_BANK, QuestTargetRarity.COMMON)
        result += target("lumaguilds:war/win", QuestAction.WIN_WARS, QuestTargetRarity.RARE)
        return result
    }

    private fun target(
        id: String,
        action: QuestAction,
        rarity: QuestTargetRarity,
        provenance: BlockProvenancePolicy = BlockProvenancePolicy.ANY,
        conditions: Set<QuestConditionType> = emptySet(),
        naturalDimensions: Set<String> = emptySet()
    ): QuestTarget {
        val range = QuestAmountPolicy.range(action, rarity)
        return QuestTarget(
            id = id,
            allowedActions = setOf(action),
            minimumAmount = range.minimum,
            maximumAmount = range.maximum,
            naturalDimensions = naturalDimensions,
            supportedConditions = conditions,
            provenancePolicy = provenance,
            rarity = rarity
        )
    }

    private fun allRecipes(): List<Recipe> {
        val recipes = mutableListOf<Recipe>()
        val iterator = plugin.server.recipeIterator()
        while (iterator.hasNext()) recipes += iterator.next()
        return recipes
    }

    private fun compressionBlocks(recipes: List<Recipe>): Set<Material> = recipes.asSequence()
        .filterIsInstance<org.bukkit.inventory.ShapedRecipe>()
        .filter { it.result.amount == 1 && it.result.type.isBlock }
        .filter { recipe ->
            val ingredients = recipe.ingredientMap.values.filterNotNull().map { it.type }
            ingredients.size >= 4 && ingredients.distinct().size == 1
        }
        .map { it.result.type }
        .toSet()

    private fun blockRarity(material: Material, compressionBlocks: Set<Material>): QuestTargetRarity = when {
        material.name == "NETHERITE_BLOCK" -> QuestTargetRarity.PRECIOUS
        material.name == "ANCIENT_DEBRIS" -> QuestTargetRarity.PRECIOUS
        material in compressionBlocks -> QuestTargetRarity.RARE
        material.name.endsWith("_ORE") -> QuestTargetRarity.RARE
        BULK_TOKENS.any { material.name.contains(it) } -> QuestTargetRarity.BULK
        else -> QuestTargetRarity.COMMON
    }

    private fun itemRarity(material: Material, compressionBlocks: Set<Material>): QuestTargetRarity = when {
        material.name.startsWith("NETHERITE_") -> QuestTargetRarity.PRECIOUS
        material in compressionBlocks -> QuestTargetRarity.RARE
        material.maxStackSize == 1 -> QuestTargetRarity.UNCOMMON
        else -> QuestTargetRarity.COMMON
    }

    private fun blockId(material: Material) = "minecraft:block/${material.key.key}"
    private fun itemId(material: Material) = "minecraft:item/${material.key.key}"
    private fun entityId(type: EntityType) = "minecraft:entity/${type.key.key}"

    companion object {
        val SPATIAL_CONDITIONS = setOf(
            QuestConditionType.X_WITHIN,
            QuestConditionType.Z_WITHIN,
            QuestConditionType.ABOVE_Y,
            QuestConditionType.BELOW_Y
        )

        val PLAYER_CONDITIONS = SPATIAL_CONDITIONS + setOf(
            QuestConditionType.IN_DIMENSION,
            QuestConditionType.WITHOUT_ELYTRA
        )
        private val BULK_TOKENS = setOf(
            "STONE", "DEEPSLATE", "DIRT", "SAND", "GRAVEL", "NETHERRACK",
            "END_STONE", "_LOG", "_WOOD", "_PLANKS"
        )

        private val TECHNICAL_BLOCKS = setOf(
            "AIR", "CAVE_AIR", "VOID_AIR", "BEDROCK", "END_PORTAL", "END_PORTAL_FRAME",
            "NETHER_PORTAL", "BARRIER", "LIGHT", "STRUCTURE_BLOCK", "STRUCTURE_VOID",
            "JIGSAW", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK",
            "MOVING_PISTON", "PISTON_HEAD", "FIRE", "SOUL_FIRE", "WATER", "LAVA",
            "SPAWNER", "TRIAL_SPAWNER", "VAULT", "REINFORCED_DEEPSLATE", "BUDDING_AMETHYST"
        )
    }
}

