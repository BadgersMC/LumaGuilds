package net.lumalyte.lg.interaction.listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.application.services.VaultInventoryManager
import net.lumalyte.lg.application.utilities.GoldBalanceButton
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
    private val transactionLogger: VaultTransactionLogger
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
     * CRITICAL: This must handle multi-viewer synchronization correctly.
     * We schedule a task to run AFTER the Bukkit click processing completes,
     * then update the cache and broadcast to all viewers.
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
            // CRITICAL: Read the actual item from the GUI inventory AFTER the click completed
            val resultingItem = holder.inventory.getItem(slot)

            // Update cache (source of truth) and broadcast to ALL other viewers
            // This ensures every viewer sees the same state
            vaultInventoryManager.updateSlotWithBroadcast(guildId, slot, resultingItem, player.uniqueId)

            // Log transaction for valuable items
            if (resultingItem != null && isValuableItem(resultingItem)) {
                transactionLogger.logItemTransaction(
                    guildId,
                    player.uniqueId,
                    VaultTransactionType.ITEM_ADD,
                    resultingItem,
                    slot
                )
                // Immediate flush for valuable items
                vaultInventoryManager.forceFlush(guildId)
            } else if (resultingItem == null) {
                // Item was removed, check if we should log removal
                val previousItem = vaultInventoryManager.getSlot(guildId, slot)
                if (previousItem != null && isValuableItem(previousItem)) {
                    transactionLogger.logItemTransaction(
                        guildId,
                        player.uniqueId,
                        VaultTransactionType.ITEM_REMOVE,
                        previousItem,
                        slot
                    )
                    vaultInventoryManager.forceFlush(guildId)
                }
            }
        })
    }

    /**
     * Checks if an item is valuable and should be immediately saved.
     */
    private fun isValuableItem(item: org.bukkit.inventory.ItemStack): Boolean {
        val material = item.type

        // Check for valuable materials
        val valuableMaterials = setOf(
            org.bukkit.Material.NETHERITE_INGOT,
            org.bukkit.Material.NETHERITE_BLOCK,
            org.bukkit.Material.NETHERITE_SWORD,
            org.bukkit.Material.NETHERITE_PICKAXE,
            org.bukkit.Material.NETHERITE_AXE,
            org.bukkit.Material.NETHERITE_SHOVEL,
            org.bukkit.Material.NETHERITE_HOE,
            org.bukkit.Material.NETHERITE_HELMET,
            org.bukkit.Material.NETHERITE_CHESTPLATE,
            org.bukkit.Material.NETHERITE_LEGGINGS,
            org.bukkit.Material.NETHERITE_BOOTS,
            org.bukkit.Material.DIAMOND,
            org.bukkit.Material.DIAMOND_BLOCK,
            org.bukkit.Material.ELYTRA,
            org.bukkit.Material.ENCHANTED_GOLDEN_APPLE,
            org.bukkit.Material.NETHER_STAR,
            org.bukkit.Material.BEACON,
            org.bukkit.Material.DRAGON_HEAD,
            org.bukkit.Material.DRAGON_EGG,
            org.bukkit.Material.TRIDENT
        )

        if (material in valuableMaterials) {
            return true
        }

        // Check for enchantments
        if (item.hasItemMeta() && item.itemMeta.hasEnchants()) {
            return true
        }

        return false
    }

    /**
     * Updates the gold button for all viewers of a vault.
     */
    private fun updateGoldButtonForAllViewers(guildId: java.util.UUID) {
        val viewers = vaultInventoryManager.getViewersForVault(guildId)
        val newBalance = vaultInventoryManager.getGoldBalance(guildId)
        val goldButton = GoldBalanceButton.createItem(newBalance)

        viewers.forEach { session ->
            try {
                session.inventory.setItem(0, goldButton)
            } catch (e: Exception) {
            // Event listener - catching all exceptions to prevent listener failure
                // Inventory no longer valid, unregister viewer
                vaultInventoryManager.unregisterViewer(session.playerId)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.inventory.holder

        if (holder !is VaultInventoryHolder) {
            return
        }

        val player = event.whoClicked as? Player ?: return
        val guildId = holder.guildId

        // Prevent dragging items into slot 0
        if (event.rawSlots.contains(0)) {
            event.isCancelled = true
            player.sendMessage(Component.text("Cannot place items in slot 0", NamedTextColor.RED))
            return
        }

        // CRITICAL: Update cache for ALL dragged slots
        // Schedule after drag completes to read final GUI state
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
            // Update cache for each affected slot in the vault inventory
            for (rawSlot in event.rawSlots) {
                // Only process slots in the vault inventory (not player inventory)
                if (rawSlot >= 0 && rawSlot < holder.getCapacity()) {
                    val resultingItem = holder.inventory.getItem(rawSlot)

                    // Update cache and broadcast to other viewers
                    vaultInventoryManager.updateSlotWithBroadcast(guildId, rawSlot, resultingItem, player.uniqueId)

                    // Log transaction for valuable items
                    if (resultingItem != null && isValuableItem(resultingItem)) {
                        transactionLogger.logItemTransaction(
                            guildId,
                            player.uniqueId,
                            VaultTransactionType.ITEM_ADD,
                            resultingItem,
                            rawSlot
                        )
                    }
                }
            }

            // Flush to database after drag operation
            vaultInventoryManager.forceFlush(guildId)
        })
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

        // Sync inventory state to cache
        holder.syncToCache()

        // Unregister viewer and flush if last viewer
        vaultInventoryManager.closeVaultFor(guildId, player.uniqueId)
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
