package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.BudgetCategory
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.logging.Logger
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Bedrock Edition guild budget management menu using Cumulus CustomForm
 * Provides budget allocation and monitoring for Bedrock players
 */
class BedrockGuildBudgetMenu(
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
        val budgetIcon = BedrockFormUtils.createFormImage(config, config.guildBankIconUrl, config.guildBankIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "form.title.guild.budget")} - ${guild.name}")
            .apply { budgetIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.budget.description"))
            .label("<bold><gold>=== Budget Categories ===")
            .apply { addBudgetCategorySection() }
            .validResultHandler { response ->
                handleBudgetUpdates(response)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun CustomForm.Builder.addBudgetCategorySection(): CustomForm.Builder {
        val categories = BudgetCategory.values()

        categories.forEach { category ->
            val budget = bankService.getBudgetByCategory(guild.id, category)

            label("<bold><white>${category.name.replace("_", " ")}")
            label("<gray>Current: <white>${budget?.allocatedAmount ?: 0} coins")
            label("<gray>Spent: <white>${budget?.spentAmount ?: 0} coins")
            label("<gray>Remaining: <white>${budget?.getRemainingAmount() ?: 0} coins")

            val usagePercentage = budget?.getUsagePercentage() ?: 0.0
            val status = when {
                usagePercentage >= 100 -> "<red>OVER BUDGET"
                usagePercentage >= 80 -> "<yellow>NEAR LIMIT"
                else -> "<green>WITHIN LIMIT"
            }
            label("<gray>Status: $status")

            // Budget allocation input
            addLocalizedInput(
                player, bedrockLocalization,
                "guild.budget.allocation",
                budget?.allocatedAmount?.toString() ?: "0"
            )
        }

        return this
    }

    private fun handleBudgetUpdates(response: org.geysermc.cumulus.response.CustomFormResponse) {
        try {
            // Check permissions
            if (!hasBudgetManagementPermission()) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to manage budgets!")
                return
            }

            val categories = BudgetCategory.values()

            categories.forEachIndexed { index, category ->
                val allocation = response.asInput(index)?.toIntOrNull() ?: 0

                if (allocation > 0) {
                    val now = Instant.now()
                    val monthEnd = now.plus(30, ChronoUnit.DAYS)

                    val success = bankService.setBudget(guild.id, category, allocation, now, monthEnd)
                    if (success != null) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Updated ${category.name} budget to $allocation coins")
                    } else {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to update ${category.name} budget")
                    }
                }
            }
        } catch (e: Exception) {
            logger.warning("Error updating guild budgets: ${e.message}")
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Error updating budgets!")
        }
    }

    private fun hasBudgetManagementPermission(): Boolean {
        return memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_BUDGETS)
    }
}

