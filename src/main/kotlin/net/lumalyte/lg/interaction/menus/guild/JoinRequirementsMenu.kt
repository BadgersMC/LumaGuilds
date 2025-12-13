package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.LfgJoinResult
import net.lumalyte.lg.application.services.LfgService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.JoinRequirement
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Menu showing join requirements for a guild via LFG.
 * Displays join fee details and allows the player to confirm joining.
 */
class JoinRequirementsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
) : Menu, KoinComponent {

    private val lfgService: LfgService by inject()
    private val configService: ConfigService by inject()
    private val physicalCurrencyService: PhysicalCurrencyService by inject()
    private val bankService: BankService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        val gui = ChestGui(3, "Join ${guild.name}")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Guild info display
        addGuildInfo(pane, 1, 1)

        // Join requirement display
        addJoinRequirementInfo(pane, 4, 1)

        // Action buttons
        addConfirmButton(pane, 6, 1)
        addCancelButton(pane, 8, 1)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addGuildInfo(pane: StaticPane, x: Int, y: Int) {
        val isPeaceful = guild.mode == net.lumalyte.lg.domain.entities.GuildMode.PEACEFUL

        val guildItem = ItemStack(Material.WHITE_BANNER)
            .name("§6${guild.name}")
            .lore("§7Guild you are joining")
            .lore("§7")
            .lore("§7Level: §f${guild.level}")
            .lore("§7Mode: ${if (isPeaceful) "§aPeaceful" else "§cHostile"}")

        pane.addItem(GuiItem(guildItem), x, y)
    }

    private fun addJoinRequirementInfo(pane: StaticPane, x: Int, y: Int) {
        val requirement = lfgService.getJoinRequirement(guild)
        val config = configService.loadConfig()

        val item = if (requirement != null) {
            // Show join fee requirement
            val playerBalance = if (requirement.isPhysicalCurrency) {
                physicalCurrencyService.calculatePlayerInventoryValue(player.uniqueId)
            } else {
                bankService.getPlayerBalance(player.uniqueId)
            }

            val hasEnough = playerBalance >= requirement.amount
            val statusColor = if (hasEnough) "§a" else "§c"
            val statusIcon = if (hasEnough) "✓" else "✗"

            val currencyMaterial = if (requirement.isPhysicalCurrency) {
                try { Material.valueOf(requirement.currencyName) } catch (e: Exception) { Material.GOLD_INGOT }
            } else {
                Material.GOLD_INGOT
            }

            ItemStack(currencyMaterial)
                .name("§e💰 JOIN FEE")
                .lore("§7This guild requires a join fee")
                .lore("§7")
                .lore("§7Required: §f${requirement.amount} ${formatCurrencyName(requirement.currencyName)}")
                .lore("§7Your balance: $statusColor$playerBalance ${formatCurrencyName(requirement.currencyName)}")
                .lore("§7")
                .lore("$statusColor$statusIcon ${if (hasEnough) "You have enough!" else "Insufficient funds!"}")
        } else {
            // No join fee
            ItemStack(Material.EMERALD)
                .name("§a✓ NO JOIN FEE")
                .lore("§7This guild has no join fee!")
                .lore("§7")
                .lore("§aClick confirm to join for free")
        }

        pane.addItem(GuiItem(item), x, y)
    }

    private fun addConfirmButton(pane: StaticPane, x: Int, y: Int) {
        val canJoinResult = lfgService.canJoinGuild(player.uniqueId, guild)
        val canJoin = canJoinResult is LfgJoinResult.Success

        val confirmItem = if (canJoin) {
            ItemStack(Material.GREEN_WOOL)
                .name("§a✅ JOIN GUILD")
                .lore("§7Click to join ${guild.name}")
                .lore("§7")
                .lore("§eClick to proceed")
        } else {
            ItemStack(Material.GRAY_WOOL)
                .name("§c❌ CANNOT JOIN")
                .lore("§7You cannot join this guild")
                .lore("§7")
                .lore(getCannotJoinReason(canJoinResult))
        }

        val confirmGuiItem = GuiItem(confirmItem) {
            if (canJoin) {
                processJoin()
            } else {
                player.sendMessage("§c❌ ${getCannotJoinReason(canJoinResult)}")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
        pane.addItem(confirmGuiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack(Material.RED_WOOL)
            .name("§c❌ CANCEL")
            .lore("§7Return to guild list")
            .lore("§7No action will be taken")

        val cancelGuiItem = GuiItem(cancelItem) {
            player.closeInventory()
            // Return to LFG menu - will be implemented later
            player.sendMessage("§7Cancelled joining ${guild.name}")
        }
        pane.addItem(cancelGuiItem, x, y)
    }

    private fun processJoin() {
        player.closeInventory()

        val result = lfgService.joinGuild(player.uniqueId, guild)

        when (result) {
            is LfgJoinResult.Success -> {
                player.sendMessage("§a✅ ${result.message}")
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
            }
            is LfgJoinResult.InsufficientFunds -> {
                player.sendMessage("§c❌ Insufficient funds! You need ${result.required} ${result.currencyType} but only have ${result.current}")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            is LfgJoinResult.GuildFull -> {
                player.sendMessage("§c❌ ${result.message}")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            is LfgJoinResult.AlreadyInGuild -> {
                player.sendMessage("§c❌ ${result.message}")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            is LfgJoinResult.VaultUnavailable -> {
                player.sendMessage("§c❌ ${result.message}")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            is LfgJoinResult.Error -> {
                player.sendMessage("§c❌ ${result.message}")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
    }

    private fun getCannotJoinReason(result: LfgJoinResult): String {
        return when (result) {
            is LfgJoinResult.InsufficientFunds -> "§cNeed ${result.required} ${result.currencyType}"
            is LfgJoinResult.GuildFull -> "§cGuild is full"
            is LfgJoinResult.AlreadyInGuild -> "§cYou're already in a guild"
            is LfgJoinResult.VaultUnavailable -> "§cGuild vault unavailable"
            is LfgJoinResult.Error -> "§c${result.message}"
            is LfgJoinResult.Success -> "§aReady to join"
        }
    }

    private fun formatCurrencyName(name: String): String {
        return name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    override fun passData(data: Any?) {
        // No data passing needed
    }
}
