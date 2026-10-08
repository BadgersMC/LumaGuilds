package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.domain.entities.RankPermission
import org.bukkit.event.inventory.InventoryAction

/** Maps inventory packet actions to the existing vault item permissions. */
internal object VaultItemPermissions {
    private val deposit = setOf(RankPermission.DEPOSIT_TO_VAULT)
    private val withdraw = setOf(RankPermission.WITHDRAW_FROM_VAULT)
    private val swaps = deposit + withdraw
    private val vaultActions =
        buildMap {
            listOf(InventoryAction.PLACE_ALL, InventoryAction.PLACE_SOME, InventoryAction.PLACE_ONE)
                .forEach { put(it, deposit) }
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
            ).forEach { put(it, withdraw) }
            listOf(InventoryAction.SWAP_WITH_CURSOR, InventoryAction.HOTBAR_SWAP, InventoryAction.HOTBAR_MOVE_AND_READD)
                .forEach { put(it, swaps) }
        }
    private val playerActions =
        mapOf(
            InventoryAction.MOVE_TO_OTHER_INVENTORY to deposit,
            InventoryAction.COLLECT_TO_CURSOR to withdraw,
        )

    fun required(
        action: InventoryAction,
        rawSlot: Int,
        vaultSize: Int,
    ): Set<RankPermission> =
        when {
            rawSlot == 0 -> emptySet()

            // Gold button checks its own requested operation.
            rawSlot in 1 until vaultSize -> vaultActions[action].orEmpty()

            else -> playerActions[action].orEmpty()
        }
}
