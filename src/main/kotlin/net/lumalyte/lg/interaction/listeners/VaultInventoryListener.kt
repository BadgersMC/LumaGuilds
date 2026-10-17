package net.lumalyte.lg.interaction.listeners

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.PhysicalGoldRequest
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.badgersmc.nexus.i18n.LangService

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.application.utilities.GoldBalanceButton
import net.lumalyte.lg.application.utilities.ValuableItemChecker
import net.lumalyte.lg.config.VaultConfig
import net.lumalyte.lg.domain.entities.RankPermission
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
import org.slf4j.LoggerFactory

/**
 * Listens for inventory events on guild vault inventories.
 * Handles gold button clicks, item movements, and real-time synchronization.
 */
class VaultInventoryListener(
    private val plugin: JavaPlugin,
    private val vaultInventoryManager: VaultInventoryManager,
    private val transactionLogger: VaultTransactionLogger,
    private val vaultConfig: VaultConfig,
    private val guildRepository: net.lumalyte.lg.application.persistence.GuildRepository,
    private val memberService: MemberService,
    private val platformDetectionService: PlatformDetectionService
) : Listener, KoinComponent {

    private val bankService: BankService by inject()
    private val lang: LangService by inject()

    private val logger = LoggerFactory.getLogger(VaultInventoryListener::class.java)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? VaultInventoryHolder ?: return
        val player = event.whoClicked as? Player ?: return
        if (event.isCancelled || !authorizeClick(event, holder, player)) return
        handleVaultClick(event, holder, player)
    }

    private fun authorizeClick(event: InventoryClickEvent, holder: VaultInventoryHolder, player: Player): Boolean {
        if (!hasCurrentAccess(player, holder)) {
            event.isCancelled = true
            return false
        }
        val required = VaultItemPermissions.required(event.action, event.rawSlot, event.inventory.size)
        val allowed = event.action != InventoryAction.UNKNOWN &&
            required.all { memberService.hasPermission(player.uniqueId, holder.guildId, it) }
        if (!allowed) {
            event.isCancelled = true
            player.sendMessage(lang.msg("notification.vault.inventory.action_denied"))
        }
        return allowed
    }

    private fun hasCurrentAccess(player: Player, holder: VaultInventoryHolder): Boolean {
        val allowed = memberService.hasPermission(player.uniqueId, holder.guildId, RankPermission.ACCESS_VAULT)
        if (!allowed) {
            player.sendMessage(lang.msg("notification.vault.inventory.access_denied"))
            closeRevokedView(player, holder)
        }
        return allowed
    }

    private fun closeRevokedView(player: Player, holder: VaultInventoryHolder) {
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
            val stillViewing = player.openInventory.topInventory === holder.inventory
            val restored = memberService.hasPermission(player.uniqueId, holder.guildId, RankPermission.ACCESS_VAULT)
            if (player.isOnline && stillViewing && !restored) player.closeInventory()
        })
    }

    private fun handleVaultClick(event: InventoryClickEvent, holder: VaultInventoryHolder, player: Player) {
        if (!affectsVault(event)) return
        vaultInventoryManager.validateAndRepairVault(holder.guildId, holder.inventory)
        if (protectBalanceButton(event, holder, player)) return
        if (event.rawSlot == 0) {
            event.isCancelled = true
            handleGoldButtonClick(player, holder.guildId, holder.guildName, event.click, event.isShiftClick)
        } else {
            handleNormalSlotClick(event, holder, player, holder.guildId, event.rawSlot)
        }
    }

    private fun affectsVault(event: InventoryClickEvent): Boolean =
        event.rawSlot in 0 until event.inventory.size ||
            event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY || event.action == InventoryAction.COLLECT_TO_CURSOR

    private fun protectBalanceButton(event: InventoryClickEvent, holder: VaultInventoryHolder, player: Player): Boolean {
        val blocked = transfersIntoEmptyButton(event, holder) || collectsBalanceButton(event, holder)
        if (blocked) {
            event.isCancelled = true
            player.sendMessage(lang.msg("notification.vault.inventory.reserved_button"))
        }
        return blocked
    }

    private fun transfersIntoEmptyButton(event: InventoryClickEvent, holder: VaultInventoryHolder): Boolean {
        return event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY &&
            event.currentItem != null && holder.inventory.getItem(0) == null
    }

    private fun collectsBalanceButton(event: InventoryClickEvent, holder: VaultInventoryHolder): Boolean {
        val button = holder.inventory.getItem(0)
        return event.action == InventoryAction.COLLECT_TO_CURSOR && button != null && event.cursor?.isSimilar(button) == true
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
                // Check DEPOSIT_TO_VAULT permission
                if (!memberService.hasPermission(player.uniqueId, guildId, RankPermission.DEPOSIT_TO_VAULT)) {
                    player.sendMessage(Component.text("You don't have permission to deposit to the vault!", NamedTextColor.RED))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }
                depositAllGold(player, guildId, guildName)
            }
            // Left Click: Open deposit menu
            clickType == ClickType.LEFT -> {
                // Check DEPOSIT_TO_VAULT permission
                if (!memberService.hasPermission(player.uniqueId, guildId, RankPermission.DEPOSIT_TO_VAULT)) {
                    player.sendMessage(Component.text("You don't have permission to deposit to the vault!", NamedTextColor.RED))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }
                val depositMenu = GoldDepositMenu(plugin, player, guildId, guildName, vaultInventoryManager, transactionLogger)
                depositMenu.open()
            }
            // Right Click: Open withdraw menu
            clickType == ClickType.RIGHT -> {
                // Check WITHDRAW_FROM_VAULT permission
                if (!memberService.hasPermission(player.uniqueId, guildId, RankPermission.WITHDRAW_FROM_VAULT)) {
                    player.sendMessage(Component.text("You don't have permission to withdraw from the vault!", NamedTextColor.RED))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }
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
        val totalNuggets = bankService.getMaxPhysicalDeposit(guildId, player.uniqueId)
        if (totalNuggets > 0) {
            val result = bankService.depositPhysical(PhysicalGoldRequest(
                java.util.UUID.randomUUID(), guildId, player.uniqueId, totalNuggets, "Guild vault deposit all"))
            if (result !is GuildGoldResult.Applied) {
                if (result is GuildGoldResult.Failed && !result.compensationSucceeded) {
                    player.sendMessage(lang.msg("menu.bank.feedback.deposit_pending", "transaction" to result.transactionId))
                } else {
                    player.sendMessage(lang.msg("menu.bank.feedback.deposit_rejected"))
                }
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                return
            }

            // Update gold button for all viewers
            updateGoldButtonForAllViewers(guildId)

            // Feedback
            player.sendMessage(lang.msg("menu.bank.feedback.deposit_success", "amount" to totalNuggets))
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
        if (!hasCurrentAccess(player, holder)) {
            event.isCancelled = true
            return
        }

        // SAFETY: Completely prevent drag-clicking in vaults.
        // Drag operations create race conditions and can cause item deletion.
        //
        // Use event.inventory.size as the authoritative slot bound rather than
        // holder.getCapacity() — Geyser (Bedrock bridge) may fire InventoryDragEvent
        // with rawSlots empty/missing and newItems.keys populated instead, so we union
        // both sets. For Bedrock players we also cancel unconditionally as a belt-and-
        // suspenders measure for Geyser's inconsistent drag event translation.
        val vaultSize = event.inventory.size
        val affectedSlots = event.rawSlots + event.newItems.keys
        val affectsVault = affectedSlots.any { slot -> slot in 0 until vaultSize }
            || platformDetectionService.isBedrockPlayer(player)

        if (affectsVault) {
            event.isCancelled = true
            player.sendMessage(
                Component.text("⚠ Drag-clicking is disabled in vaults for safety", NamedTextColor.YELLOW)
                    .append(Component.text(" - Please move items individually", NamedTextColor.GRAY))
            )
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun authorizeVaultOpen(event: InventoryOpenEvent) {
        val holder = event.inventory.holder as? VaultInventoryHolder ?: return
        val player = event.player as? Player ?: return
        if (!hasCurrentAccess(player, holder)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (event.isCancelled) return
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

        // CRITICAL: Check if vault is being removed (broken)
        // If the vault is being removed, we must NOT sync the inventory back
        // This prevents the duplication bug where items are re-seeded after being dropped
        val isBeingRemoved = vaultInventoryManager.isVaultBeingRemoved(guildId)

        if (isBeingRemoved) {
            // Vault is being broken - do NOT sync inventory back to cache/database
            logger.info("Vault for guild $guildId is being removed - skipping inventory sync to prevent item duplication")

            // Unregister viewer without flushing to database
            vaultInventoryManager.closeVaultFor(guildId, player.uniqueId, flushToDatabase = false)
            return
        }

        // Check if vault is still AVAILABLE before syncing
        // If the vault was broken while a player had it open, we must NOT sync the inventory back
        // Otherwise, items that were dropped will be duplicated when saved to the database
        val guild = guildRepository.getById(guildId)
        val vaultIsAvailable = guild?.vaultStatus == net.lumalyte.lg.domain.entities.VaultStatus.AVAILABLE

        if (vaultIsAvailable) {
            // Sync entire inventory state to cache
            vaultInventoryManager.syncInventoryToCache(guildId, inventory)
        } else {
            // Vault was broken - do NOT sync inventory back to cache/database
            logger.info("Vault for guild $guildId is UNAVAILABLE - skipping inventory sync to prevent item duplication")
        }

        // Check if this is the last viewer (before unregistering)
        val isLastViewer = inventory.viewers.size <= 1

        // Unregister viewer (but don't flush if vault is unavailable)
        vaultInventoryManager.closeVaultFor(guildId, player.uniqueId, flushToDatabase = vaultIsAvailable)

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
