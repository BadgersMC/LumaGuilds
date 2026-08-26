package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.bedrock
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

/**
 * Bedrock Edition guild party management menu using Cumulus SimpleForm and CustomForm
 * Provides comprehensive party coordination and management interface
 */
class BedrockGuildPartyManagementMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        return SimpleForm.builder()
            .title("${lang.bedrock("bedrock.party.management.title")} - ${guild.name}")
            .content(buildPartyManagementContent())
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.current_parties"),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.requests"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.create"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.send_request"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.settings"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.back"),
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
            return lang.bedrock("bedrock.party.management.disabled")
        }

        val activeParties = partyService.getActivePartiesForGuild(guild.id)
        val incomingRequests = partyService.getPendingRequestsForGuild(guild.id)
        val outgoingRequests = partyService.getPendingRequestsFromGuild(guild.id)

        return """
            |${lang.bedrock("bedrock.party.management.welcome")}
            |
            |${lang.bedrock("bedrock.party.management.active_parties")}: ${activeParties.size}
            |${lang.bedrock("bedrock.party.management.incoming_requests")}: ${incomingRequests.size}
            |${lang.bedrock("bedrock.party.management.outgoing_requests")}: ${outgoingRequests.size}
            |
            |${lang.bedrock("bedrock.party.management.description")}
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
            player.sendMessage(lang.msg("bedrock.party.management.no_active_parties"))
            return
        }

        val form = SimpleForm.builder()
            .title(lang.bedrock("bedrock.party.management.current_parties"))
            .content(lang.bedrock("bedrock.party.management.select_party"))

        activeParties.forEach { party ->
            val partyName = party.name ?: lang.bedrock("bedrock.party.management.unnamed")
            val memberCount = party.guildIds.size
            val createdDate = party.createdAt.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))

            form.addButtonWithImage(
                config,
                "$partyName\n${lang.bedrock("bedrock.party.management.members")}: $memberCount | ${lang.bedrock("bedrock.party.management.created")}: $createdDate",
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.party.management.back"),
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

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
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
            .title(lang.bedrock("bedrock.party.management.requests"))
            .content("""
                |${lang.bedrock("bedrock.party.management.incoming_requests")}: ${incomingRequests.size}
                |${lang.bedrock("bedrock.party.management.outgoing_requests")}: ${outgoingRequests.size}
            """.trimMargin())

        if (incomingRequests.isNotEmpty()) {
            form.addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.view_incoming"),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
        }
        if (outgoingRequests.isNotEmpty()) {
            form.addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.view_outgoing"),
                config.editIconUrl,
                config.editIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.party.management.back"),
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

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
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
            player.sendMessage(lang.msg("bedrock.party.management.no_permission"))
            return
        }

        val form = CustomForm.builder()
            .title(lang.bedrock("bedrock.party.management.create"))
            .label(lang.bedrock("bedrock.party.management.create_description"))
            .input(
                lang.bedrock("bedrock.party.management.party_name"),
                lang.bedrock("bedrock.party.management.party_name_placeholder")
            )
            .input(
                lang.bedrock("bedrock.party.management.party_description"),
                lang.bedrock("bedrock.party.management.party_description_placeholder")
            )
            .validResultHandler { response ->
                handleCreatePartyResponse(response)
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }
            .build()

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
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
            player.sendMessage(lang.msg("bedrock.party.management.no_permission"))
            return
        }

        val form = CustomForm.builder()
            .title(lang.bedrock("bedrock.party.management.send_request"))
            .label(lang.bedrock("bedrock.party.management.send_request_description"))
            .input(
                lang.bedrock("bedrock.party.management.target_guild"),
                lang.bedrock("bedrock.party.management.target_guild_placeholder")
            )
            .input(
                lang.bedrock("bedrock.party.management.request_message"),
                lang.bedrock("bedrock.party.management.request_message_placeholder")
            )
            .validResultHandler { response ->
                handleSendPartyRequestResponse(response)
            }
            .closedOrInvalidResultHandler { _, _ ->
                getForm() // Back to main menu
            }
            .build()

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
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
            .title(lang.bedrock("bedrock.party.management.settings"))
            .content(lang.bedrock("bedrock.party.management.settings_description"))

        if (canManageParties) {
            form.addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.settings_permissions"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
        } else {
            form.addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.settings_permissions_disabled"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.party.management.settings_info"),
            config.editIconUrl,
            config.editIconPath
        )
        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.party.management.back"),
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

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
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
        player.sendMessage("<yellow>Party details are not available in Bedrock.")
    }

    private fun openIncomingRequestsMenu() {
        val config = getBedrockConfig()
        val incomingRequests = partyService.getPendingRequestsForGuild(guild.id)
        if (incomingRequests.isEmpty()) {
            player.sendMessage(lang.msg("bedrock.party.management.no_incoming_requests"))
            return
        }

        val form = SimpleForm.builder()
            .title(lang.bedrock("bedrock.party.management.incoming_requests"))
            .content(lang.bedrock("bedrock.party.management.select_request"))

        incomingRequests.forEach { request ->
            val fromGuild = guildService.getGuild(request.fromGuildId)
            val fromGuildName = fromGuild?.name ?: "Unknown Guild"
            form.addButtonWithImage(
                config,
                "${lang.bedrock("bedrock.party.management.from")}: $fromGuildName\n${lang.bedrock("bedrock.party.management.message")}: ${request.message ?: lang.bedrock("bedrock.party.management.no_message")}",
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.party.management.back"),
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

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
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
            player.sendMessage(lang.msg("bedrock.party.management.no_outgoing_requests"))
            return
        }

        val form = SimpleForm.builder()
            .title(lang.bedrock("bedrock.party.management.outgoing_requests"))
            .content(lang.bedrock("bedrock.party.management.select_request"))

        outgoingRequests.forEach { request ->
            val toGuild = guildService.getGuild(request.toGuildId)
            val toGuildName = toGuild?.name ?: "Unknown Guild"
            form.addButtonWithImage(
                config,
                "${lang.bedrock("bedrock.party.management.to")}: $toGuildName\n${lang.bedrock("bedrock.party.management.message")}: ${request.message ?: lang.bedrock("bedrock.party.management.no_message")}",
                config.editIconUrl,
                config.editIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.party.management.back"),
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

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
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
            .title(if (isIncoming) lang.bedrock("bedrock.party.management.request_action_incoming") else lang.bedrock("bedrock.party.management.request_action_outgoing"))
            .content(lang.bedrock("bedrock.party.management.request_action_description"))

        if (isIncoming) {
            form.addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.request_accept"),
                config.confirmIconUrl,
                config.confirmIconPath
            )
            form.addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.request_reject"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
        } else {
            form.addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.request_cancel"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.bedrock("bedrock.party.management.back"),
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
                            player.sendMessage(lang.msg("bedrock.party.management.request_accepted"))
                        } else {
                            player.sendMessage(lang.msg("bedrock.party.management.request_failed"))
                        }
                    }
                    isIncoming && clickedButton == 1 -> {
                        // Reject request
                        val success = partyService.rejectPartyRequest((request as net.lumalyte.lg.domain.entities.PartyRequest).id, guild.id, player.uniqueId)
                        if (success) {
                            player.sendMessage(lang.msg("bedrock.party.management.request_rejected"))
                        } else {
                            player.sendMessage(lang.msg("bedrock.party.management.request_failed"))
                        }
                    }
                    !isIncoming && clickedButton == 0 -> {
                        // Cancel request
                        val success = partyService.cancelPartyRequest((request as net.lumalyte.lg.domain.entities.PartyRequest).id, guild.id, player.uniqueId)
                        if (success) {
                            player.sendMessage(lang.msg("bedrock.party.management.request_cancelled"))
                        } else {
                            player.sendMessage(lang.msg("bedrock.party.management.request_failed"))
                        }
                    }
                }
                openPartyRequestsMenu() // Refresh requests menu
            }
            .closedOrInvalidResultHandler { _, _ ->
                openPartyRequestsMenu() // Back to requests menu
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
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
            player.sendMessage(lang.msg("bedrock.party.management.create_name_required"))
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
            player.sendMessage(lang.msg("bedrock.party.management.create_success", "party" to partyName))
        } else {
            player.sendMessage(lang.msg("bedrock.party.management.create_failed"))
        }

        getForm() // Refresh main menu
    }

    private fun handleSendPartyRequestResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        val targetGuildName = response.asInput(0)
        val message = response.asInput(1)

        if (targetGuildName.isNullOrBlank()) {
            player.sendMessage(lang.msg("bedrock.party.management.send_request_guild_required"))
            return
        }

        val targetGuild = guildService.getAllGuilds().find { it.name.equals(targetGuildName, ignoreCase = true) }
        if (targetGuild == null) {
            player.sendMessage(lang.msg("bedrock.party.management.send_request_guild_not_found"))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(lang.msg("bedrock.party.management.send_request_self"))
            return
        }

        val request = partyService.sendPartyRequest(guild.id, targetGuild.id, player.uniqueId, message)
        if (request != null) {
            player.sendMessage(lang.msg("bedrock.party.management.send_request_success", "guild" to targetGuild.name))
        } else {
            player.sendMessage(lang.msg("bedrock.party.management.send_request_failed"))
        }

        getForm() // Refresh main menu
    }

    private fun openPartyPermissionsMenu() {
        player.sendMessage("<yellow>Party permissions configuration is not available in Bedrock.")
    }

    private fun openPartyInfoMenu() {
        val config = getBedrockConfig()
        val form = SimpleForm.builder()
            .title(lang.bedrock("bedrock.party.management.settings_info"))
            .content("""
                |${lang.bedrock("bedrock.party.management.settings_info_description")}
                |
                |${lang.bedrock("bedrock.party.management.settings_info_permissions")}
                |• ${lang.bedrock("bedrock.party.management.settings_info_view")}: ${lang.bedrock("bedrock.party.management.settings_info_all_members")}
                |• ${lang.bedrock("bedrock.party.management.settings_info_accept")}: ${lang.bedrock("bedrock.party.management.settings_info_admin_only")}
                |• ${lang.bedrock("bedrock.party.management.settings_info_send")}: ${lang.bedrock("bedrock.party.management.settings_info_admin_only")}
                |• ${lang.bedrock("bedrock.party.management.settings_info_manage")}: ${lang.bedrock("bedrock.party.management.settings_info_admin_only")}
                |
                |${lang.bedrock("bedrock.party.management.settings_info_invite_only")}
            """.trimMargin())
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.management.back"),
                config.closeIconUrl,
                config.closeIconPath
            )
            .validResultHandler { _ ->
                openPartySettingsMenu()
            }
            .closedOrInvalidResultHandler { _, _ ->
                openPartySettingsMenu()
            }

        bedrockNavigator.openMenu(object : BaseBedrockMenu(menuNavigator, player, logger) {
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
