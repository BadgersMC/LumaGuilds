package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.War
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
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Bedrock Edition guild war management menu using Cumulus SimpleForm and CustomForm
 * Provides comprehensive war declaration, management, and tracking interface
 */
class BedrockGuildWarManagementMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
    messageService: MessageService
) : BaseBedrockMenu(menuNavigator, player, logger, messageService), KoinComponent {

    private val warService: WarService by inject()
    private val guildService: GuildService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val canManageWars = warService.canPlayerManageWars(player.uniqueId, guild.id)

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.war.management.title")} - ${guild.name}")
            .content("""
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.welcome")}
                |
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.description")}
                |
                |${bedrockLocalization.getBedrockString(player, "menu.notice")}
            """.trimMargin())
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.war.management.active.wars"),
                config.guildWarsIconUrl,
                config.guildWarsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.war.management.war.declarations"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.war.management.declare.war"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.war.management.war.history"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.war.management.war.statistics"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "menu.back"),
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
        val wars: List<War> = warService.getWarsForGuild(guild.id)
        val activeWars = wars.filter { war: War -> war.isActive }

        val form = SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.war.management.active.wars"))
            .content(
                if (activeWars.isEmpty()) {
                    bedrockLocalization.getBedrockString(player, "guild.war.management.no.active.wars")
                } else {
                    "${bedrockLocalization.getBedrockString(player, "guild.war.management.select.war")}\n\n${bedrockLocalization.getBedrockString(player, "guild.war.management.active.wars.count", activeWars.size)}"
                }
            )

        activeWars.forEach { war: War ->
            val opponentGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val opponentGuild = guildService.getGuild(opponentGuildId)
            val opponentName = opponentGuild?.name ?: bedrockLocalization.getBedrockString(player, "guild.war.management.unknown.guild")

            val startedDate = war.startedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) ?: "Unknown"
            val remainingDays = war.remainingDuration?.toDays() ?: 0

            form.addButtonWithImage(
                config,
                "⚔️ vs $opponentName\n${bedrockLocalization.getBedrockString(player, "guild.war.management.started")}: $startedDate\n${bedrockLocalization.getBedrockString(player, "guild.war.management.remaining.days", remainingDays)}",
                config.guildWarsIconUrl,
                config.guildWarsIconPath
            )
        }

        form.addButtonWithImage(
            config,
            bedrockLocalization.getBedrockString(player, "menu.back"),
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

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
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
            .title(bedrockLocalization.getBedrockString(player, "guild.war.management.war.declarations"))
            .content("""
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.declarations.incoming")}: ${incomingDeclarations.size}
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.declarations.outgoing")}: ${outgoingDeclarations.size}
            """.trimMargin())

        if (incomingDeclarations.isNotEmpty()) {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.war.management.view.incoming.declarations"),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
        }

        if (outgoingDeclarations.isNotEmpty()) {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.war.management.view.outgoing.declarations"),
                config.editIconUrl,
                config.editIconPath
            )
        }

        if (incomingDeclarations.isEmpty() && outgoingDeclarations.isEmpty()) {
            form.content(bedrockLocalization.getBedrockString(player, "guild.war.management.no.declarations"))
        }

        form.addButtonWithImage(
            config,
            bedrockLocalization.getBedrockString(player, "menu.back"),
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

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
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
            .title(bedrockLocalization.getBedrockString(player, "guild.war.management.declare.war"))
            .label(bedrockLocalization.getBedrockString(player, "guild.war.management.declare.war.description"))
            .addLocalizedInput(
                player, bedrockLocalization,
                "guild.war.management.declare.war.target.label",
                "guild.war.management.declare.war.target.placeholder"
            )
            .addLocalizedInput(
                player, bedrockLocalization,
                "guild.war.management.declare.war.duration.label",
                "guild.war.management.declare.war.duration.placeholder"
            )
            .addLocalizedInput(
                player, bedrockLocalization,
                "guild.war.management.declare.war.reason.label",
                "guild.war.management.declare.war.reason.placeholder"
            )
            .validResultHandler { response ->
                handleDeclareWarResponse(response)
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }
            .build()

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
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
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.management.declare.war.target.required"))
            return
        }

        val targetGuild = guildService.getGuildByName(targetGuildName)
        if (targetGuild == null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.management.declare.war.guild.not.found"))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.management.declare.war.self"))
            return
        }

        val duration = try {
            Duration.ofDays(durationStr.toLong().coerceIn(1, 30))
        } catch (e: Exception) {
            Duration.ofDays(7)
        }

        val success = warService.declareWar(guild.id, targetGuild.id, duration, emptySet(), player.uniqueId)
        if (success != null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.management.declare.war.declared", targetGuild.name))
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.management.declare.war.failed"))
        }

        getForm() // Refresh main menu
    }

    private fun openWarHistoryMenu() {
        val config = getBedrockConfig()
        val warHistory = warService.getWarHistory(guild.id, 10)

        val form = SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.war.management.war.history"))
            .content(
                if (warHistory.isEmpty()) {
                    bedrockLocalization.getBedrockString(player, "guild.war.management.no.war.history")
                } else {
                    "${bedrockLocalization.getBedrockString(player, "guild.war.management.select.war")}\n\n${bedrockLocalization.getBedrockString(player, "guild.war.management.history.count", warHistory.size)}"
                }
            )

        warHistory.forEach { war: War ->
            val opponentGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val opponentGuild = guildService.getGuild(opponentGuildId)
            val opponentName = opponentGuild?.name ?: bedrockLocalization.getBedrockString(player, "guild.war.management.unknown.guild")
            val result = if (war.winner == guild.id) "✅ ${bedrockLocalization.getBedrockString(player, "guild.war.management.won")}" else "❌ ${bedrockLocalization.getBedrockString(player, "guild.war.management.lost")}"

            val endedDate = war.endedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) ?: "Ongoing"

            form.addButtonWithImage(
                config,
                "⚔️ vs $opponentName - $result\n${bedrockLocalization.getBedrockString(player, "guild.war.management.ended")}: $endedDate",
                config.editIconUrl,
                config.editIconPath
            )
        }

        form.addButtonWithImage(
            config,
            bedrockLocalization.getBedrockString(player, "menu.back"),
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

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
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
            .title(bedrockLocalization.getBedrockString(player, "guild.war.management.war.statistics"))
            .content("""
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.statistics.wars.won")}: $wins
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.statistics.wars.lost")}: $losses
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.statistics.win.rate")}: ${String.format("%.1f", winLossRatio * 100)}%
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.statistics.total.wars")}: ${warHistory.size}
            """.trimMargin())
            .addButtonWithImage(
                getBedrockConfig(),
                bedrockLocalization.getBedrockString(player, "menu.back"),
                getBedrockConfig().closeIconUrl,
                getBedrockConfig().closeIconPath
            )
            .validResultHandler { _ ->
                getForm() // Back to main menu
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
            override fun getForm(): Form = form.build()

            override fun handleResponse(player: Player, response: Any?) {
                onFormResponseReceived()
            }
        })
    }

    private fun openWarDetailsMenu(war: War) {
        val opponentGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
        val opponentGuild = guildService.getGuild(opponentGuildId)
        val opponentName = opponentGuild?.name ?: bedrockLocalization.getBedrockString(player, "guild.war.management.unknown.guild")
        val isWinner = war.winner == guild.id

        val status = when {
            war.winner != null -> if (isWinner) bedrockLocalization.getBedrockString(player, "guild.war.management.won") else bedrockLocalization.getBedrockString(player, "guild.war.management.lost")
            war.endedAt != null -> bedrockLocalization.getBedrockString(player, "guild.war.management.draw")
            else -> bedrockLocalization.getBedrockString(player, "guild.war.management.active")
        }

        val form = SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.war.management.war.details")} - $opponentName")
            .content("""
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.opponent")}: $opponentName
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.started")}: ${war.startedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")) ?: "Not Started"}
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.ended")}: ${war.endedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")) ?: bedrockLocalization.getBedrockString(player, "guild.war.management.ongoing")}
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.result")}: $status
                |${bedrockLocalization.getBedrockString(player, "guild.war.management.duration")}: ${war.duration.toDays()} days
            """.trimMargin())
            .addButtonWithImage(
                getBedrockConfig(),
                bedrockLocalization.getBedrockString(player, "menu.back"),
                getBedrockConfig().closeIconUrl,
                getBedrockConfig().closeIconPath
            )
            .validResultHandler { _ ->
                getForm() // Back to main menu
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
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
            .title(bedrockLocalization.getBedrockString(player, "guild.war.management.incoming.declarations"))
            .content(bedrockLocalization.getBedrockString(player, "guild.war.management.select.declaration"))

        incomingDeclarations.forEach { declaration ->
            val declaringGuild = guildService.getGuild(declaration.declaringGuildId)
            val declaringName = declaringGuild?.name ?: bedrockLocalization.getBedrockString(player, "guild.war.management.unknown.guild")

            form.addButtonWithImage(
                config,
                "${bedrockLocalization.getBedrockString(player, "guild.war.management.from")}: $declaringName\n${bedrockLocalization.getBedrockString(player, "guild.war.management.duration")}: ${declaration.proposedDuration.toDays()} days",
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
        }

        form.addButtonWithImage(
            config,
            bedrockLocalization.getBedrockString(player, "menu.back"),
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

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
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
            .title(bedrockLocalization.getBedrockString(player, "guild.war.management.outgoing.declarations"))
            .content(bedrockLocalization.getBedrockString(player, "guild.war.management.select.declaration"))

        outgoingDeclarations.forEach { declaration ->
            val targetGuild = guildService.getGuild(declaration.defendingGuildId)
            val targetName = targetGuild?.name ?: bedrockLocalization.getBedrockString(player, "guild.war.management.unknown.guild")

            form.addButtonWithImage(
                config,
                "${bedrockLocalization.getBedrockString(player, "guild.war.management.to")}: $targetName\n${bedrockLocalization.getBedrockString(player, "guild.war.management.duration")}: ${declaration.proposedDuration.toDays()} days",
                config.editIconUrl,
                config.editIconPath
            )
        }

        form.addButtonWithImage(
            config,
            bedrockLocalization.getBedrockString(player, "menu.back"),
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

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
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

        val form = SimpleForm.builder()
            .title(if (isIncoming) bedrockLocalization.getBedrockString(player, "guild.war.management.declaration.action.incoming") else bedrockLocalization.getBedrockString(player, "guild.war.management.declaration.action.outgoing"))
            .content(bedrockLocalization.getBedrockString(player, "guild.war.management.declaration.action.description"))

        if (isIncoming) {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.war.management.declaration.accept"),
                config.confirmIconUrl,
                config.confirmIconPath
            )
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.war.management.declaration.reject"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
        } else {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.war.management.declaration.cancel"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
        }

        form.addButtonWithImage(
            config,
            bedrockLocalization.getBedrockString(player, "menu.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                when {
                    isIncoming && clickedButton == 0 -> {
                        // Accept declaration
                        val success = warService.acceptWarDeclaration((declaration as net.lumalyte.lg.domain.entities.WarDeclaration).id, player.uniqueId)
                        if (success != null) {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.management.declaration.accepted"))
                        } else {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.management.declaration.failed"))
                        }
                        openWarDeclarationsMenu()
                    }
                    isIncoming && clickedButton == 1 -> {
                        // Reject declaration
                        val success = warService.rejectWarDeclaration((declaration as net.lumalyte.lg.domain.entities.WarDeclaration).id, player.uniqueId)
                        if (success) {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.management.declaration.rejected"))
                        } else {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.management.declaration.failed"))
                        }
                        openWarDeclarationsMenu()
                    }
                    !isIncoming && clickedButton == 0 -> {
                        // Cancel declaration
                        val success = warService.cancelWarDeclaration((declaration as net.lumalyte.lg.domain.entities.WarDeclaration).id, player.uniqueId)
                        if (success) {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.management.declaration.cancelled"))
                        } else {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.management.declaration.failed"))
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

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
            override fun getForm(): Form = formWithHandler

            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
        onFormResponseReceived()
    }
}

