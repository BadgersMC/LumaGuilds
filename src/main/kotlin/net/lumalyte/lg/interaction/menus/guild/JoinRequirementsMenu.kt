package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService

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
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.legacy("menu.join_requirements.title", "guild" to guild.name)))
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

        val guildItem = ItemStack.of(Material.WHITE_BANNER)
            .name(lang.legacy("menu.join_requirements.guild.name", "guild" to guild.name))
            .lore(lang.legacy("menu.join_requirements.guild.description"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.join_requirements.guild.level", "level" to guild.level))
            .lore(if (isPeaceful) lang.legacy("menu.join_requirements.guild.mode.peaceful") else lang.legacy("menu.join_requirements.guild.mode.hostile"))

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
            val currencyMaterial = if (requirement.isPhysicalCurrency) {
                try { Material.valueOf(requirement.currencyName) } catch (e: Exception) { Material.GOLD_INGOT }
            } else {
                Material.GOLD_INGOT
            }

            ItemStack.of(currencyMaterial)
                .name(lang.legacy("menu.join_requirements.fee.name"))
                .lore(lang.legacy("menu.join_requirements.fee.description"))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.join_requirements.fee.required", "amount" to requirement.amount, "currency" to formatCurrencyName(requirement.currencyName)))
                .lore(if (hasEnough) {
                    lang.legacy("menu.join_requirements.fee.balance.enough", "amount" to playerBalance, "currency" to formatCurrencyName(requirement.currencyName))
                } else {
                    lang.legacy("menu.join_requirements.fee.balance.insufficient", "amount" to playerBalance, "currency" to formatCurrencyName(requirement.currencyName))
                })
                .lore(lang.legacy("menu.common.blank"))
                .lore(if (hasEnough) lang.legacy("menu.join_requirements.fee.status.enough") else lang.legacy("menu.join_requirements.fee.status.insufficient"))
        } else {
            // No join fee
            ItemStack.of(Material.EMERALD)
                .name(lang.legacy("menu.join_requirements.free.name"))
                .lore(lang.legacy("menu.join_requirements.free.description"))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.join_requirements.free.confirm"))
        }

        pane.addItem(GuiItem(item), x, y)
    }

    private fun addConfirmButton(pane: StaticPane, x: Int, y: Int) {
        val canJoinResult = lfgService.canJoinGuild(player.uniqueId, guild)
        val canJoin = canJoinResult is LfgJoinResult.Success

        val confirmItem = if (canJoin) {
            ItemStack.of(Material.GREEN_WOOL)
                .name(lang.legacy("menu.join_requirements.confirm.name"))
                .lore(lang.legacy("menu.join_requirements.confirm.description", "guild" to guild.name))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.join_requirements.confirm.proceed"))
        } else {
            ItemStack.of(Material.GRAY_WOOL)
                .name(lang.legacy("menu.join_requirements.confirm.disabled"))
                .lore(lang.legacy("menu.join_requirements.confirm.unavailable"))
                .lore(lang.legacy("menu.common.blank"))
                .lore(getCannotJoinReason(canJoinResult))
        }

        val confirmGuiItem = GuiItem(confirmItem) {
            if (canJoin) {
                processJoin()
            } else {
                player.sendMessage(lang.msg("menu.join_requirements.feedback.cannot_join", "reason" to getCannotJoinReason(canJoinResult)))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
        pane.addItem(confirmGuiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack.of(Material.RED_WOOL)
            .name(lang.legacy("menu.join_requirements.cancel.name"))
            .lore(lang.legacy("menu.join_requirements.cancel.description"))
            .lore(lang.legacy("menu.join_requirements.cancel.effect"))

        val cancelGuiItem = GuiItem(cancelItem) {
            player.closeInventory()
            // Return to LFG menu - will be implemented later
            player.sendMessage(lang.msg("menu.join_requirements.feedback.cancelled", "guild" to guild.name))
        }
        pane.addItem(cancelGuiItem, x, y)
    }

    private fun processJoin() {
        player.closeInventory()

        val result = lfgService.joinGuild(player.uniqueId, guild)

        when (result) {
            is LfgJoinResult.Success -> {
                player.sendMessage(lang.msg("menu.join_requirements.feedback.success", "message" to result.message))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
            }
            is LfgJoinResult.InsufficientFunds -> {
                player.sendMessage(lang.msg("menu.join_requirements.feedback.insufficient_funds", "required" to result.required, "currency" to result.currencyType, "current" to result.current))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            is LfgJoinResult.GuildFull -> {
                player.sendMessage(lang.msg("menu.join_requirements.feedback.error", "message" to result.message))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            is LfgJoinResult.AlreadyInGuild -> {
                player.sendMessage(lang.msg("menu.join_requirements.feedback.error", "message" to result.message))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            is LfgJoinResult.VaultUnavailable -> {
                player.sendMessage(lang.msg("menu.join_requirements.feedback.error", "message" to result.message))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            is LfgJoinResult.Error -> {
                player.sendMessage(lang.msg("menu.join_requirements.feedback.error", "message" to result.message))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
    }

    private fun getCannotJoinReason(result: LfgJoinResult): String {
        return when (result) {
            is LfgJoinResult.InsufficientFunds -> lang.legacy("menu.join_requirements.reason.insufficient_funds", "required" to result.required, "currency" to result.currencyType)
            is LfgJoinResult.GuildFull -> lang.legacy("menu.join_requirements.reason.guild_full")
            is LfgJoinResult.AlreadyInGuild -> lang.legacy("menu.join_requirements.reason.already_member")
            is LfgJoinResult.VaultUnavailable -> lang.legacy("menu.join_requirements.reason.vault_unavailable")
            is LfgJoinResult.Error -> lang.legacy("menu.join_requirements.reason.error", "message" to result.message)
            is LfgJoinResult.Success -> lang.legacy("menu.join_requirements.reason.ready")
        }
    }

    private fun formatCurrencyName(name: String): String {
        return name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    override fun passData(data: Any?) {
        // No data passing needed
    }
}
