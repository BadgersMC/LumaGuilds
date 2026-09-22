package net.lumalyte.lg.infrastructure.services

import com.nexomc.nexo.api.NexoBlocks
import com.nexomc.nexo.api.NexoItems
import net.lumalyte.lg.domain.entities.BlockProvenancePolicy
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.QuestTargetRarity
import net.lumalyte.lg.domain.services.QuestAmountPolicy
import net.lumalyte.lg.domain.services.QuestTargetProvider
import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.inventory.Recipe
import org.bukkit.plugin.Plugin

class NexoQuestTargetProvider(private val plugin: Plugin) : QuestTargetProvider {
    override val namespace: String = "nexo"

    override fun discoverTargets(): Collection<QuestTarget> {
        if (!plugin.server.pluginManager.isPluginEnabled("Nexo")) return emptyList()

        val result = mutableListOf<QuestTarget>()
        runCatching { NexoBlocks.blockIDs().sorted() }.getOrDefault(emptyList()).forEach { id ->
            val rarity = rarity(id)
            result += target(
                "nexo:block/${id.lowercase()}",
                QuestAction.MINE_BLOCKS,
                rarity,
                BlockProvenancePolicy.NATURAL_ONLY
            )
            result += target(
                "nexo:block/${id.lowercase()}",
                QuestAction.PLACE_BLOCKS,
                rarity,
                BlockProvenancePolicy.PLAYER_PLACED
            )
        }

        allRecipes().asSequence()
            .filter(BukkitQuestTargetProvider::isCraftingMatrixRecipe)
            .map(Recipe::getResult)
            .mapNotNull { resultStack ->
                runCatching { NexoItems.idFromItem(resultStack) }.getOrNull()?.takeIf(String::isNotBlank)
            }
            .distinct()
            .sorted()
            .forEach { id ->
                result += target("nexo:item/${id.lowercase()}", QuestAction.CRAFT_ITEMS, rarity(id))
            }

        return result
    }

    private fun target(
        id: String,
        action: QuestAction,
        rarity: QuestTargetRarity,
        provenance: BlockProvenancePolicy = BlockProvenancePolicy.ANY
    ): QuestTarget {
        val range = QuestAmountPolicy.range(action, rarity)
        return QuestTarget(
            id = id,
            allowedActions = setOf(action),
            minimumAmount = range.minimum,
            maximumAmount = range.maximum,
            supportedConditions = BukkitQuestTargetProvider.SPATIAL_CONDITIONS,
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

    private fun rarity(id: String): QuestTargetRarity {
        val normalized = id.uppercase()
        return when {
            "NETHERITE" in normalized -> QuestTargetRarity.PRECIOUS
            "ORE" in normalized || "GEM" in normalized || "CRYSTAL" in normalized -> QuestTargetRarity.RARE
            else -> QuestTargetRarity.COMMON
        }
    }
}
