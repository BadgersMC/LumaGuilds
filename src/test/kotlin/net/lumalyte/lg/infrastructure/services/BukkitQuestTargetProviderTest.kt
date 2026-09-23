package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.Material
import org.bukkit.inventory.CraftingRecipe
import org.bukkit.inventory.StonecuttingRecipe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BukkitQuestTargetProviderTest {
    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `target discovery skips legacy materials before registry key access`() {
        MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()

        val targets = BukkitQuestTargetProvider(plugin).discoverTargets()

        assertTrue(targets.isNotEmpty())
        assertTrue(Material.entries.any { it.isLegacy })
    }

    @Test
    fun `natural mining excludes crafted only blocks`() {
        assertFalse(BukkitQuestTargetProvider.isNaturalMineTarget(Material.NETHERITE_BLOCK))
        assertFalse(
            BukkitQuestTargetProvider.isNaturalMineTarget(
                Material.BEACON,
                setOf(Material.BEACON),
            )
        )
        assertTrue(
            BukkitQuestTargetProvider.isNaturalMineTarget(
                Material.SANDSTONE,
                setOf(Material.SANDSTONE),
            )
        )
        assertTrue(BukkitQuestTargetProvider.isNaturalMineTarget(Material.ANCIENT_DEBRIS))
    }

    @Test
    fun `enchant targets match enchanting table component semantics`() {
        assertTrue(BukkitQuestTargetProvider.isEnchantingTableTarget(Material.DIAMOND_SWORD))
        assertTrue(BukkitQuestTargetProvider.isEnchantingTableTarget(Material.BOOK))
        assertFalse(BukkitQuestTargetProvider.isEnchantingTableTarget(Material.SHIELD))
        assertFalse(BukkitQuestTargetProvider.isEnchantingTableTarget(Material.ELYTRA))
        assertFalse(BukkitQuestTargetProvider.isEnchantingTableTarget(Material.FLINT_AND_STEEL))
    }

    @Test
    fun `craft quest targets only use crafting matrix recipes`() {
        assertTrue(BukkitQuestTargetProvider.isCraftingMatrixRecipe(mockk<CraftingRecipe>()))
        assertFalse(BukkitQuestTargetProvider.isCraftingMatrixRecipe(mockk<StonecuttingRecipe>()))
    }

    @Test
    fun `discovered targets exclude impossible quest pairs`() {
        MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()

        val targets = BukkitQuestTargetProvider(plugin).discoverTargets()

        assertFalse(targets.any {
            it.id == "minecraft:block/netherite_block" &&
                QuestAction.MINE_BLOCKS in it.allowedActions
        })
        assertFalse(targets.any {
            it.id == "minecraft:item/shield" &&
                QuestAction.ENCHANT_ITEMS in it.allowedActions
        })
    }
}
