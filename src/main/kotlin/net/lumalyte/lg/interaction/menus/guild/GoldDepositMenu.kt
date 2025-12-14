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
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/**
 * Menu for depositing gold into the guild vault.
 * Allows players to deposit gold items from their inventory into the vault balance.
 */
class GoldDepositMenu(
    private val plugin: JavaPlugin,
    private val player: Player,
    private val guildId: UUID,
    private val guildName: String,
    private val vaultInventoryManager: VaultInventoryManager,
    private val transactionLogger: VaultTransactionLogger
) : Listener {

    private lateinit var inventory: Inventory
    private var isOpen = false

    init {
        createInventory()
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    /**
     * Creates the deposit inventory interface.
     */
    private fun createInventory() {
        inventory = Bukkit.createInventory(null, 27, Component.text("Deposit Gold"))

        // Add instruction item
        val instructionItem = ItemStack(Material.PAPER).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(
                    Component.text("How to Deposit Gold", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
                )
                meta.lore(
                    listOf(
                        Component.empty(),
                        Component.text("Place gold items in this inventory:", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.text("  • Raw Gold (1 currency each)", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.text("  • Raw Gold Block (9 currency each)", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Or click the 'Deposit All' button", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.text("to deposit all gold instantly", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Close the inventory to confirm deposit", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }
        inventory.setItem(13, instructionItem)

        // Add deposit all button
        val depositAllItem = ItemStack(Material.GOLD_BLOCK).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(
                    Component.text("Deposit All Gold", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                )
                meta.lore(
                    listOf(
                        Component.empty(),
                        Component.text("Click to deposit all gold items", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.text("from your inventory instantly", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }
        inventory.setItem(22, depositAllItem)
    }

    /**
     * Opens the deposit menu for the player.
     */
    fun open() {
        player.openInventory(inventory)
        isOpen = true
    }

    /**
     * Deposits all gold items from the player's inventory.
     */
    private fun depositAllGold() {
        var totalNuggets = 0L
        val itemsToRemove = mutableListOf<Int>()

        // Scan player inventory for gold items
        for (i in 0 until player.inventory.size) {
            val item = player.inventory.getItem(i) ?: continue

            val value = GoldBalanceButton.calculateGoldValue(item)
            if (value > 0) {
                totalNuggets += value
                itemsToRemove.add(i)
            }
        }

        if (totalNuggets > 0) {
            // Remove gold items from player inventory
            for (slot in itemsToRemove) {
                player.inventory.setItem(slot, null)
            }

            // Force inventory update to client
            player.updateInventory()

            // Add to vault balance atomically (prevents race conditions)
            val newBalance = vaultInventoryManager.depositGold(guildId, player.uniqueId, totalNuggets)

            // Immediate flush to database (high-value transaction)
            vaultInventoryManager.forceFlush(guildId)

            // Feedback
            player.sendMessage(
                Component.text("✓ Deposited ", NamedTextColor.GREEN)
                    .append(Component.text("$totalNuggets currency", NamedTextColor.GOLD))
                    .append(Component.text(" into the guild vault", NamedTextColor.GREEN))
            )
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)
        } else {
            player.sendMessage(Component.text("You have no gold items to deposit", NamedTextColor.RED))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }

        player.closeInventory()
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked.uniqueId != player.uniqueId) return
        if (!isOpen) return

        // Check if clicking in the top inventory (deposit menu)
        if (event.clickedInventory == inventory) {
            val clickedItem = event.currentItem

            // Handle deposit all button click
            if (event.slot == 22 && clickedItem != null && clickedItem.type == Material.GOLD_BLOCK) {
                event.isCancelled = true
                depositAllGold()
                return
            }

            // Handle instruction item click (prevent removal)
            if (event.slot == 13 && clickedItem != null && clickedItem.type == Material.PAPER) {
                event.isCancelled = true
                return
            }

            // Allow placing gold items only
            if (event.cursor.type != Material.AIR) {
                val cursorValue = GoldBalanceButton.calculateGoldValue(event.cursor)
                if (cursorValue == 0L) {
                    event.isCancelled = true
                    player.sendMessage(Component.text("Only gold items can be deposited", NamedTextColor.RED))
                }
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.player.uniqueId != player.uniqueId) return
        if (event.inventory != inventory) return
        if (!isOpen) return

        isOpen = false

        // Calculate total gold value in the inventory
        var totalNuggets = 0L

        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (item.type == Material.PAPER || item.type == Material.GOLD_BLOCK && i == 22) {
                continue // Skip instruction and deposit all button
            }

            val value = GoldBalanceButton.calculateGoldValue(item)
            totalNuggets += value
        }

        if (totalNuggets > 0) {
            // Add to vault balance atomically (prevents race conditions)
            val newBalance = vaultInventoryManager.depositGold(guildId, player.uniqueId, totalNuggets)

            // Immediate flush to database (high-value transaction)
            vaultInventoryManager.forceFlush(guildId)

            // Feedback
            player.sendMessage(
                Component.text("✓ Deposited ", NamedTextColor.GREEN)
                    .append(Component.text("$totalNuggets currency", NamedTextColor.GOLD))
                    .append(Component.text(" into $guildName's vault", NamedTextColor.GREEN))
            )
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)
        }

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
