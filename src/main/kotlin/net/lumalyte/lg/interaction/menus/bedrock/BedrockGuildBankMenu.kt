package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.BankSettingsRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.BankWithdrawalResult
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.BankSettings
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.ModalForm
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild bank menu using Cumulus CustomForm
 * Provides advanced banking interface with sliders and validation
 */
class BedrockGuildBankMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val bankService: BankService by inject()
    private val configService: ConfigService by inject()
    private val guildService: GuildService by inject()
    private val authorization by lazy { BedrockGuildAuthorization(guildService) }
    private val physicalCurrencyService: PhysicalCurrencyService by inject()
    private val bankSettingsRepository: BankSettingsRepository by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        // Use physical currency from inventory if enabled, otherwise use Vault balance
        val playerBalance = if (physicalCurrencyService.isPhysicalCurrencyEnabled()) {
            physicalCurrencyService.calculatePlayerInventoryValue(player.uniqueId)
        } else {
            bankService.getPlayerBalance(player.uniqueId)
        }
        val guildBalance = bankService.getBalance(guild.id)
        val autoDepositEnabled = (bankSettingsRepository.getByGuildId(guild.id) ?: BankSettings(guild.id))
            .scheduledDepositsEnabled
        val config = getBedrockConfig()
        val bankIcon = BedrockFormUtils.createFormImage(config, config.guildBankIconUrl, config.guildBankIconPath)
        val canManageBankSettings = authorization.canManageBankSettings(player.uniqueId, guild.id)
        val navigationOptions = mutableListOf(
            lang.bedrock("bedrock.bank.navigation.transactions"),
            lang.bedrock("bedrock.bank.navigation.history"),
            lang.bedrock("bedrock.bank.navigation.statistics"),
            lang.bedrock("bedrock.bank.navigation.contributions"),
        ).apply {
            if (canManageBankSettings) {
                add(lang.bedrock("bedrock.bank.navigation.automation"))
                add(lang.bedrock("bedrock.bank.navigation.budget"))
                add(lang.bedrock("bedrock.bank.navigation.security"))
            }
        }

        val builder = CustomForm.builder()
            .title(lang.bedrock("bedrock.bank.title", "guild" to guild.name))
            .apply { bankIcon?.let { icon(it) } }
            .label(createBalanceInfoSection(playerBalance, guildBalance))
            .dropdown(
                lang.bedrock("bedrock.bank.navigation.label"),
                navigationOptions,
                0
            )
            .slider(
                lang.bedrock("bedrock.bank.deposit.slider"),
                0f,
                playerBalance.toFloat(),
                100f,
                0f
            )
            .input(
                lang.bedrock("bedrock.bank.deposit.custom_label"),
                lang.bedrock("bedrock.bank.deposit.custom_placeholder"),
                ""
            )
            .slider(
                lang.bedrock("bedrock.bank.withdraw.slider"),
                0f,
                guildBalance.toFloat(),
                100f,
                0f
            )
            .input(
                lang.bedrock("bedrock.bank.withdraw.custom_label"),
                lang.bedrock("bedrock.bank.withdraw.custom_placeholder"),
                ""
            )

        if (canManageBankSettings) {
            builder.label(lang.bedrock("bedrock.bank.navigation.management"))
            builder.toggle(lang.bedrock("bedrock.bank.auto_deposit"), autoDepositEnabled)
        } else {
            val autoDepositState = if (autoDepositEnabled) {
                lang.bedrock("bedrock.bank.management.enabled")
            } else {
                lang.bedrock("bedrock.bank.management.disabled")
            }
            builder.label(
                lang.bedrock(
                    "bedrock.bank.management.auto_deposit_read_only",
                    "state" to autoDepositState
                )
            )
        }

        return builder
            .label(createValidationInfoSection())
            .validResultHandler { response ->
                handleFormResponse(
                    response,
                    playerBalance,
                    guildBalance,
                    autoDepositEnabled,
                    canManageBankSettings
                )
            }
            .closedOrInvalidResultHandler { _, _ ->
                navigateBack()
            }
            .build()
    }

    private fun createBalanceInfoSection(playerBalance: Int, guildBalance: Int): String {
        return lang.bedrock(
            "bedrock.bank.balance_section",
            "player_balance" to playerBalance,
            "guild_balance" to guildBalance
        )
    }

    private fun createValidationInfoSection(): String {
        return lang.bedrock("bedrock.bank.validation_section")
    }

    private fun handleFormResponse(
        response: org.geysermc.cumulus.response.CustomFormResponse,
        playerBalance: Int,
        guildBalance: Int,
        currentAutoDepositEnabled: Boolean,
        canManageBankSettings: Boolean
    ) {
        try {
            onFormResponseReceived()

            val navigationSelection = response.next() as? Int ?: 0
            if (navigationSelection != 0) {
                navigateToBankSection(navigationSelection)
                return
            }

            val depositSliderValue = response.next() as? Float ?: 0f
            val depositInputValue = response.next() as? String ?: ""
            val withdrawSliderValue = response.next() as? Float ?: 0f
            val withdrawInputValue = response.next() as? String ?: ""
            val autoDepositEnabled = if (canManageBankSettings) {
                response.next() as? Boolean ?: currentAutoDepositEnabled
            } else {
                currentAutoDepositEnabled
            }

            // Parse amounts
            val depositAmount = parseAmount(depositInputValue, depositSliderValue, playerBalance, true)
            val withdrawAmount = parseAmount(withdrawInputValue, withdrawSliderValue, guildBalance, false)

            // Validate permissions - only check permissions for actions being performed
            if (depositAmount > 0 && !physicalCurrencyService.isPhysicalCurrencyEnabled() &&
                !bankService.canDeposit(player.uniqueId, guild.id)) {
                player.sendMessage(lang.msg("bedrock.bank.error.no_deposit_permission"))
                navigateBack()
                return
            }

            if (withdrawAmount > 0 && !bankService.canWithdraw(player.uniqueId, guild.id)) {
                player.sendMessage(lang.msg("bedrock.bank.error.no_withdraw_permission"))
                navigateBack()
                return
            }

            // Validate amounts
            val validationErrors = mutableListOf<String>()

            if (depositAmount > 0 && depositAmount > playerBalance) {
                validationErrors.add(lang.bedrock("bedrock.bank.error.insufficient_player_funds", "balance" to playerBalance))
            }

            if (withdrawAmount > 0 && withdrawAmount > guildBalance) {
                validationErrors.add(lang.bedrock("bedrock.bank.error.insufficient_guild_funds", "balance" to guildBalance))
            }

            if (depositAmount < 0) {
                validationErrors.add(lang.bedrock("bedrock.bank.error.invalid_deposit"))
            }

            if (withdrawAmount < 0) {
                validationErrors.add(lang.bedrock("bedrock.bank.error.invalid_withdraw"))
            }

            if (depositAmount > 0 && withdrawAmount > 0) {
                validationErrors.add(lang.bedrock("bedrock.bank.error.both_amounts"))
            }

            if (validationErrors.isNotEmpty()) {
                showValidationErrors(validationErrors)
                return
            }

            // Check if any transactions to perform
            if (depositAmount == 0 && withdrawAmount == 0) {
                if (autoDepositEnabled != currentAutoDepositEnabled) {
                    saveAutoDeposit(autoDepositEnabled)
                } else {
                    player.sendMessage(lang.msg("bedrock.bank.feedback.no_transactions"))
                }
                navigateBack()
                return
            }

            // Show confirmation for transactions
            showTransactionConfirmation(depositAmount, withdrawAmount, autoDepositEnabled, currentAutoDepositEnabled)

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error processing guild bank form response: ${e.message}")
            player.sendMessage(lang.msg("bedrock.bank.error.processing"))
            navigateBack()
        }
    }

    private fun navigateToBankSection(selection: Int) {
        val target = when (selection) {
            1 -> menuFactory.createGuildBankTransactionHistoryMenu(menuNavigator, player, guild)
            2 -> menuFactory.createGuildBankStatisticsMenu(menuNavigator, player, guild)
            3 -> menuFactory.createGuildMemberContributionsMenu(menuNavigator, player, guild)
            4 -> if (authorization.canManageBankSettings(player.uniqueId, guild.id)) {
                menuFactory.createGuildBankAutomationMenu(menuNavigator, player, guild)
            } else null
            5 -> if (authorization.canManageBankSettings(player.uniqueId, guild.id)) {
                menuFactory.createGuildBankBudgetMenu(menuNavigator, player, guild)
            } else null
            6 -> if (authorization.canManageBankSettings(player.uniqueId, guild.id)) {
                menuFactory.createGuildBankSecurityMenu(menuNavigator, player, guild)
            } else null
            else -> null
        }

        if (target != null) {
            bedrockNavigator.openMenu(target)
        } else {
            player.sendMessage(lang.msg("bedrock.bank.management.no_permission"))
            reopen()
        }
    }

    private fun parseAmount(inputValue: String, sliderValue: Float, maxValue: Int, isDeposit: Boolean): Int {
        // If input has value, use it; otherwise use slider
        val inputAmount = if (inputValue.isNotBlank()) {
            inputValue.toIntOrNull()
        } else {
            null
        }

        return when {
            inputAmount != null -> inputAmount
            sliderValue > 0 -> sliderValue.toInt()
            else -> 0
        }
    }

    private fun showValidationErrors(errors: List<String>) {
        val errorMessage = errors.joinToString("\n") { lang.bedrock("bedrock.bank.validation.row", "error" to it) }

        // Send error message and reopen form
        player.sendMessage(lang.msg("bedrock.bank.validation.title"))
        player.sendMessage(lang.msg("bedrock.bank.validation.errors", "errors" to errorMessage))
        player.sendMessage(lang.msg("bedrock.bank.validation.retry"))
        player.sendMessage(lang.msg("bedrock.bank.validation.cancel"))

        // Reopen the form for retry
        reopen()
    }

    private fun showTransactionConfirmation(
        depositAmount: Int,
        withdrawAmount: Int,
        autoDepositEnabled: Boolean,
        currentAutoDepositEnabled: Boolean
    ) {
        val config = getBedrockConfig()

        if (config.bedrockMenusEnabled) {
            // Use ModalForm for confirmation
            val confirmationMessage = buildConfirmationMessage(depositAmount, withdrawAmount, autoDepositEnabled)

            val customForm = CustomForm.builder()
                .title(lang.bedrock("bedrock.bank.confirmation.title"))
                .label(confirmationMessage)
                .toggle(lang.bedrock("bedrock.bank.confirmation.confirm"), false)
                .validResultHandler { response ->
                    val confirm = response.next() as? Boolean ?: false
                    if (confirm) {
                        executeTransactions(depositAmount, withdrawAmount, autoDepositEnabled, currentAutoDepositEnabled)
                    } else {
                        navigateBack()
                    }
                }
                .closedOrInvalidResultHandler { _, _ ->
                    navigateBack()
                }
                .build()

            val floodgateApi = org.geysermc.floodgate.api.FloodgateApi.getInstance()
            floodgateApi.sendForm(player.uniqueId, customForm)
        } else {
            // Fallback to message confirmation
            val confirmationMessage = buildConfirmationMessage(depositAmount, withdrawAmount, autoDepositEnabled)
            player.sendMessage(lang.msg("bedrock.bank.confirmation.title_message"))
            player.sendMessage(lang.msg("bedrock.bank.confirmation.details", "details" to confirmationMessage))
            player.sendMessage(lang.msg("bedrock.bank.confirmation.instructions"))

            // For message-based confirmation, execute immediately
            executeTransactions(depositAmount, withdrawAmount, autoDepositEnabled, currentAutoDepositEnabled)
        }
    }

    private fun buildConfirmationMessage(depositAmount: Int, withdrawAmount: Int, autoDepositEnabled: Boolean): String {
        val messages = mutableListOf<String>()

        if (depositAmount > 0) {
            messages.add(lang.bedrock("bedrock.bank.confirmation.deposit", "amount" to depositAmount))
        }

        if (withdrawAmount > 0) {
            messages.add(lang.bedrock("bedrock.bank.confirmation.withdraw", "amount" to withdrawAmount))
        }

        if (autoDepositEnabled) {
            messages.add(lang.bedrock("bedrock.bank.confirmation.auto_deposit"))
        }

        return messages.joinToString("\n")
    }

    private fun executeTransactions(
        depositAmount: Int,
        withdrawAmount: Int,
        autoDepositEnabled: Boolean,
        currentAutoDepositEnabled: Boolean
    ) {
        val changes = mutableListOf<String>()
        var allSuccessful = true

        // Execute deposit
        if (depositAmount > 0) {
            val deposited = if (physicalCurrencyService.isPhysicalCurrencyEnabled()) {
                bankService.depositPhysical(
                    net.lumalyte.lg.application.services.PhysicalGoldRequest(
                        java.util.UUID.randomUUID(),
                        guild.id,
                        player.uniqueId,
                        depositAmount.toLong(),
                        "Bedrock guild bank physical deposit",
                    )
                ) is net.lumalyte.lg.domain.gold.GuildGoldResult.Applied
            } else {
                bankService.deposit(guild.id, player.uniqueId, depositAmount) != null
            }
            if (deposited) {
                changes.add(lang.bedrock("bedrock.bank.success.deposit", "amount" to depositAmount))
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("bedrock.bank.error.deposit_failed"))
            }
        }

        // Execute withdrawal
        if (withdrawAmount > 0) {
            val outcome = bankService.withdrawOutcome(guild.id, player.uniqueId, withdrawAmount)
            if (outcome is BankWithdrawalResult.Completed) {
                changes.add(lang.bedrock("bedrock.bank.success.withdraw", "amount" to withdrawAmount))
            } else if (outcome is BankWithdrawalResult.Ambiguous) {
                allSuccessful = false
                player.sendMessage(lang.msg("menu.bank.feedback.withdraw_pending", "transaction" to outcome.transactionId))
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("bedrock.bank.error.withdraw_failed"))
            }
        }

        if (autoDepositEnabled != currentAutoDepositEnabled) {
            if (bankSettingsEditor().saveAutoDeposit(guild.id, autoDepositEnabled)) {
                if (autoDepositEnabled) {
                    changes.add(lang.bedrock("bedrock.bank.success.auto_deposit_enabled"))
                } else {
                    changes.add(lang.bedrock("bedrock.bank.success.auto_deposit_disabled"))
                }
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("bedrock.bank.error.auto_deposit_failed"))
            }
        }

        // Show results
        if (changes.isNotEmpty()) {
            if (allSuccessful) {
                player.sendMessage(lang.msg("bedrock.bank.success.title"))
                changes.forEach { player.sendMessage(lang.msg("bedrock.bank.success.row", "change" to it)) }
            } else {
                player.sendMessage(lang.msg("bedrock.bank.success.partial"))
            }
        }

        navigateBack()
    }

    private fun saveAutoDeposit(enabled: Boolean) {
        if (bankSettingsEditor().saveAutoDeposit(guild.id, enabled)) {
            if (enabled) {
                player.sendMessage(lang.msg("bedrock.bank.success.auto_deposit_enabled"))
            } else {
                player.sendMessage(lang.msg("bedrock.bank.success.auto_deposit_disabled"))
            }
        } else {
            player.sendMessage(lang.msg("bedrock.bank.error.auto_deposit_failed"))
        }
    }

    private fun bankSettingsEditor(): BedrockBankSettingsEditor =
        BedrockBankSettingsEditor(bankSettingsRepository) { targetGuildId ->
            authorization.canManageBankSettings(player.uniqueId, targetGuildId)
        }

    override fun shouldCacheForm(): Boolean = false

    override fun createCacheKey(): String {
        return "${this::class.simpleName}:${player.uniqueId}:${guild.id}"
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
        onFormResponseReceived()
    }
}
