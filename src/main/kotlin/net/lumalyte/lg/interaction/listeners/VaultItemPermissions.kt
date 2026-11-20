package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.domain.entities.RankPermission
import org.bukkit.event.inventory.InventoryAction

/** Maps inventory packet actions to the existing vault item permissions. */
internal object VaultItemPermissions {
    private val DEPOSIT = setOf(RankPermission.DEPOSIT_TO_VAULT)
    private val WITHDRAW = setOf(RankPermission.WITHDRAW_FROM_VAULT)
    private val SWAPS = DEPOSIT + WITHDRAW
    private val VAULT_ACTIONS =
        buildMap {
            listOf(InventoryAction.PLACE_ALL, InventoryAction.PLACE_SOME, InventoryAction.PLACE_ONE)
                .forEach { put(it, DEPOSIT) }
            listOf(
                InventoryAction.PICKUP_ALL,
                InventoryAction.PICKUP_SOME,
                InventoryAction.PICKUP_HALF,
                InventoryAction.PICKUP_ONE,
                InventoryAction.DROP_ALL_SLOT,
                InventoryAction.DROP_ONE_SLOT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY,
                InventoryAction.COLLECT_TO_CURSOR,
                InventoryAction.CLONE_STACK,
            ).forEach { put(it, WITHDRAW) }
            listOf(InventoryAction.SWAP_WITH_CURSOR, InventoryAction.HOTBAR_SWAP, InventoryAction.HOTBAR_MOVE_AND_READD)
                .forEach { put(it, SWAPS) }
        }
    private val PLAYER_ACTIONS =
        mapOf(
            InventoryAction.MOVE_TO_OTHER_INVENTORY to DEPOSIT,
            InventoryAction.COLLECT_TO_CURSOR to WITHDRAW,
        )

    /** [goldButton] is false for the second vault (REQ-244), where slot 0 is an ordinary slot. */
    fun required(action: InventoryAction, rawSlot: Int, vaultSize: Int, goldButton: Boolean = true): Set<RankPermission> {
        return when {
            // Gold button checks its own requested operation.
            goldButton && rawSlot == 0 -> emptySet()
            rawSlot in 0 until vaultSize -> VAULT_ACTIONS[action].orEmpty()
            else -> PLAYER_ACTIONS[action].orEmpty()
        }
    }
}
