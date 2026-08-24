package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Bedrock Edition guild war management menu using Cumulus SimpleForm and CustomForm
 * Provides comprehensive war declaration, management, and tracking interface
 */
class BedrockGuildWarManagementMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger), KoinComponent {

    private val warService: WarService by inject()
    private val guildService: GuildService by inject()
    private val bankService: BankService by inject()
    private val memberService: MemberService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val canManageWars = warService.canPlayerManageWars(player.uniqueId, guild.id)

        return SimpleForm.builder()
            .title("${lang.bedrock("bedrock.war_management.management_title")} - ${guild.name}")
            .content("""
                |${lang.bedrock("bedrock.war_management.management_welcome")}
                |
                |${lang.bedrock("bedrock.war_management.management_description")}
                |
                |${lang.bedrock("bedrock.relations_management.notice")}
            """.trimMargin())
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.war_management.management_active_wars"),
                config.guildWarsIconUrl,
                config.guildWarsIconPath
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.war_management.management_war_declarations"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.war_management.management_declare_war"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.war_management.management_war_history"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.war_management.management_war_statistics"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.relations_management.back"),
                config.closeIconUrl,
                config.closeIconPath
            )
            .validResultHandler { response ->
                handleMainMenuResponse(response.clickedButtonId())
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun handleMainMenuResponse(buttonId: Int) {
        when (buttonId) {
            0 -> openActiveWarsMenu()
            1 -> openWarDeclarationsMenu()
            2 -> openDeclareWarMenu()
            3 -> openWarHistoryMenu()
            4 -> openWarStatisticsMenu()
            5 -> bedrockNavigator.goBack()
        }
    }

    private fun openActiveWarsMenu() {
        val config = getBedrockConfig()
        val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }

        val form = SimpleForm.builder()
            .title(lang.bedrock("bedrock.war_management.management_active_wars"))
            .content(
                if (activeWars.isEmpty()) {
                    lang.bedrock("bedrock.war_management.management_no_active_wars")
                } else {
                    "${lang.bedrock("bedrock.war_management.management_select_war")}\n\n${lang.bedrock("bedrock.war_management.management_active_wars_count", "count" to activeWars.size)}"
                }
            )

        activeWars.forEach { war ->
            val opponentGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val opponentGuild = guildService.getGuild(opponentGuildId)
            val opponentName = opponentGuild?.name ?: lang.bedrock("bedrock.war_management.management_unknown_guild")

            val startedDate = war.startedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) ?: "Unknown"
            val remainingDays = war.remainingDuration?.toDays() ?: 0

            form.addButtonWithImage(
                config,
                    "⚔ vs $opponentName\n${lang.bedrock("bedrock.war_management.management_started")}: $startedDate\n${lang.bedrock("bedrock.war_management.management_remaining_days", "days" to remainingDays)}",
                config.guildWarsIconUrl,
                config.guildWarsIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton < activeWars.size) {
                    openWarDetailsMenu(activeWars[clickedButton])
                } else {
                    getForm() // Back to main menu
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }
            .build()

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
            override fun getForm(): Form = formWithHandler

            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun openWarDeclarationsMenu() {
        val config = getBedrockConfig()
        val incomingDeclarations = warService.getPendingDeclarationsForGuild(guild.id)
        val outgoingDeclarations = warService.getDeclarationsByGuild(guild.id).filter { it.isValid }

        val form = SimpleForm.builder()
            .title(lang.bedrock("bedrock.war_management.management_war_declarations"))
            .content("""
                |${lang.bedrock("bedrock.war_management.management_declarations_incoming")}: ${incomingDeclarations.size}
                |${lang.bedrock("bedrock.war_management.management_declarations_outgoing")}: ${outgoingDeclarations.size}
            """.trimMargin())

        if (incomingDeclarations.isNotEmpty()) {
            form.addButtonWithImage(
                config,
                lang.bedrock("bedrock.war_management.management_view_incoming_declarations"),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
        }

        if (outgoingDeclarations.isNotEmpty()) {
            form.addButtonWithImage(
                config,
                lang.bedrock("bedrock.war_management.management_view_outgoing_declarations"),
                config.editIconUrl,
                config.editIconPath
            )
        }

        if (incomingDeclarations.isEmpty() && outgoingDeclarations.isEmpty()) {
            form.content(lang.bedrock("bedrock.war_management.management_no_declarations"))
        }

        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                val buttonIndex = when {
                    incomingDeclarations.isNotEmpty() && outgoingDeclarations.isNotEmpty() -> clickedButton
                    incomingDeclarations.isNotEmpty() || outgoingDeclarations.isNotEmpty() -> clickedButton
                    else -> -1
                }

                when {
                    incomingDeclarations.isNotEmpty() && clickedButton == 0 -> openIncomingDeclarationsMenu()
                    outgoingDeclarations.isNotEmpty() && clickedButton == (if (incomingDeclarations.isNotEmpty()) 1 else 0) -> openOutgoingDeclarationsMenu()
                    clickedButton == maxOf(if (incomingDeclarations.isNotEmpty()) 1 else 0, if (outgoingDeclarations.isNotEmpty()) 1 else 0) -> getForm() // Back
                    else -> getForm() // Back to main menu
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }
            .build()

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
            override fun getForm(): Form = formWithHandler

            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun openDeclareWarMenu() {
        val config = getBedrockConfig()

        val form = CustomForm.builder()
            .title(lang.bedrock("bedrock.war_management.management_declare_war"))
            .label(lang.bedrock("bedrock.war_management.management_declare_war_description"))
            .input(
                lang.bedrock("bedrock.war_management.management_declare_war_target_label"),
                lang.bedrock("bedrock.war_management.management_declare_war_target_placeholder")
            )
            .input(
                lang.bedrock("bedrock.war_management.management_declare_war_duration_label"),
                lang.bedrock("bedrock.war_management.management_declare_war_duration_placeholder")
            )
            .input(
                lang.bedrock("bedrock.war_management.management_declare_war_reason_label"),
                lang.bedrock("bedrock.war_management.management_declare_war_reason_placeholder")
            )
            .validResultHandler { response ->
                handleDeclareWarResponse(response)
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }
            .build()

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
            override fun getForm(): Form = form

            override fun handleResponse(player: Player, response: Any?) {
                onFormResponseReceived()
            }
        })
    }

    private fun handleDeclareWarResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        val targetGuildName = response.asInput(0)
        val durationStr = response.asInput(1) ?: "7"
        val reason = response.asInput(2)

        if (targetGuildName.isNullOrBlank()) {
            player.sendMessage(lang.msg("bedrock.war_management.management_declare_war_target_required"))
            return
        }

        val targetGuild = guildService.getGuildByName(targetGuildName)
        if (targetGuild == null) {
            player.sendMessage(lang.msg("bedrock.war_management.management_declare_war_guild_not_found"))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(lang.msg("bedrock.war_management.management_declare_war_self"))
            return
        }

        val duration = try {
            Duration.ofDays(durationStr.toLong().coerceIn(1, 30))
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            Duration.ofDays(7)
        }

        val success = warService.createWarDeclaration(
            declaringGuildId = guild.id,
            defendingGuildId = targetGuild.id,
            duration = duration,
            objectives = emptySet(),
            wagerAmount = 0,
            terms = null,
            actorId = player.uniqueId
        )
        if (success != null) {
            player.sendMessage(lang.msg("bedrock.war_management.management_declare_war_declared", "guild" to targetGuild.name))
        } else {
            player.sendMessage(lang.msg("bedrock.war_management.management_declare_war_failed"))
        }

        getForm() // Refresh main menu
    }

    private fun openWarHistoryMenu() {
        val config = getBedrockConfig()
        val warHistory = warService.getWarHistory(guild.id, 10)

        val form = SimpleForm.builder()
            .title(lang.bedrock("bedrock.war_management.management_war_history"))
            .content(
                if (warHistory.isEmpty()) {
                    lang.bedrock("bedrock.war_management.management_no_war_history")
                } else {
                    "${lang.bedrock("bedrock.war_management.management_select_war")}\n\n${lang.bedrock("bedrock.war_management.management_history_count", "count" to warHistory.size)}"
                }
            )

        warHistory.forEach { war ->
            val opponentGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val opponentGuild = guildService.getGuild(opponentGuildId)
            val opponentName = opponentGuild?.name ?: lang.bedrock("bedrock.war_management.management_unknown_guild")
            val result = if (war.winner == guild.id) "✅ ${lang.bedrock("bedrock.war_management.management_won")}" else "❌ ${lang.bedrock("bedrock.war_management.management_lost")}"

            val endedDate = war.endedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) ?: "Ongoing"

            form.addButtonWithImage(
                config,
                "⚔ vs $opponentName - $result\n${lang.bedrock("bedrock.war_management.management_ended")}: $endedDate",
                config.editIconUrl,
                config.editIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton < warHistory.size) {
                    openWarDetailsMenu(warHistory[clickedButton])
                } else {
                    getForm() // Back to main menu
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }
            .build()

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
            override fun getForm(): Form = formWithHandler

            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun openWarStatisticsMenu() {
        val winLossRatio = warService.getWinLossRatio(guild.id)
        val warHistory = warService.getWarHistory(guild.id, 50)
        val wins = warHistory.count { it.winner == guild.id }
        val losses = warHistory.size - wins

        val form = SimpleForm.builder()
            .title(lang.bedrock("bedrock.war_management.management_war_statistics"))
            .content("""
                |${lang.bedrock("bedrock.war_management.management_statistics_wars_won")}: $wins
                |${lang.bedrock("bedrock.war_management.management_statistics_wars_lost")}: $losses
                |${lang.bedrock("bedrock.war_management.management_statistics_win_rate")}: ${String.format("%.1f", winLossRatio * 100)}%
                |${lang.bedrock("bedrock.war_management.management_statistics_total_wars")}: ${warHistory.size}
            """.trimMargin())
            .addButtonWithImage(
                getBedrockConfig(),
                lang.bedrock("bedrock.relations_management.back"),
                getBedrockConfig().closeIconUrl,
                getBedrockConfig().closeIconPath
            )
            .validResultHandler { _ ->
                getForm() // Back to main menu
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
            override fun getForm(): Form = form.build()

            override fun handleResponse(player: Player, response: Any?) {
                onFormResponseReceived()
            }
        })
    }

    private fun openWarDetailsMenu(war: net.lumalyte.lg.domain.entities.War) {
        val opponentGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
        val opponentGuild = guildService.getGuild(opponentGuildId)
        val opponentName = opponentGuild?.name ?: lang.bedrock("bedrock.war_management.management_unknown_guild")
        val isWinner = war.winner == guild.id

        val status = when {
            war.winner != null -> if (isWinner) lang.bedrock("bedrock.war_management.management_won") else lang.bedrock("bedrock.war_management.management_lost")
            war.endedAt != null -> lang.bedrock("bedrock.war_management.management_draw")
            else -> lang.bedrock("bedrock.war_management.management_active")
        }

        val form = SimpleForm.builder()
            .title("${lang.bedrock("bedrock.war_management.management_war_details")} - $opponentName")
            .content("""
                |${lang.bedrock("bedrock.war_management.management_opponent")}: $opponentName
                |${lang.bedrock("bedrock.war_management.management_started")}: ${war.startedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")) ?: "Not Started"}
                |${lang.bedrock("bedrock.war_management.management_ended")}: ${war.endedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")) ?: lang.bedrock("bedrock.war_management.management_ongoing")}
                |${lang.bedrock("bedrock.war_management.management_result")}: $status
                |${lang.bedrock("bedrock.war_management.management_duration")}: ${war.duration.toDays()} days
            """.trimMargin())
            .addButtonWithImage(
                getBedrockConfig(),
                lang.bedrock("bedrock.relations_management.back"),
                getBedrockConfig().closeIconUrl,
                getBedrockConfig().closeIconPath
            )
            .validResultHandler { _ ->
                getForm() // Back to main menu
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
            override fun getForm(): Form = form.build()

            override fun handleResponse(player: Player, response: Any?) {
                onFormResponseReceived()
            }
        })
    }

    private fun openIncomingDeclarationsMenu() {
        val config = getBedrockConfig()
        val incomingDeclarations = warService.getPendingDeclarationsForGuild(guild.id)

        val form = SimpleForm.builder()
            .title(lang.bedrock("bedrock.war_management.management_incoming_declarations"))
            .content(lang.bedrock("bedrock.war_management.management_select_declaration"))

        incomingDeclarations.forEach { declaration ->
            val declaringGuild = guildService.getGuild(declaration.declaringGuildId)
            val declaringName = declaringGuild?.name ?: lang.bedrock("bedrock.war_management.management_unknown_guild")

            form.addButtonWithImage(
                config,
                "${lang.bedrock("bedrock.war_management.management_from")}: $declaringName\n${lang.bedrock("bedrock.war_management.management_duration")}: ${declaration.proposedDuration.toDays()} days",
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton < incomingDeclarations.size) {
                    openDeclarationActionMenu(incomingDeclarations.elementAt(clickedButton), true)
                } else {
                    openWarDeclarationsMenu() // Back to declarations menu
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                openWarDeclarationsMenu() // Back to declarations menu
            }
            .build()

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
            override fun getForm(): Form = formWithHandler

            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun openOutgoingDeclarationsMenu() {
        val config = getBedrockConfig()
        val outgoingDeclarations = warService.getDeclarationsByGuild(guild.id).filter { it.isValid }

        val form = SimpleForm.builder()
            .title(lang.bedrock("bedrock.war_management.management_outgoing_declarations"))
            .content(lang.bedrock("bedrock.war_management.management_select_declaration"))

        outgoingDeclarations.forEach { declaration ->
            val targetGuild = guildService.getGuild(declaration.defendingGuildId)
            val targetName = targetGuild?.name ?: lang.bedrock("bedrock.war_management.management_unknown_guild")

            form.addButtonWithImage(
                config,
                "${lang.bedrock("bedrock.war_management.management_to")}: $targetName\n${lang.bedrock("bedrock.war_management.management_duration")}: ${declaration.proposedDuration.toDays()} days",
                config.editIconUrl,
                config.editIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton < outgoingDeclarations.size) {
                    openDeclarationActionMenu(outgoingDeclarations.elementAt(clickedButton), false)
                } else {
                    openWarDeclarationsMenu() // Back to declarations menu
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                openWarDeclarationsMenu() // Back to declarations menu
            }
            .build()

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
            override fun getForm(): Form = formWithHandler

            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun openDeclarationActionMenu(declaration: Any, isIncoming: Boolean) {
        val config = getBedrockConfig()
        val warDeclaration = declaration as net.lumalyte.lg.domain.entities.WarDeclaration

        val contentBuilder = StringBuilder()
        contentBuilder.append(lang.bedrock("bedrock.war_management.management_declaration_action_description"))
        contentBuilder.append("\n\n")
        contentBuilder.append(lang.bedrock("bedrock.war_management.declaration_duration", "days" to warDeclaration.proposedDuration.toDays()) + "\n")
        contentBuilder.append(lang.bedrock("bedrock.war_management.declaration_objectives", "count" to warDeclaration.objectives.size) + "\n")

        if (warDeclaration.wagerAmount > 0) {
            contentBuilder.append("\n${lang.bedrock("bedrock.war_management.wager_info_header")}\n")
                contentBuilder.append(lang.bedrock("bedrock.war_management.wager_info_amount", "amount" to warDeclaration.wagerAmount) + "\n")
            if (isIncoming) {
                    contentBuilder.append(lang.bedrock("bedrock.war_management.wager_info_must_match", "amount" to warDeclaration.wagerAmount) + "\n")
                    contentBuilder.append(lang.bedrock("bedrock.war_management.wager_info_total_pot", "amount" to warDeclaration.wagerAmount * 2) + "\n")
                contentBuilder.append(lang.bedrock("bedrock.war_management.wager_info_winner_takes_all") + "\n")
            } else {
                contentBuilder.append(lang.bedrock("bedrock.war_management.wager_info_awaiting_match") + "\n")
            }
        }

        if (warDeclaration.terms != null) {
            contentBuilder.append("\n" + lang.bedrock("bedrock.war_management.declaration_terms", "terms" to warDeclaration.terms) + "\n")
        }

        val form = SimpleForm.builder()
            .title(if (isIncoming) lang.bedrock("bedrock.war_management.management_declaration_action_incoming") else lang.bedrock("bedrock.war_management.management_declaration_action_outgoing"))
            .content(contentBuilder.toString())

        if (isIncoming) {
            form.addButtonWithImage(
                config,
                lang.bedrock("bedrock.war_management.management_declaration_accept"),
                config.confirmIconUrl,
                config.confirmIconPath
            )
            form.addButtonWithImage(
                config,
                lang.bedrock("bedrock.war_management.management_declaration_reject"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
        } else {
            form.addButtonWithImage(
                config,
                lang.bedrock("bedrock.war_management.management_declaration_cancel"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                when {
                    isIncoming && clickedButton == 0 -> {
                        // Accept declaration
                        acceptWarDeclaration(declaration as net.lumalyte.lg.domain.entities.WarDeclaration)
                    }
                    isIncoming && clickedButton == 1 -> {
                        // Reject declaration
                        val success = warService.rejectWarDeclaration((declaration as net.lumalyte.lg.domain.entities.WarDeclaration).id, player.uniqueId)
                        if (success) {
                            player.sendMessage(lang.msg("bedrock.war_management.management_declaration_rejected"))
                        } else {
                            player.sendMessage(lang.msg("bedrock.war_management.management_declaration_failed"))
                        }
                        openWarDeclarationsMenu()
                    }
                    !isIncoming && clickedButton == 0 -> {
                        // Cancel declaration
                        val success = warService.cancelWarDeclaration((declaration as net.lumalyte.lg.domain.entities.WarDeclaration).id, player.uniqueId)
                        if (success) {
                            player.sendMessage(lang.msg("bedrock.war_management.management_declaration_cancelled"))
                        } else {
                            player.sendMessage(lang.msg("bedrock.war_management.management_declaration_failed"))
                        }
                        openWarDeclarationsMenu()
                    }
                    else -> openWarDeclarationsMenu() // Back
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                openWarDeclarationsMenu() // Back to declarations menu
            }
            .build()

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
            override fun getForm(): Form = formWithHandler

            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun acceptWarDeclaration(warDeclaration: net.lumalyte.lg.domain.entities.WarDeclaration) {
        try {
            // REQ-039: escrow happens in the war service (acceptWarDeclaration →
            // createWager deducts both guilds). The menu only pre-checks funds for
            // a friendly error; it must NOT withdraw or create the wager itself.
            if (warDeclaration.wagerAmount > 0) {
                // Refresh guild data to get current bank balance
                val currentGuild = guildService.getGuild(guild.id)
                if (currentGuild == null) {
                    player.sendMessage(lang.msg("bedrock.war_management.error_load_guild"))
                    openWarDeclarationsMenu()
                    return
                }

                // Check if guild has sufficient funds to match wager
                val guildBalance = bankService.getBalance(currentGuild.id)
                if (guildBalance < warDeclaration.wagerAmount) {
                    player.sendMessage(lang.msg("bedrock.war_management.error_match_wager"))
            player.sendMessage(lang.msg("bedrock.war_management.wager_need_amount", "amount" to warDeclaration.wagerAmount))
            player.sendMessage(lang.msg("bedrock.war_management.wager_have_amount", "amount" to guildBalance))
                    openWarDeclarationsMenu()
                    return
                }

                // Check withdraw permissions
                if (!memberService.hasPermission(player.uniqueId, currentGuild.id, RankPermission.WITHDRAW_FROM_BANK)) {
                    player.sendMessage(lang.msg("bedrock.war_management.wager_no_permission"))
                    openWarDeclarationsMenu()
                    return
                }
            }

            val war = warService.acceptWarDeclaration(warDeclaration.id, player.uniqueId)
            if (war != null) {
                if (warDeclaration.wagerAmount > 0) {
                    val wager = warService.getWager(war.id)
                    if (wager != null) {
                        player.sendMessage(lang.msg("bedrock.war_management.accepted"))
                player.sendMessage(lang.msg("bedrock.war_management.wager_pot_total", "amount" to wager.totalPot))
                    } else {
                        player.sendMessage(lang.msg("bedrock.war_management.accepted"))
                        player.sendMessage(lang.msg("bedrock.war_management.wager_escrow_failed"))
                    }
                } else {
                    player.sendMessage(lang.msg("bedrock.war_management.management_declaration_accepted"))
                }
            } else {
                player.sendMessage(lang.msg("bedrock.war_management.management_declaration_failed"))
            }
            openWarDeclarationsMenu()
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage(lang.msg("bedrock.war_management.error_accepting", "reason" to (e.message ?: lang.bedrock("bedrock.war_management.unknown_error"))))
            openWarDeclarationsMenu()
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
        onFormResponseReceived()
    }
}
