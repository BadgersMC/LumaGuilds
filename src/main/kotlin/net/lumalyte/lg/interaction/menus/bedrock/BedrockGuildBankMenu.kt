package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
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

    override fun getForm(): Form {
        val playerBalance = bankService.getPlayerBalance(player.uniqueId)
        val guildBalance = bankService.getBalance(guild.id)
        val config = getBedrockConfig()
        val bankIcon = BedrockFormUtils.createFormImage(config, config.guildBankIconUrl, config.guildBankIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "form.title.guild.bank")} - ${guild.name}")
            .apply { bankIcon?.let { icon(it) } }
            .label(createBalanceInfoSection(playerBalance, guildBalance))
            .addLocalizedSlider(
                player, bedrockLocalization,
                "guild.bank.deposit.slider.label",
                0f,
                playerBalance.toFloat(),
                100f,
                0f
            )
            .addLocalizedInput(
                player, bedrockLocalization,
                "guild.bank.deposit.custom.label",
                "guild.bank.deposit.custom.placeholder",
                ""
            )
            .addLocalizedSlider(
                player, bedrockLocalization,
                "guild.bank.withdraw.slider.label",
                0f,
                guildBalance.toFloat(),
                100f,
                0f
            )
            .addLocalizedInput(
                player, bedrockLocalization,
                "guild.bank.withdraw.custom.label",
                "guild.bank.withdraw.custom.placeholder",
                ""
            )
            .addLocalizedToggle(
                player, bedrockLocalization,
                "guild.bank.settings.auto.deposit",
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
        return """
            |${bedrockLocalization.getBedrockString(player, "guild.bank.description")}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.bank.balance.label")}: ${bedrockLocalization.getBedrockString(player, "guild.bank.balance.amount", playerBalance)}
            |Guild Balance: ${bedrockLocalization.getBedrockString(player, "guild.bank.balance.amount", guildBalance)}
            |
            |Use the sliders below to deposit or withdraw money.
        """.trimMargin()
    }

    private fun createValidationInfoSection(): String {
        return """
            |${bedrockLocalization.getBedrockString(player, "form.validation.title")}
            |• ${bedrockLocalization.getBedrockString(player, "validation.invalid.number")}
            |• ${bedrockLocalization.getBedrockString(player, "validation.number.too.small", 0)}
            |• ${bedrockLocalization.getBedrockString(player, "validation.insufficient.funds")}
            |• ${bedrockLocalization.getBedrockString(player, "validation.permission.denied")}
        """.trimMargin()
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

            // Validate permissions
            if (!bankService.canDeposit(player.uniqueId, guild.id)) {
                player.sendMessage("§c[ERROR] ${localize("guild.bank.error.no.deposit.permission")}")
                navigateBack()
                return
            }

            if (!bankService.canWithdraw(player.uniqueId, guild.id)) {
                player.sendMessage("§c[ERROR] ${localize("guild.bank.error.no.withdraw.permission")}")
                navigateBack()
                return
            }

            // Validate amounts
            val validationErrors = mutableListOf<String>()

            if (depositAmount > 0 && depositAmount > playerBalance) {
                validationErrors.add(localize("guild.bank.error.insufficient.player.funds", playerBalance))
            }

            if (withdrawAmount > 0 && withdrawAmount > guildBalance) {
                validationErrors.add(localize("guild.bank.error.insufficient.guild.funds", guildBalance))
            }

            if (depositAmount < 0) {
                validationErrors.add(localize("guild.bank.error.invalid.deposit.amount"))
            }

            if (withdrawAmount < 0) {
                validationErrors.add(localize("guild.bank.error.invalid.withdraw.amount"))
            }

            if (depositAmount > 0 && withdrawAmount > 0) {
                validationErrors.add(localize("guild.bank.error.both.amounts"))
            }

            if (validationErrors.isNotEmpty()) {
                showValidationErrors(validationErrors)
                return
            }

            // Check if any transactions to perform
            if (depositAmount == 0 && withdrawAmount == 0) {
                player.sendMessage("§7${localize("guild.bank.no.transactions")}")
                navigateBack()
                return
            }

            // Show confirmation for transactions
            showTransactionConfirmation(depositAmount, withdrawAmount, autoDepositEnabled)

        } catch (e: Exception) {
            logger.warning("Error processing guild bank form response: ${e.message}")
            player.sendMessage("§c[ERROR] ${localize("form.error.processing")}")
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
        val errorMessage = errors.joinToString("\n") { "• $it" }

        // Send error message and reopen form
        player.sendMessage("§c[ERROR] ${localize("form.validation.errors.title")}")
        player.sendMessage("§7$errorMessage")
        player.sendMessage("§e${localize("form.button.retry")}")
        player.sendMessage("§c${localize("form.button.cancel")}")

        // Reopen the form for retry
        reopen()
    }

    private fun showTransactionConfirmation(depositAmount: Int, withdrawAmount: Int, autoDepositEnabled: Boolean) {
        val config = getBedrockConfig()

        if (config.bedrockMenusEnabled) {
            // Use ModalForm for confirmation
            val confirmationMessage = buildConfirmationMessage(depositAmount, withdrawAmount, autoDepositEnabled)

            val customForm = CustomForm.builder()
                .title(localize("guild.bank.confirmation.title"))
                .label(confirmationMessage)
                .toggle(localize("form.button.confirm"), false)
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
            player.sendMessage("§e${localize("guild.bank.confirmation.title")}")
            player.sendMessage("§7$confirmationMessage")
            player.sendMessage("§a${localize("guild.bank.confirm.instructions")}")

            // For message-based confirmation, execute immediately
            executeTransactions(depositAmount, withdrawAmount, autoDepositEnabled)
        }
    }

    private fun buildConfirmationMessage(depositAmount: Int, withdrawAmount: Int, autoDepositEnabled: Boolean): String {
        val messages = mutableListOf<String>()

        if (depositAmount > 0) {
            messages.add(localize("guild.bank.confirm.deposit", depositAmount))
        }

        if (withdrawAmount > 0) {
            messages.add(localize("guild.bank.confirm.withdraw", withdrawAmount))
        }

        if (autoDepositEnabled) {
            messages.add(localize("guild.bank.confirm.auto.deposit"))
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
                changes.add(localize("guild.bank.success.deposit", depositAmount))
            } else {
                allSuccessful = false
                player.sendMessage("§c[ERROR] ${localize("guild.bank.error.deposit.failed")}")
            }
        }

        // Execute withdrawal
        if (withdrawAmount > 0) {
            val transaction = bankService.withdraw(guild.id, player.uniqueId, withdrawAmount)
            if (transaction != null) {
                changes.add(localize("guild.bank.success.withdraw", withdrawAmount))
            } else {
                allSuccessful = false
                player.sendMessage("§c[ERROR] ${localize("guild.bank.error.withdraw.failed")}")
            }
        }

        // Handle auto-deposit setting (placeholder)
        if (autoDepositEnabled) {
            // TODO: Implement auto-deposit setting
            changes.add(localize("guild.bank.success.auto.deposit.enabled"))
        }

        // Show results
        if (changes.isNotEmpty()) {
            if (allSuccessful) {
                player.sendMessage("§a✅ ${localize("guild.bank.success.title")}")
                changes.forEach { player.sendMessage("§7• $it") }
            } else {
                player.sendMessage("§e[WARNING] ${localize("guild.bank.partial.success")}")
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
