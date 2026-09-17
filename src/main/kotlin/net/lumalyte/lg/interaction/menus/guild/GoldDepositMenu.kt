package net.lumalyte.lg.interaction.menus.guild

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.PhysicalGoldRequest
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.badgersmc.nexus.i18n.LangService

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
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
) : Listener, KoinComponent {

    private val bankService: BankService by inject()
    private val lang: LangService by inject()

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
        val instructionItem = ItemStack.of(Material.PAPER).apply {
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
        val depositAllItem = ItemStack.of(Material.GOLD_BLOCK).apply {
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
        finishDeposit(confirm = true)
    }

    private fun finishDeposit(confirm: Boolean) {
        if (!isOpen) return

        isOpen = false

        val physical: net.lumalyte.lg.infrastructure.services.BukkitPhysicalGoldAdapter by inject()
        try {
            physical.withDepositInventory(player.uniqueId, inventory, setOf(13, 22)) {
                val amount = if (confirm) bankService.getMaxPhysicalDeposit(guildId, player.uniqueId) else 0
                if (amount > 0) {
                    val result = bankService.depositPhysical(PhysicalGoldRequest(
                        java.util.UUID.randomUUID(), guildId, player.uniqueId, amount, "Guild deposit window"))
                    when {
                        result is GuildGoldResult.Applied ->
                            player.sendMessage(lang.msg("menu.bank.feedback.deposit_success", "amount" to amount))
                        result is GuildGoldResult.Failed && !result.compensationSucceeded ->
                            player.sendMessage(lang.msg("menu.bank.feedback.deposit_pending", "transaction" to result.transactionId))
                        else -> player.sendMessage(lang.msg("menu.bank.feedback.deposit_rejected"))
                    }
                }
            }
        } finally {
            unregisterListeners()
        }

    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (event.player.uniqueId != player.uniqueId) return

        finishDeposit(confirm = false)
    }

    private fun unregisterListeners() {
        InventoryClickEvent.getHandlerList().unregister(this)
        InventoryCloseEvent.getHandlerList().unregister(this)
        PlayerQuitEvent.getHandlerList().unregister(this)
    }
}
