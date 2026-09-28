package net.lumalyte.lg.utils

import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QuestIconProviderTest {
    @Test
    fun `quest actions use Season 2 custom icons`() {
        val expected = mapOf(
            QuestAction.KILL_PLAYERS to "lg_combat",
            QuestAction.KILL_MOBS to "lg_combat",
            QuestAction.HARVEST_CROPS to "lg_farming",
            QuestAction.MINE_BLOCKS to "lg_mining",
            QuestAction.PLACE_BLOCKS to "lg_block_place",
            QuestAction.CRAFT_ITEMS to "lg_crafting",
            QuestAction.SMELT_ITEMS to "lg_smelting",
            QuestAction.FISH to "lg_fishing",
            QuestAction.ENCHANT_ITEMS to "lg_enchanting",
            QuestAction.DEPOSIT_BANK to "lg_deposit",
            QuestAction.WIN_WARS to "lg_war_victory",
        )

        expected.forEach { (action, iconId) ->
            assertEquals(iconId, QuestIconProvider.actionIconId(action))
        }
    }

    @Test
    fun `fishing keeps a vanilla fishing rod as the final fallback`() {
        assertEquals(Material.FISHING_ROD, QuestIconProvider.vanillaFallback(QuestAction.FISH))
    }
}
