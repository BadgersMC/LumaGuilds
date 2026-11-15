package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.config.PrestigePerksConfig
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.rewards.PrestigePerk
import net.lumalyte.lg.domain.rewards.PrestigePerks
import net.lumalyte.lg.interaction.listeners.VaultItemPermissions
import org.bukkit.event.inventory.InventoryAction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-242, REQ-244, REQ-245, REQ-247, REQ-248 */
class SecondVaultRulesTest {
    private val perks = PrestigePerks(PrestigePerksConfig())

    @Test
    fun `the second vault opens at Prestige VI and is listed as that tier's perk`() {
        assertFalse(perks.secondVault(5))
        assertTrue(perks.secondVault(6))
        assertFalse(perks.secondVault(null), "an unreadable tier gives no perk")
        assertEquals(listOf(PrestigePerk.SECOND_VAULT), perks.unlockedAt(6))
        assertTrue(PrestigePerk.SECOND_VAULT !in perks.unlockedAt(5))
    }

    @Test
    fun `slot 0 is an ordinary slot in the second vault`() {
        assertEquals(setOf(RankPermission.DEPOSIT_TO_VAULT),
            VaultItemPermissions.required(InventoryAction.PLACE_ALL, 0, 27, goldButton = false))
        assertEquals(emptySet(), VaultItemPermissions.required(InventoryAction.PLACE_ALL, 0, 27))
    }

    @Test
    fun `every way of putting items in needs deposit, so withdraw-only can refuse them`() {
        val size = 27
        val deposits = listOf(
            InventoryAction.PLACE_ALL to 3, InventoryAction.PLACE_SOME to 3, InventoryAction.PLACE_ONE to 3,
            InventoryAction.SWAP_WITH_CURSOR to 3, InventoryAction.HOTBAR_SWAP to 3,
            InventoryAction.MOVE_TO_OTHER_INVENTORY to size + 4,
        )
        deposits.forEach { (action, slot) ->
            assertTrue(RankPermission.DEPOSIT_TO_VAULT in VaultItemPermissions.required(action, slot, size, goldButton = false), "$action")
        }
        listOf(InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF, InventoryAction.MOVE_TO_OTHER_INVENTORY).forEach { action ->
            assertFalse(RankPermission.DEPOSIT_TO_VAULT in VaultItemPermissions.required(action, 3, size, goldButton = false), "$action")
        }
    }

    @Test
    fun `slots above capacity are kept, not lost, when the vault is smaller`() {
        val stored = mapOf(0 to "a", 8 to "b", 9 to "c", 40 to "d")
        val (shown, kept) = SecondVaultSlots.split(stored, 9)
        assertEquals(mapOf(0 to "a", 8 to "b"), shown)
        assertEquals(mapOf(9 to "c", 40 to "d"), kept)
        assertEquals(mapOf(0 to "x", 9 to "c", 40 to "d"), SecondVaultSlots.merge(mapOf(0 to "x"), kept))
    }
}
