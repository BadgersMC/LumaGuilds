package net.lumalyte.lg.infrastructure.listeners

import io.mockk.mockk
import net.lumalyte.lg.infrastructure.services.BukkitQuestTargetProvider
import org.bukkit.Material
import org.bukkit.inventory.CraftingRecipe
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.StonecuttingRecipe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

class QuestGenerationAuditTest {
    @Test
    fun `shift crafting counts every crafted result`() {
        MockBukkit.mock()
        try {
            val result = ItemStack(Material.STICK, 4)
            val matrix = arrayOf<ItemStack?>(
                ItemStack(Material.OAK_PLANKS, 10),
                ItemStack(Material.OAK_PLANKS, 6),
            )
            val destination = arrayOfNulls<ItemStack>(36)

            assertEquals(24L, shiftCraftedAmount(result, matrix, destination))
        } finally {
            MockBukkit.unmock()
        }
    }

    @Test
    fun `shift crafting stops at destination capacity`() {
        MockBukkit.mock()
        try {
            val result = ItemStack(Material.STICK, 4)
            val matrix = arrayOf<ItemStack?>(
                ItemStack(Material.OAK_PLANKS, 10),
                ItemStack(Material.OAK_PLANKS, 10),
            )
            val destination = Array<ItemStack?>(36) { ItemStack(Material.STONE, 64) }
            destination[0] = ItemStack(Material.STICK, 60)

            assertEquals(4L, shiftCraftedAmount(result, matrix, destination))
        } finally {
            MockBukkit.unmock()
        }
    }

    @Test
    fun `natural mining rejects crafted-only blocks but keeps known world-generated blocks`() {
        assertFalse(BukkitQuestTargetProvider.isNaturalMineTarget(Material.NETHERITE_BLOCK))
        assertFalse(BukkitQuestTargetProvider.isNaturalMineTarget(
            Material.BEACON,
            setOf(Material.BEACON),
        ))
        assertTrue(BukkitQuestTargetProvider.isNaturalMineTarget(
            Material.SANDSTONE,
            setOf(Material.SANDSTONE),
        ))
        assertTrue(BukkitQuestTargetProvider.isNaturalMineTarget(Material.ANCIENT_DEBRIS))
    }

    @Test
    fun `craft quest targets only admit crafting matrix recipes`() {
        val crafting = mockk<CraftingRecipe>()
        val stonecutting = mockk<StonecuttingRecipe>()

        assertTrue(BukkitQuestTargetProvider.isCraftingMatrixRecipe(crafting))
        assertFalse(BukkitQuestTargetProvider.isCraftingMatrixRecipe(stonecutting))
    }
}
