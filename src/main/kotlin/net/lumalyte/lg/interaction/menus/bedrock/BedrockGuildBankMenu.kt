package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.domain.entities.Guild
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
    private val physicalCurrencyService: PhysicalCurrencyService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        // Use physical currency from inventory if enabled, otherwise use Vault balance
        val playerBalance = if (physicalCurrencyService.isPhysicalCurrencyEnabled()) {
            physicalCurrencyService.calculatePlayerInventoryValue(player.uniqueId)
        } else {
            bankService.getPlayerBalance(player.uniqueId)
        }
        val guildBalance = bankService.getBalance(guild.id)
        val config = getBedrockConfig()
        val bankIcon = BedrockFormUtils.createFormImage(config, config.guildBankIconUrl, config.guildBankIconPath)

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.bank.title", "guild" to guild.name))
            .apply { bankIcon?.let { icon(it) } }
            .label(createBalanceInfoSection(playerBalance, guildBalance))
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
            .toggle(
                lang.bedrock("bedrock.bank.auto_deposit"),
                false // TODO: Get current auto-deposit setting
            )
            .label(createValidationInfoSection())
            .validResultHandler { response ->
                handleFormResponse(response, playerBalance, guildBalance)
            }
            .closedOrInvalidResultHandler { _, _ ->
                // Handle form closed without submission
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
        guildBalance: Int
    ) {
        try {
            onFormResponseReceived()

            val depositSliderValue = response.next() as? Float ?: 0f
            val depositInputValue = response.next() as? String ?: ""
            val withdrawSliderValue = response.next() as? Float ?: 0f
            val withdrawInputValue = response.next() as? String ?: ""
            val autoDepositEnabled = response.next() as? Boolean ?: false

            // Parse amounts
            val depositAmount = parseAmount(depositInputValue, depositSliderValue, playerBalance, true)
            val withdrawAmount = parseAmount(withdrawInputValue, withdrawSliderValue, guildBalance, false)

            // Validate permissions - only check permissions for actions being performed
            if (depositAmount > 0 && !bankService.canDeposit(player.uniqueId, guild.id)) {
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
                player.sendMessage(lang.msg("bedrock.bank.feedback.no_transactions"))
                navigateBack()
                return
            }

            // Show confirmation for transactions
            showTransactionConfirmation(depositAmount, withdrawAmount, autoDepositEnabled)

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error processing guild bank form response: ${e.message}")
            player.sendMessage(lang.msg("bedrock.bank.error.processing"))
            navigateBack()
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

    private fun showTransactionConfirmation(depositAmount: Int, withdrawAmount: Int, autoDepositEnabled: Boolean) {
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
                        executeTransactions(depositAmount, withdrawAmount, autoDepositEnabled)
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
            executeTransactions(depositAmount, withdrawAmount, autoDepositEnabled)
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

    private fun executeTransactions(depositAmount: Int, withdrawAmount: Int, autoDepositEnabled: Boolean) {
        val changes = mutableListOf<String>()
        var allSuccessful = true

        // Execute deposit
        if (depositAmount > 0) {
            val transaction = bankService.deposit(guild.id, player.uniqueId, depositAmount)
            if (transaction != null) {
                changes.add(lang.bedrock("bedrock.bank.success.deposit", "amount" to depositAmount))
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("bedrock.bank.error.deposit_failed"))
            }
        }

        // Execute withdrawal
        if (withdrawAmount > 0) {
            val transaction = bankService.withdraw(guild.id, player.uniqueId, withdrawAmount)
            if (transaction != null) {
                changes.add(lang.bedrock("bedrock.bank.success.withdraw", "amount" to withdrawAmount))
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("bedrock.bank.error.withdraw_failed"))
            }
        }

        // Handle auto-deposit setting (placeholder)
        if (autoDepositEnabled) {
            // TODO: Implement auto-deposit setting
            changes.add(lang.bedrock("bedrock.bank.success.auto_deposit_enabled"))
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

    override fun shouldCacheForm(): Boolean = true

    override fun createCacheKey(): String {
        return "${this::class.simpleName}:${player.uniqueId}:${guild.id}"
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
        onFormResponseReceived()
    }
}
