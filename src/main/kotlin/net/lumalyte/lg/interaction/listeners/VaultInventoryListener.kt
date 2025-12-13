package net.lumalyte.lg.interaction.listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.application.services.VaultInventoryManager
import net.lumalyte.lg.application.utilities.GoldBalanceButton
import net.lumalyte.lg.application.utilities.ValuableItemChecker
import net.lumalyte.lg.config.VaultConfig
import net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionLogger
import net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionType
import net.lumalyte.lg.interaction.inventory.VaultInventoryHolder
import net.lumalyte.lg.interaction.menus.guild.GoldDepositMenu
import net.lumalyte.lg.interaction.menus.guild.GoldWithdrawMenu
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * Listens for inventory events on guild vault inventories.
 * Handles gold button clicks, item movements, and real-time synchronization.
 */
class VaultInventoryListener(
    private val plugin: JavaPlugin,
    private val vaultInventoryManager: VaultInventoryManager,
    private val transactionLogger: VaultTransactionLogger,
    private val vaultConfig: VaultConfig
) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder

        // Only handle vault inventories
        if (holder !is VaultInventoryHolder) {
            return
        }

        val player = event.whoClicked as? Player ?: return
        val guildId = holder.guildId
        val clickedSlot = event.rawSlot

        // Check if clicking in the vault inventory (not player inventory)
        if (clickedSlot < 0 || clickedSlot >= holder.getCapacity()) {
            return // Clicking in player inventory, allow normal behavior
        }

        // CRITICAL: Validate and repair inventory sync BEFORE processing click
        // This ensures the player is viewing the current state from cache
        vaultInventoryManager.validateAndRepairVault(guildId, holder.inventory)

        // Handle slot 0 (Gold Balance Button) clicks
        if (clickedSlot == 0) {
            event.isCancelled = true
            handleGoldButtonClick(player, guildId, holder.guildName, event.click, event.isShiftClick)
            return
        }

        // Prevent moving items into slot 0
        if (event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            val item = event.currentItem
            if (item != null && holder.inventory.getItem(0) == null) {
                // Would move to slot 0, cancel it
                event.isCancelled = true
                player.sendMessage(Component.text("Slot 0 is reserved for the Gold Balance Button", NamedTextColor.RED))
                return
            }
        }

        // Handle normal slot clicks (1-53)
        handleNormalSlotClick(event, holder, player, guildId, clickedSlot)
    }

    /**
     * Handles clicks on the Gold Balance Button (slot 0).
     */
    private fun handleGoldButtonClick(
        player: Player,
        guildId: java.util.UUID,
        guildName: String,
        clickType: ClickType,
        isShift: Boolean
    ) {
        when {
            // Shift + Left Click: Deposit all gold
            isShift && (clickType == ClickType.LEFT || clickType == ClickType.SHIFT_LEFT) -> {
                depositAllGold(player, guildId, guildName)
            }
            // Left Click: Open deposit menu
            clickType == ClickType.LEFT -> {
                val depositMenu = GoldDepositMenu(plugin, player, guildId, guildName, vaultInventoryManager, transactionLogger)
                depositMenu.open()
            }
            // Right Click: Open withdraw menu
            clickType == ClickType.RIGHT -> {
                val withdrawMenu = GoldWithdrawMenu(plugin, player, guildId, guildName, vaultInventoryManager, transactionLogger)
                withdrawMenu.open()
            }
            else -> {
                player.sendMessage(Component.text("Invalid action on Gold Balance Button", NamedTextColor.RED))
            }
        }

        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
    }

    /**
     * Deposits all gold items from the player's inventory.
     */
    private fun depositAllGold(player: Player, guildId: java.util.UUID, guildName: String) {
        var totalNuggets = 0L

        // Scan player inventory for gold items
        for (i in 0 until player.inventory.size) {
            val item = player.inventory.getItem(i) ?: continue

            val value = GoldBalanceButton.calculateGoldValue(item)
            if (value > 0) {
                totalNuggets += value
                player.inventory.setItem(i, null)
            }
        }

        if (totalNuggets > 0) {
            // Add to vault balance atomically (prevents race conditions)
            val newBalance = vaultInventoryManager.depositGold(guildId, player.uniqueId, totalNuggets)

            // Immediate flush to database
            vaultInventoryManager.forceFlush(guildId)

            // Update gold button for all viewers
            updateGoldButtonForAllViewers(guildId)

            // Feedback
            player.sendMessage(
                Component.text("✓ Deposited ", NamedTextColor.GREEN)
                    .append(Component.text("$totalNuggets currency", NamedTextColor.GOLD))
                    .append(Component.text(" into $guildName's vault", NamedTextColor.GREEN))
            )
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)
        } else {
            player.sendMessage(Component.text("You have no gold items to deposit", NamedTextColor.RED))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    /**
     * Handles clicks on normal vault slots (1-53).
     *
     * Uses syncInventoryToCache to sync the ENTIRE inventory state after any click.
     * This correctly handles ALL click types including shift-click, which moves items
     * to unpredictable slots. With shared inventory, Bukkit handles viewer sync natively.
     */
    private fun handleNormalSlotClick(
        event: InventoryClickEvent,
        holder: VaultInventoryHolder,
        player: Player,
        guildId: java.util.UUID,
        slot: Int
    ) {
        // Schedule a sync task to run AFTER the click completes (next tick)
        // This ensures we read the correct inventory state after Bukkit processes the click
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
            // Sync the ENTIRE inventory to cache - this correctly handles:
            // - Normal clicks (single slot)
            // - Shift-click (item moves to first available slot, not clicked slot)
            // - Number key swaps
            // - Any other click type
            // With shared inventory, all viewers see changes immediately (Bukkit handles this)
            vaultInventoryManager.syncInventoryToCache(guildId, holder.inventory)

            // Log transactions for valuable items by scanning what changed
            logValuableItemTransactions(guildId, player, holder.inventory)
        })
    }

    /**
     * Scans the inventory for valuable items and logs transactions.
     * Called after syncInventoryToCache to log any valuable item changes.
     */
    private fun logValuableItemTransactions(
        guildId: java.util.UUID,
        player: Player,
        inventory: org.bukkit.inventory.Inventory
    ) {
        // Check all non-gold-button slots for valuable items
        var hasValuableChanges = false
        for (slot in 1 until inventory.size) {
            val item = inventory.getItem(slot)
            if (item != null && isValuableItem(item)) {
                hasValuableChanges = true
                break
            }
        }

        // If valuable items exist, force flush to ensure persistence
        if (hasValuableChanges) {
            vaultInventoryManager.forceFlush(guildId)
        }
    }

    /**
     * Checks if an item is valuable and should be immediately saved.
     * Delegates to the shared ValuableItemChecker utility.
     */
    private fun isValuableItem(item: org.bukkit.inventory.ItemStack): Boolean {
        return ValuableItemChecker.isValuableItem(item, vaultConfig)
    }

    /**
     * Updates the gold button for all viewers of a vault.
     * With shared inventory, we only need to update slot 0 once - all viewers see the same Inventory.
     */
    private fun updateGoldButtonForAllViewers(guildId: java.util.UUID) {
        val newBalance = vaultInventoryManager.getGoldBalance(guildId)
        val goldButton = GoldBalanceButton.createItem(newBalance)

        // With shared inventory, all viewers share the same Inventory object
        // So we just need to get one viewer's inventory and update it
        val viewers = vaultInventoryManager.getViewersForVault(guildId)
        if (viewers.isNotEmpty()) {
            try {
                // All viewers share the same inventory, so updating one updates all
                viewers.first().inventory.setItem(0, goldButton)
            } catch (e: Exception) {
                // Event listener - catching all exceptions to prevent listener failure
                // Inventory no longer valid, try to clean up
                viewers.forEach { session ->
                    vaultInventoryManager.unregisterViewer(session.playerId)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.inventory.holder

        if (holder !is VaultInventoryHolder) {
            return
        }

        val player = event.whoClicked as? Player ?: return

        // SAFETY: Completely prevent drag-clicking in vaults
        // Drag operations create race conditions and can cause item deletion
        // Check if any of the dragged slots are in the vault inventory
        val affectsVault = event.rawSlots.any { slot ->
            slot >= 0 && slot < holder.getCapacity()
        }

        if (affectsVault) {
            event.isCancelled = true
            player.sendMessage(
                Component.text("⚠ Drag-clicking is disabled in vaults for safety", NamedTextColor.YELLOW)
                    .append(Component.text(" - Please move items individually", NamedTextColor.GRAY))
            )
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val holder = event.inventory.holder

        if (holder !is VaultInventoryHolder) {
            return
        }

        val player = event.player as? Player ?: return
        val guildId = holder.guildId

        // Register player as viewer
        vaultInventoryManager.openVaultFor(guildId, player, event.inventory)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder

        if (holder !is VaultInventoryHolder) {
            return
        }

        val player = event.player as? Player ?: return
        val guildId = holder.guildId
        val inventory = holder.inventory

        // Sync entire inventory state to cache
        vaultInventoryManager.syncInventoryToCache(guildId, inventory)

        // Check if this is the last viewer (before unregistering)
        val isLastViewer = inventory.viewers.size <= 1

        // Unregister viewer and flush if last viewer
        vaultInventoryManager.closeVaultFor(guildId, player.uniqueId)

        // If this was the last viewer, evict the shared inventory to free memory
        if (isLastViewer) {
            vaultInventoryManager.evictSharedInventory(guildId)
        }
    }

    /**
     * Cleanup vault viewer sessions when player quits.
     * Prevents memory leak from players disconnecting with vault open.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // Check if player has any active vault sessions and clean them up
        vaultInventoryManager.cleanupDisconnectedPlayer(player.uniqueId)
    }
}
