package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.util.logging.Logger
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Bedrock Edition guild bank security menu using Cumulus CustomForm
 * Provides comprehensive bank security management for Bedrock players
 */
class BedrockGuildBankSecurityMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
    messageService: MessageService
) : BaseBedrockMenu(menuNavigator, player, logger, messageService), KoinComponent {

    private val bankService: BankService by inject()
    private val memberService: MemberService by inject()

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done via validResultHandler in getForm()
        // This method satisfies the BedrockMenu interface requirement
    }

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val securityIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        val securitySettings = bankService.getSecuritySettings(guild.id)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "form.title.bank.security")} - ${guild.name}")
            .apply { securityIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "bank.security.description"))
            .label("<bold><gold>=== Current Security Settings ===")
            .label("<gray>Dual Authorization Threshold: <white>${securitySettings?.dualAuthThreshold ?: 1000} coins")
            .label("<gray>Fraud Detection: <white>${if (securitySettings?.fraudDetectionEnabled == true) "§aEnabled" else "<red>Disabled"}")
            .label("<gray>Emergency Freeze: <white>${if (securitySettings?.emergencyFreeze == true) "§cACTIVE" else "<green>Inactive"}")
            .label("")
            .addLocalizedToggle(
                player, bedrockLocalization,
                "bank.security.fraud.detection",
                securitySettings?.fraudDetectionEnabled == true
            )
            .addLocalizedInput(
                player, bedrockLocalization,
                "bank.security.threshold",
                securitySettings?.dualAuthThreshold?.toString() ?: "1000"
            )
            .addLocalizedToggle(
                player, bedrockLocalization,
                "bank.security.emergency.freeze",
                securitySettings?.emergencyFreeze == true
            )
            .validResultHandler { response ->
                handleSecuritySettingsUpdate(response)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun handleSecuritySettingsUpdate(response: org.geysermc.cumulus.response.CustomFormResponse) {
        try {
            val fraudDetectionEnabled = response.asToggle(0) ?: false
            val dualAuthThreshold = response.asInput(1)?.toIntOrNull() ?: 1000
            val emergencyFreeze = response.asToggle(2) ?: false

            // Check permissions
            if (!hasSecurityManagementPermission()) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to manage bank security!")
                return
            }

            val settings = bankService.getSecuritySettings(guild.id)
                ?: net.lumalyte.lg.domain.entities.BankSecuritySettings(
                    id = java.util.UUID.randomUUID(),
                    guildId = guild.id,
                    dualAuthThreshold = dualAuthThreshold,
                    fraudDetectionEnabled = fraudDetectionEnabled,
                    emergencyFreeze = emergencyFreeze
                )

            val updatedSettings = settings.copy(
                dualAuthThreshold = dualAuthThreshold,
                fraudDetectionEnabled = fraudDetectionEnabled,
                emergencyFreeze = emergencyFreeze,
                updatedAt = Instant.now()
            )

            val success = bankService.updateSecuritySettings(guild.id, updatedSettings)
            if (success) {
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Bank security settings updated!")

                if (emergencyFreeze && !settings.emergencyFreeze) {
                    bankService.activateEmergencyFreeze(guild.id, player.uniqueId, "Activated via Bedrock menu")
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>⚠️ Emergency freeze activated!")
                } else if (!emergencyFreeze && settings.emergencyFreeze) {
                    bankService.deactivateEmergencyFreeze(guild.id, player.uniqueId)
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>Emergency freeze deactivated!")
                }
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to update security settings!")
            }
        } catch (e: Exception) {
            logger.warning("Error updating bank security settings: ${e.message}")
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Error updating security settings!")
        }
    }

    private fun hasSecurityManagementPermission(): Boolean {
        return memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_BANK_SECURITY) ||
               memberService.hasPermission(player.uniqueId, guild.id, RankPermission.ACTIVATE_EMERGENCY_FREEZE) ||
               memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DEACTIVATE_EMERGENCY_FREEZE)
    }
}

