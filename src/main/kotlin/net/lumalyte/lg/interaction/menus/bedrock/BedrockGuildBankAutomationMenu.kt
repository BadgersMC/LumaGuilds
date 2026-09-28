package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.BankSettingsRepository
import net.lumalyte.lg.application.services.BankAutomationService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.BankSettings
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant
import java.util.logging.Logger

/** Bedrock bank automation status/editor backed by the same executing automation as Java. */
class BedrockGuildBankAutomationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val bankSettingsRepository: BankSettingsRepository by inject()
    private val bankAutomationService: BankAutomationService by inject()
    private val configService: ConfigService by inject()
    private val guildService: GuildService by inject()
    private val authorization by lazy { BedrockGuildAuthorization(guildService) }
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        if (!authorization.canManageBankSettings(player.uniqueId, guild.id)) {
            return noPermissionForm()
        }

        val settings = bankSettingsRepository.getByGuildId(guild.id) ?: BankSettings(guild.id)
        val nextRun = bankAutomationService.getNextInterestRun(guild.id)
        val periodHours = configService.loadConfig().bank.interestCompoundPeriodHours
        val editor = BedrockBankSettingsEditor(bankSettingsRepository) { targetGuildId ->
            authorization.canManageBankSettings(player.uniqueId, targetGuildId)
        }

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.bank_automation.title", "guild" to guild.name))
            .label(
                readOnlySetting(
                    lang.bedrock("bedrock.bank_automation.scheduled_deposits"),
                    settings.scheduledDepositsEnabled
                )
            )
            .label(
                readOnlySetting(
                    lang.bedrock("bedrock.bank_automation.auto_rewards"),
                    settings.autoRewardsEnabled
                )
            )
            .label(
                readOnlySetting(
                    lang.bedrock("bedrock.bank_automation.recurring_payments"),
                    settings.recurringPaymentsEnabled
                )
            )
            .label(automationStatus(settings, nextRun, periodHours))
            .input(
                lang.bedrock("bedrock.bank_automation.interest_percent"),
                lang.bedrock("bedrock.bank_automation.interest_placeholder"),
                (settings.interestRate * 100.0).toString()
            )
            .validResultHandler { response ->
                val interestPercent = response.next() as? String ?: ""
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (!player.isOnline) return@Runnable
                    onFormResponseReceived()
                    if (editor.saveInterestRate(guild.id, interestPercent)) {
                        player.sendMessage(lang.msg("menu.bank_automation.feedback.saved"))
                        open()
                    } else {
                        player.sendMessage(lang.msg("bedrock.bank_automation.invalid"))
                        open()
                    }
                })
            }
            .closedOrInvalidResultHandler { _, _ ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onFormResponseReceived()
                    if (player.isOnline) bedrockNavigator.goBack()
                })
            }
            .build()
    }

    private fun noPermissionForm(): Form =
        CustomForm.builder()
            .title(lang.bedrock("bedrock.bank_automation.title", "guild" to guild.name))
            .label(lang.bedrock("bedrock.bank.management.no_permission"))
            .validResultHandler {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.isOnline) bedrockNavigator.goBack()
                })
            }
            .closedOrInvalidResultHandler { _, _ ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.isOnline) bedrockNavigator.goBack()
                })
            }
            .build()

    private fun readOnlySetting(setting: String, enabled: Boolean): String {
        val state = if (enabled) {
            lang.bedrock("bedrock.bank.management.enabled")
        } else {
            lang.bedrock("bedrock.bank.management.disabled")
        }
        return lang.bedrock(
            "bedrock.bank_automation.read_only",
            "setting" to setting,
            "state" to state
        )
    }

    private fun automationStatus(settings: BankSettings, nextRun: Instant?, periodHours: Int): String {
        val status = if (settings.interestRate > 0.0) {
            lang.bedrock(
                "bedrock.bank_automation.status.active",
                "rate" to String.format("%.2f", settings.interestRate * 100.0),
                "hours" to periodHours
            )
        } else {
            lang.bedrock("bedrock.bank_automation.status.inactive")
        }
        val next = nextRun?.let {
            lang.bedrock(
                "bedrock.bank_automation.status.next_run",
                "time" to formatCountdown(it)
            )
        } ?: lang.bedrock("bedrock.bank_automation.status.next_run_pending")
        return listOf(
            status,
            next,
            lang.bedrock("bedrock.bank_automation.status.executing_note")
        ).joinToString("\n")
    }

    private fun formatCountdown(nextRun: Instant): String {
        val seconds = Duration.between(Instant.now(), nextRun).seconds.coerceAtLeast(0)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            "%dh %02dm %02ds".format(hours, minutes, secs)
        } else {
            "%dm %02ds".format(minutes, secs)
        }
    }

    override fun shouldCacheForm(): Boolean = false
    override fun handleResponse(player: Player, response: Any?) = Unit
}
