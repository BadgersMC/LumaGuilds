package net.lumalyte.lg.utils

import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * Resolves weekly quest artwork by gameplay action.
 *
 * Action-specific Season 2 artwork is preferred. If an action icon is missing,
 * the existing tier icon remains the compatibility fallback, followed by a
 * vanilla material so the menu and toast can never render without an icon.
 */
object QuestIconProvider {
    fun itemFor(quest: QuestDefinition): ItemStack =
        NexoItemProvider.getItemStackOrFallback(actionIconId(quest.action)) {
            NexoItemProvider.getItemStackOrFallback(
                "lg_quest_${quest.tier.name.lowercase()}"
            ) {
                ItemStack.of(vanillaFallback(quest.action))
            }
        }

    fun actionIconId(action: QuestAction): String = when (action) {
        QuestAction.KILL_PLAYERS,
        QuestAction.KILL_MOBS -> "lg_combat"
        QuestAction.HARVEST_CROPS -> "lg_farming"
        QuestAction.MINE_BLOCKS -> "lg_mining"
        QuestAction.PLACE_BLOCKS -> "lg_block_place"
        QuestAction.CRAFT_ITEMS -> "lg_crafting"
        QuestAction.SMELT_ITEMS -> "lg_smelting"
        QuestAction.FISH -> "lg_fishing"
        QuestAction.ENCHANT_ITEMS -> "lg_enchanting"
        QuestAction.DEPOSIT_BANK -> "lg_deposit"
        QuestAction.WIN_WARS -> "lg_war_victory"
    }

    fun vanillaFallback(action: QuestAction): Material = when (action) {
        QuestAction.KILL_PLAYERS,
        QuestAction.KILL_MOBS -> Material.IRON_SWORD
        QuestAction.HARVEST_CROPS -> Material.IRON_HOE
        QuestAction.MINE_BLOCKS -> Material.IRON_PICKAXE
        QuestAction.PLACE_BLOCKS -> Material.BRICKS
        QuestAction.CRAFT_ITEMS -> Material.CRAFTING_TABLE
        QuestAction.SMELT_ITEMS -> Material.FURNACE
        QuestAction.FISH -> Material.FISHING_ROD
        QuestAction.ENCHANT_ITEMS -> Material.ENCHANTING_TABLE
        QuestAction.DEPOSIT_BANK -> Material.GOLD_INGOT
        QuestAction.WIN_WARS -> Material.NETHER_STAR
    }
}
