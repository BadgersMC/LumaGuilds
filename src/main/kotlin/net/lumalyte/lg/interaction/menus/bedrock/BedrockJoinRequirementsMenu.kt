package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.LfgJoinResult
import net.lumalyte.lg.application.services.LfgService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.JoinRequirementsMenu
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition menu showing join requirements for a guild via LFG.
 * Displays join fee details and allows the player to confirm joining.
 */
class BedrockJoinRequirementsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val lfgService: LfgService by inject()
    private val configService: ConfigService by inject()
    private val physicalCurrencyService: PhysicalCurrencyService by inject()
    private val bankService: BankService by inject()

    override fun getForm(): Form {
        val requirement = lfgService.getJoinRequirement(guild)
        val canJoinResult = lfgService.canJoinGuild(player.uniqueId, guild)
        val canJoin = canJoinResult is LfgJoinResult.Success

        val content = buildFormContent(requirement, canJoinResult)

        val builder = SimpleForm.builder()
            .title(localize("lfg.join.requirements.title"))
            .content(content)

        if (canJoin) {
            builder.button(localize("lfg.join.requirements.button.join"))
        }
        builder.button(localize("lfg.join.requirements.button.cancel"))

        return builder
            .validResultHandler { response ->
                onFormResponseReceived()
                val buttonId = response.clickedButtonId()

                if (canJoin && buttonId == 0) {
                    processJoin()
                } else {
                    // Cancel button or cannot join
                    player.sendMessage(localize("lfg.join.requirements.cancelled"))
                    player.closeInventory()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(localize("lfg.join.requirements.cancelled"))
            })
            .build()
    }

    private fun buildFormContent(
        requirement: net.lumalyte.lg.domain.values.JoinRequirement?,
        canJoinResult: LfgJoinResult
    ): String {
        val sb = StringBuilder()

        // Guild info section
        val isPeaceful = guild.mode == GuildMode.PEACEFUL
        val modeText = if (isPeaceful) {
            localize("lfg.join.requirements.mode.peaceful")
        } else {
            localize("lfg.join.requirements.mode.hostile")
        }

        sb.appendLine(localize("lfg.join.requirements.guild.header", guild.name))
        sb.appendLine(localize("lfg.join.requirements.guild.level", guild.level))
        sb.appendLine(localize("lfg.join.requirements.guild.mode", modeText))
        sb.appendLine()

        // Join requirement section
        if (requirement != null) {
            val playerBalance = if (requirement.isPhysicalCurrency) {
                physicalCurrencyService.calculatePlayerInventoryValue(player.uniqueId)
            } else {
                bankService.getPlayerBalance(player.uniqueId)
            }

            val hasEnough = playerBalance >= requirement.amount
            val currencyDisplayName = formatCurrencyName(requirement.currencyName)

            sb.appendLine(localize("lfg.join.requirements.fee.header"))
            sb.appendLine(localize("lfg.join.requirements.fee.required", requirement.amount, currencyDisplayName))
            sb.appendLine(localize("lfg.join.requirements.fee.balance", playerBalance, currencyDisplayName))
            sb.appendLine()

            if (hasEnough) {
                sb.appendLine(localize("lfg.join.requirements.fee.sufficient"))
            } else {
                sb.appendLine(localize("lfg.join.requirements.fee.insufficient"))
            }
        } else {
            sb.appendLine(localize("lfg.join.requirements.no.fee"))
            sb.appendLine(localize("lfg.join.requirements.no.fee.description"))
        }

        // Status section
        sb.appendLine()
        when (canJoinResult) {
            is LfgJoinResult.Success -> {
                sb.appendLine(localize("lfg.join.requirements.status.ready"))
            }
            is LfgJoinResult.InsufficientFunds -> {
                sb.appendLine(localize("lfg.join.requirements.status.insufficient", canJoinResult.required, canJoinResult.currencyType))
            }
            is LfgJoinResult.GuildFull -> {
                sb.appendLine(localize("lfg.join.requirements.status.full"))
            }
            is LfgJoinResult.AlreadyInGuild -> {
                sb.appendLine(localize("lfg.join.requirements.status.already.in.guild"))
            }
            is LfgJoinResult.VaultUnavailable -> {
                sb.appendLine(localize("lfg.join.requirements.status.vault.unavailable"))
            }
            is LfgJoinResult.Error -> {
                sb.appendLine(localize("lfg.join.requirements.status.error", canJoinResult.message))
            }
        }

        return sb.toString()
    }

    private fun processJoin() {
        val result = lfgService.joinGuild(player.uniqueId, guild)

        when (result) {
            is LfgJoinResult.Success -> {
                player.sendMessage(localize("lfg.join.success", guild.name))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
            }
            is LfgJoinResult.InsufficientFunds -> {
                player.sendMessage(localize("lfg.join.error.insufficient", result.required, result.currencyType, result.current))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            is LfgJoinResult.GuildFull -> {
                player.sendMessage(localize("lfg.join.error.full"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            is LfgJoinResult.AlreadyInGuild -> {
                player.sendMessage(localize("lfg.join.error.already.member"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            is LfgJoinResult.VaultUnavailable -> {
                player.sendMessage(localize("lfg.join.error.vault.unavailable"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            is LfgJoinResult.Error -> {
                player.sendMessage(localize("lfg.join.error.generic", result.message))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
    }

    private fun formatCurrencyName(name: String): String {
        return name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
    }

    override fun createFallbackJavaMenu(): Menu {
        return JoinRequirementsMenu(menuNavigator, player, guild)
    }
}
