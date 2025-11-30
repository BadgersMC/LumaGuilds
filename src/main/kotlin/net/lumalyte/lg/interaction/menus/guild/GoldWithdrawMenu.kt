package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.lumalyte.lg.application.services.VaultInventoryManager
import net.lumalyte.lg.application.utilities.GoldBalanceButton
import net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionLogger
import net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/**
 * Menu for withdrawing gold from the guild vault.
 * Allows players to withdraw gold in optimal item form (blocks, ingots, nuggets).
 */
class GoldWithdrawMenu(
    private val plugin: JavaPlugin,
    private val player: Player,
    private val guildId: UUID,
    private val guildName: String,
    private val vaultInventoryManager: VaultInventoryManager,
    private val transactionLogger: VaultTransactionLogger
) : Listener {

    private lateinit var inventory: Inventory
    private var isOpen = false

    // PDC key for storing gold nugget value on withdraw buttons
    private val nuggetValueKey = NamespacedKey(plugin, "withdraw_nugget_value")

    init {
        createInventory()
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    /**
     * Creates the withdraw inventory interface.
     */
    private fun createInventory() {
        inventory = Bukkit.createInventory(null, 27, Component.text("Withdraw Gold"))

        val currentBalance = vaultInventoryManager.getGoldBalance(guildId)

        // Add current balance display
        val balanceItem = ItemStack(Material.RAW_GOLD).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(
                    Component.text("Current Vault Balance", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
                )
                meta.lore(
                    listOf(
                        Component.empty(),
                        Component.text("⭐ $currentBalance Currency", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.empty()
                    )
                )
            }
        }
        inventory.setItem(13, balanceItem)

        // Add quick withdraw buttons
        addQuickWithdrawButton(10, 1, "1 Currency")
        addQuickWithdrawButton(11, 8, "8 Currency")
        addQuickWithdrawButton(12, 64, "64 Currency")

        addQuickWithdrawButton(19, 128, "128 Currency")
        addQuickWithdrawButton(20, 256, "256 Currency")
        addQuickWithdrawButton(21, 512, "512 Currency")

        // Add withdraw all button
        val withdrawAllItem = ItemStack(Material.RAW_GOLD).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(
                    Component.text("Withdraw All Gold", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                )
                meta.lore(
                    listOf(
                        Component.empty(),
                        Component.text("Click to withdraw all gold", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.text("from the vault in optimal form", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }
        inventory.setItem(22, withdrawAllItem)
    }

    /**
     * Adds a quick withdraw button to the inventory.
     */
    private fun addQuickWithdrawButton(
        slot: Int,
        amount: Long,
        description: String
    ) {
        val displayAmount = minOf(amount, 64).toInt()
        val item = ItemStack(Material.RAW_GOLD, displayAmount).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(
                    Component.text("Withdraw $description", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
                )
                meta.lore(
                    listOf(
                        Component.empty(),
                        Component.text("Click to withdraw", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                    )
                )
                // Store currency amount in item's persistent data container for reference
                meta.persistentDataContainer.set(nuggetValueKey, PersistentDataType.LONG, amount)
            }
        }

        inventory.setItem(slot, item)
    }

    /**
     * Opens the withdraw menu for the player.
     */
    fun open() {
        player.openInventory(inventory)
        isOpen = true
    }

    /**
     * Withdraws a specific amount of gold from the vault.
     */
    private fun withdrawGold(amount: Long) {
        // Attempt atomic withdrawal (prevents race conditions)
        val newBalance = vaultInventoryManager.withdrawGold(guildId, player.uniqueId, amount)

        if (newBalance == -1L) {
            player.sendMessage(
                Component.text("Insufficient gold in vault", NamedTextColor.RED)
            )
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Immediate flush to database (high-value transaction)
        vaultInventoryManager.forceFlush(guildId)

        // Give items to player in optimal form
        val items = GoldBalanceButton.convertToItems(amount)
        val leftoverItems = mutableListOf<ItemStack>()

        for (item in items) {
            val leftovers = player.inventory.addItem(item)
            leftoverItems.addAll(leftovers.values)
        }

        // Drop items that didn't fit in inventory
        if (leftoverItems.isNotEmpty()) {
            for (item in leftoverItems) {
                player.world.dropItemNaturally(player.location, item)
            }
            player.sendMessage(
                Component.text("Some items were dropped at your feet (inventory full)", NamedTextColor.YELLOW)
            )
        }

        // Feedback
        player.sendMessage(
            Component.text("✓ Withdrew ", NamedTextColor.GREEN)
                .append(Component.text("$amount currency", NamedTextColor.GOLD))
                .append(Component.text(" from $guildName's vault", NamedTextColor.GREEN))
        )
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)

        player.closeInventory()
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked.uniqueId != player.uniqueId) return
        if (event.clickedInventory != inventory) return
        if (!isOpen) return

        event.isCancelled = true

        val clickedItem = event.currentItem ?: return

        // Handle withdraw all button
        if (event.slot == 22 && clickedItem.type == Material.GOLD_BLOCK) {
            val currentBalance = vaultInventoryManager.getGoldBalance(guildId)
            if (currentBalance > 0) {
                withdrawGold(currentBalance)
            } else {
                player.sendMessage(Component.text("Vault has no gold to withdraw", NamedTextColor.RED))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            return
        }

        // Handle balance display (prevent interaction)
        if (event.slot == 13) {
            return
        }

        // Handle quick withdraw buttons
        val meta = clickedItem.itemMeta
        if (meta != null && meta.persistentDataContainer.has(nuggetValueKey, PersistentDataType.LONG)) {
            val nuggetValue = meta.persistentDataContainer.get(nuggetValueKey, PersistentDataType.LONG) ?: return
            withdrawGold(nuggetValue)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.player.uniqueId != player.uniqueId) return
        if (event.inventory != inventory) return
        if (!isOpen) return

        isOpen = false

        // Unregister listener
        unregisterListeners()
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (event.player.uniqueId != player.uniqueId) return

        // Player disconnected - cleanup
        isOpen = false
        unregisterListeners()
    }

    private fun unregisterListeners() {
        InventoryClickEvent.getHandlerList().unregister(this)
        InventoryCloseEvent.getHandlerList().unregister(this)
        PlayerQuitEvent.getHandlerList().unregister(this)
    }
}
