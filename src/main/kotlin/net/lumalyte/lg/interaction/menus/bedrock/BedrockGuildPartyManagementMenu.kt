package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Logger
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Bedrock Edition guild party management menu using Cumulus SimpleForm and CustomForm
 * Provides comprehensive party coordination and management interface
 */
class BedrockGuildPartyManagementMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
    messageService: MessageService
) : BaseBedrockMenu(menuNavigator, player, logger, messageService) {

    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.party.management.title")} - ${guild.name}")
            .content(buildPartyManagementContent())
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.party.management.current.parties"),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.party.management.requests"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.party.management.create"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.party.management.send.request"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.party.management.settings"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "menu.back"),
                config.closeIconUrl,
                config.closeIconPath
            )
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                handleMenuSelection(clickedButton)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun buildPartyManagementContent(): String {
        val config = configService.loadConfig()

        // Check if parties are enabled
        if (!config.partiesEnabled) {
            return bedrockLocalization.getBedrockString(player, "guild.party.management.disabled")
        }

        val activeParties = partyService.getActivePartiesForGuild(guild.id)
        val incomingRequests = partyService.getPendingRequestsForGuild(guild.id)
        val outgoingRequests = partyService.getPendingRequestsFromGuild(guild.id)

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.party.management.welcome")}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.party.management.active.parties")}: ${activeParties.size}
            |${bedrockLocalization.getBedrockString(player, "guild.party.management.incoming.requests")}: ${incomingRequests.size}
            |${bedrockLocalization.getBedrockString(player, "guild.party.management.outgoing.requests")}: ${outgoingRequests.size}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.party.management.description")}
        """.trimMargin()
    }

    private fun handleMenuSelection(buttonId: Int) {
        when (buttonId) {
            0 -> openCurrentPartiesMenu()
            1 -> openPartyRequestsMenu()
            2 -> openCreatePartyMenu()
            3 -> openSendPartyRequestMenu()
            4 -> openPartySettingsMenu()
            5 -> bedrockNavigator.goBack()
        }
    }

    private fun openCurrentPartiesMenu() {
        val config = getBedrockConfig()
        val activeParties = partyService.getActivePartiesForGuild(guild.id)

        if (activeParties.isEmpty()) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.no.active.parties"))
            return
        }

        val form = SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.party.management.current.parties"))
            .content(bedrockLocalization.getBedrockString(player, "guild.party.management.select.party"))

        activeParties.forEach { party ->
            val partyName = party.name ?: bedrockLocalization.getBedrockString(player, "guild.party.management.unnamed")
            val memberCount = party.guildIds.size
            val createdDate = party.createdAt.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))

            form.addButtonWithImage(
                config,
                "$partyName\n${bedrockLocalization.getBedrockString(player, "guild.party.management.members")}: $memberCount | ${bedrockLocalization.getBedrockString(player, "guild.party.management.created")}: $createdDate",
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
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton < activeParties.size) {
                    openPartyDetailsMenu(activeParties.elementAt(clickedButton))
                } else {
                    getForm() // Back to main menu
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
            override fun getForm(): Form = form.build()
            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun openPartyRequestsMenu() {
        val config = getBedrockConfig()
        val incomingRequests = partyService.getPendingRequestsForGuild(guild.id)
        val outgoingRequests = partyService.getPendingRequestsFromGuild(guild.id)

        val form = SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.party.management.requests"))
            .content("""
                |${bedrockLocalization.getBedrockString(player, "guild.party.management.incoming.requests")}: ${incomingRequests.size}
                |${bedrockLocalization.getBedrockString(player, "guild.party.management.outgoing.requests")}: ${outgoingRequests.size}
            """.trimMargin())

        if (incomingRequests.isNotEmpty()) {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.party.management.view.incoming"),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
        }
        if (outgoingRequests.isNotEmpty()) {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.party.management.view.outgoing"),
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
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                when {
                    incomingRequests.isNotEmpty() && clickedButton == 0 -> openIncomingRequestsMenu()
                    outgoingRequests.isNotEmpty() && clickedButton == (if (incomingRequests.isNotEmpty()) 1 else 0) -> openOutgoingRequestsMenu()
                    else -> getForm() // Back to main menu
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
            override fun getForm(): Form = form.build()
            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun openCreatePartyMenu() {
        val canManageParties = memberService.hasPermission(player.uniqueId, guild.id,
            net.lumalyte.lg.domain.entities.RankPermission.MANAGE_PARTIES)

        if (!canManageParties) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.no.permission"))
            return
        }

        val form = CustomForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.party.management.create"))
            .label(bedrockLocalization.getBedrockString(player, "guild.party.management.create.description"))
            .input(
                bedrockLocalization.getBedrockString(player, "guild.party.management.party.name"),
                bedrockLocalization.getBedrockString(player, "guild.party.management.party.name.placeholder")
            )
            .input(
                bedrockLocalization.getBedrockString(player, "guild.party.management.party.description"),
                bedrockLocalization.getBedrockString(player, "guild.party.management.party.description.placeholder")
            )
            .validResultHandler { response ->
                handleCreatePartyResponse(response)
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }
            .build()

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
            override fun getForm(): Form = form
            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun openSendPartyRequestMenu() {
        val canManageParties = memberService.hasPermission(player.uniqueId, guild.id,
            net.lumalyte.lg.domain.entities.RankPermission.MANAGE_PARTIES)

        if (!canManageParties) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.no.permission"))
            return
        }

        val form = CustomForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.party.management.send.request"))
            .label(bedrockLocalization.getBedrockString(player, "guild.party.management.send.request.description"))
            .input(
                bedrockLocalization.getBedrockString(player, "guild.party.management.target.guild"),
                bedrockLocalization.getBedrockString(player, "guild.party.management.target.guild.placeholder")
            )
            .input(
                bedrockLocalization.getBedrockString(player, "guild.party.management.request.message"),
                bedrockLocalization.getBedrockString(player, "guild.party.management.request.message.placeholder")
            )
            .validResultHandler { response ->
                handleSendPartyRequestResponse(response)
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }
            .build()

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
            override fun getForm(): Form = form
            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun openPartySettingsMenu() {
        val config = getBedrockConfig()
        val canManageParties = memberService.hasPermission(player.uniqueId, guild.id,
            net.lumalyte.lg.domain.entities.RankPermission.MANAGE_PARTIES)

        val form = SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.party.management.settings"))
            .content(bedrockLocalization.getBedrockString(player, "guild.party.management.settings.description"))

        if (canManageParties) {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.party.management.settings.permissions"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
        } else {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.party.management.settings.permissions.disabled"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
        }

        form.addButtonWithImage(
            config,
            bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info"),
            config.editIconUrl,
            config.editIconPath
        )
        form.addButtonWithImage(
            config,
            bedrockLocalization.getBedrockString(player, "menu.back"),
            config.closeIconUrl,
            config.closeIconPath
        )
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                when (clickedButton) {
                    0 -> if (canManageParties) openPartyPermissionsMenu() else getForm()
                    1 -> openPartyInfoMenu()
                    2 -> getForm() // Back to main menu
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
            override fun getForm(): Form = form.build()
            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    // Helper methods for handling responses and opening sub-menus
    private fun openPartyDetailsMenu(party: Party) {
        player.sendMessage("<yellow>${bedrockLocalization.getBedrockString(player, "guild.party.management.party.details.coming.soon")}")
    }

    private fun openIncomingRequestsMenu() {
        val config = getBedrockConfig()
        val incomingRequests = partyService.getPendingRequestsForGuild(guild.id)
        if (incomingRequests.isEmpty()) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.no.incoming.requests"))
            return
        }

        val form = SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.party.management.incoming.requests"))
            .content(bedrockLocalization.getBedrockString(player, "guild.party.management.select.request"))

        incomingRequests.forEach { request ->
            val fromGuild = guildService.getGuild(request.fromGuildId)
            val fromGuildName = fromGuild?.name ?: "Unknown Guild"
            form.addButtonWithImage(
                config,
                "${bedrockLocalization.getBedrockString(player, "guild.party.management.from")}: $fromGuildName\n${bedrockLocalization.getBedrockString(player, "guild.party.management.message")}: ${request.message ?: bedrockLocalization.getBedrockString(player, "guild.party.management.no.message")}",
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
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton < incomingRequests.size) {
                    openRequestActionMenu(incomingRequests.elementAt(clickedButton), true)
                } else {
                    openPartyRequestsMenu() // Back to requests menu
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                openPartyRequestsMenu() // Back to requests menu
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
            override fun getForm(): Form = form.build()
            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun openOutgoingRequestsMenu() {
        val config = getBedrockConfig()
        val outgoingRequests = partyService.getPendingRequestsFromGuild(guild.id)
        if (outgoingRequests.isEmpty()) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.no.outgoing.requests"))
            return
        }

        val form = SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.party.management.outgoing.requests"))
            .content(bedrockLocalization.getBedrockString(player, "guild.party.management.select.request"))

        outgoingRequests.forEach { request ->
            val toGuild = guildService.getGuild(request.toGuildId)
            val toGuildName = toGuild?.name ?: "Unknown Guild"
            form.addButtonWithImage(
                config,
                "${bedrockLocalization.getBedrockString(player, "guild.party.management.to")}: $toGuildName\n${bedrockLocalization.getBedrockString(player, "guild.party.management.message")}: ${request.message ?: bedrockLocalization.getBedrockString(player, "guild.party.management.no.message")}",
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
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton < outgoingRequests.size) {
                    openRequestActionMenu(outgoingRequests.elementAt(clickedButton), false)
                } else {
                    openPartyRequestsMenu() // Back to requests menu
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                openPartyRequestsMenu() // Back to requests menu
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
            override fun getForm(): Form = form.build()
            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun openRequestActionMenu(request: net.lumalyte.lg.domain.entities.PartyRequest, isIncoming: Boolean) {
        val config = getBedrockConfig()
        val form = SimpleForm.builder()
            .title(if (isIncoming) bedrockLocalization.getBedrockString(player, "guild.party.management.request.action.incoming") else bedrockLocalization.getBedrockString(player, "guild.party.management.request.action.outgoing"))
            .content(bedrockLocalization.getBedrockString(player, "guild.party.management.request.action.description"))

        if (isIncoming) {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.party.management.request.accept"),
                config.confirmIconUrl,
                config.confirmIconPath
            )
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.party.management.request.reject"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
        } else {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.party.management.request.cancel"),
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
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                when {
                    isIncoming && clickedButton == 0 -> {
                        // Accept request
                        val success = partyService.acceptPartyRequest((request as net.lumalyte.lg.domain.entities.PartyRequest).id, guild.id, player.uniqueId)
                        if (success != null) {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.request.accepted"))
                        } else {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.request.failed"))
                        }
                    }
                    isIncoming && clickedButton == 1 -> {
                        // Reject request
                        val success = partyService.rejectPartyRequest((request as net.lumalyte.lg.domain.entities.PartyRequest).id, guild.id, player.uniqueId)
                        if (success) {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.request.rejected"))
                        } else {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.request.failed"))
                        }
                    }
                    !isIncoming && clickedButton == 0 -> {
                        // Cancel request
                        val success = partyService.cancelPartyRequest((request as net.lumalyte.lg.domain.entities.PartyRequest).id, guild.id, player.uniqueId)
                        if (success) {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.request.cancelled"))
                        } else {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.request.failed"))
                        }
                    }
                }
                openPartyRequestsMenu() // Refresh requests menu
            }
            .closedOrInvalidResultHandler { _, _ ->
                openPartyRequestsMenu() // Back to requests menu
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
            override fun getForm(): Form = form.build()
            override fun handleResponse(player: Player, response: Any?) {
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun handleCreatePartyResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        val partyName = response.asInput(0)
        val partyDescription = response.asInput(1)

        if (partyName.isNullOrBlank()) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.create.name.required"))
            return
        }

        // Create party object and call service
        val party = net.lumalyte.lg.domain.entities.Party(
            id = java.util.UUID.randomUUID(),
            name = partyName,
            guildIds = setOf(guild.id),
            leaderId = player.uniqueId,
            createdAt = java.time.Instant.now(),
            expiresAt = null // No expiration for new parties
        )

        val createdParty = partyService.createParty(party)
        if (createdParty != null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.create.success", partyName))
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.create.failed"))
        }

        getForm() // Refresh main menu
    }

    private fun handleSendPartyRequestResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        val targetGuildName = response.asInput(0)
        val message = response.asInput(1)

        if (targetGuildName.isNullOrBlank()) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.send.request.guild.required"))
            return
        }

        val targetGuild = guildService.getAllGuilds().find { it.name.equals(targetGuildName, ignoreCase = true) }
        if (targetGuild == null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.send.request.guild.not.found"))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.send.request.self"))
            return
        }

        val request = partyService.sendPartyRequest(guild.id, targetGuild.id, player.uniqueId, message)
        if (request != null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.send.request.success", targetGuild.name))
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.management.send.request.failed"))
        }

        getForm() // Refresh main menu
    }

    private fun openPartyPermissionsMenu() {
        player.sendMessage("<yellow>${bedrockLocalization.getBedrockString(player, "guild.party.management.settings.permissions.coming.soon")}")
    }

    private fun openPartyInfoMenu() {
        val config = getBedrockConfig()
        val form = SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info"))
            .content("""
                |${bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info.description")}
                |
                |${bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info.permissions")}
                |• ${bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info.view")}: ${bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info.all.members")}
                |• ${bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info.accept")}: ${bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info.admin.only")}
                |• ${bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info.send")}: ${bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info.admin.only")}
                |• ${bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info.manage")}: ${bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info.admin.only")}
                |
                |${bedrockLocalization.getBedrockString(player, "guild.party.management.settings.info.invite.only")}
            """.trimMargin())
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "menu.back"),
                config.closeIconUrl,
                config.closeIconPath
            )
            .validResultHandler { _ ->
                openPartySettingsMenu()
            }
            .closedOrInvalidResultHandler { _, _ ->
                openPartySettingsMenu()
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger, messageService) {
            override fun getForm(): Form = form.build()
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

