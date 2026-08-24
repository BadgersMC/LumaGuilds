package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.util.logging.Logger

/**
 * Bedrock Edition guild relations management menu using Cumulus SimpleForm and CustomForm
 * Provides comprehensive diplomatic relations and alliance management interface
 */
class BedrockGuildRelationsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger), KoinComponent {

    private val relationService: RelationService by inject()
    private val guildService: GuildService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val canManageRelations = relationService.canManageRelations(player.uniqueId, guild.id)

        // Get relation counts
        val relations = relationService.getGuildRelations(guild.id)
        val allies = relations.count { it.type == RelationType.ALLY && it.isActive() }
        val enemies = relations.count { it.type == RelationType.ENEMY && it.isActive() }
        val truces = relations.count { it.type == RelationType.TRUCE && it.isActive() }

        return SimpleForm.builder()
            .title("${lang.raw("bedrock.relations_management.title")} - ${guild.name}")
            .content("""
                |${lang.raw("bedrock.relations_management.welcome")}
                |
                |${lang.raw("bedrock.relations_management.description")}
                |
                |${lang.raw("bedrock.relations_management.overview")}
                |${lang.raw("bedrock.relations_management.allies")}: $allies
                |${lang.raw("bedrock.relations_management.enemies")}: $enemies
                |${lang.raw("bedrock.relations_management.truces")}: $truces
                |
                |${lang.raw("bedrock.relations_management.notice")}
            """.trimMargin())
            .addButtonWithImage(
                config,
                "${lang.raw("bedrock.relations_management.allies")} ($allies)",
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
            .addButtonWithImage(
                config,
                "${lang.raw("bedrock.relations_management.enemies")} ($enemies)",
                config.cancelIconUrl,
                config.cancelIconPath
            )
            .addButtonWithImage(
                config,
                "${lang.raw("bedrock.relations_management.truces")} ($truces)",
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                lang.raw("bedrock.relations_management.requests"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                lang.raw("bedrock.relations_management.declare_war.title"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
            .addButtonWithImage(
                config,
                lang.raw("bedrock.relations_management.request_alliance.title"),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
            .addButtonWithImage(
                config,
                lang.raw("bedrock.relations_management.request_truce.title"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                lang.raw("bedrock.relations_management.back"),
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
            0 -> openAlliesMenu()
            1 -> openEnemiesMenu()
            2 -> openTrucesMenu()
            3 -> openRequestsMenu()
            4 -> openDeclareWarMenu()
            5 -> openRequestAllianceMenu()
            6 -> openRequestTruceMenu()
            7 -> bedrockNavigator.goBack()
        }
    }

    private fun openAlliesMenu() {
        val config = getBedrockConfig()
        val allies = relationService.getGuildRelationsByType(guild.id, RelationType.ALLY).filter { it.isActive() }

        val form = SimpleForm.builder()
            .title(lang.raw("bedrock.relations_management.allies"))
            .content(
                if (allies.isEmpty()) {
                    lang.raw("bedrock.relations_management.empty.allies")
                } else {
            "${lang.raw("bedrock.relations_management.select.ally")}\n\n${lang.legacy("bedrock.relations_management.allies_count", "count" to allies.size)}"
                }
            )

        allies.forEach { relation ->
            val otherGuildId = relation.getOtherGuild(guild.id)
            val otherGuild = guildService.getGuild(otherGuildId)
            val otherName = otherGuild?.name ?: lang.raw("bedrock.relations_management.unknown_guild")

            form.addButtonWithImage(
                config,
                "🤝 $otherName\n${lang.raw("bedrock.relations_management.formed")}: ${relation.createdAt.toString().substring(0, 10)}",
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.raw("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton < allies.size) {
                    openRelationDetailsMenu(allies.elementAt(clickedButton))
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

    private fun openEnemiesMenu() {
        val config = getBedrockConfig()
        val enemies = relationService.getGuildRelationsByType(guild.id, RelationType.ENEMY).filter { it.isActive() }

        val form = SimpleForm.builder()
            .title(lang.raw("bedrock.relations_management.enemies"))
            .content(
                if (enemies.isEmpty()) {
                    lang.raw("bedrock.relations_management.empty.enemies")
                } else {
            "${lang.raw("bedrock.relations_management.select.enemy")}\n\n${lang.legacy("bedrock.relations_management.enemies_count", "count" to enemies.size)}"
                }
            )

        enemies.forEach { relation ->
            val otherGuildId = relation.getOtherGuild(guild.id)
            val otherGuild = guildService.getGuild(otherGuildId)
            val otherName = otherGuild?.name ?: lang.raw("bedrock.relations_management.unknown_guild")

            form.addButtonWithImage(
                config,
                "⚔ $otherName\n${lang.raw("bedrock.relations_management.declared")}: ${relation.createdAt.toString().substring(0, 10)}",
                config.cancelIconUrl,
                config.cancelIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.raw("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton < enemies.size) {
                    openRelationDetailsMenu(enemies.elementAt(clickedButton))
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

    private fun openTrucesMenu() {
        val config = getBedrockConfig()
        val truces = relationService.getGuildRelationsByType(guild.id, RelationType.TRUCE).filter { it.isActive() }

        val form = SimpleForm.builder()
            .title(lang.raw("bedrock.relations_management.truces"))
            .content(
                if (truces.isEmpty()) {
                    lang.raw("bedrock.relations_management.empty.truces")
                } else {
            "${lang.raw("bedrock.relations_management.select.truce")}\n\n${lang.legacy("bedrock.relations_management.truces_count", "count" to truces.size)}"
                }
            )

        truces.forEach { relation ->
            val otherGuildId = relation.getOtherGuild(guild.id)
            val otherGuild = guildService.getGuild(otherGuildId)
            val otherName = otherGuild?.name ?: lang.raw("bedrock.relations_management.unknown_guild")
            val expiresAt = relation.expiresAt?.toString()?.substring(0, 10) ?: lang.raw("bedrock.relations_management.never")

            form.addButtonWithImage(
                config,
                "🕊 $otherName\n${lang.raw("bedrock.relations_management.expires")}: $expiresAt",
                config.editIconUrl,
                config.editIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.raw("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton < truces.size) {
                    openRelationDetailsMenu(truces.elementAt(clickedButton))
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

    private fun openRequestsMenu() {
        val config = getBedrockConfig()
        val incomingRequests = relationService.getIncomingRequests(guild.id)
        val outgoingRequests = relationService.getOutgoingRequests(guild.id)

        val form = SimpleForm.builder()
            .title(lang.raw("bedrock.relations_management.requests"))
            .content("""
                |${lang.raw("bedrock.relations_management.requests_summary.incoming")}: ${incomingRequests.size}
                |${lang.raw("bedrock.relations_management.requests_summary.outgoing")}: ${outgoingRequests.size}
            """.trimMargin())

        if (incomingRequests.isNotEmpty()) {
            form.addButtonWithImage(
                config,
                lang.raw("bedrock.relations_management.view.incoming"),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
        }

        if (outgoingRequests.isNotEmpty()) {
            form.addButtonWithImage(
                config,
                lang.raw("bedrock.relations_management.view.outgoing"),
                config.editIconUrl,
                config.editIconPath
            )
        }

        if (incomingRequests.isEmpty() && outgoingRequests.isEmpty()) {
            form.content(lang.raw("bedrock.relations_management.empty.requests"))
        }

        form.addButtonWithImage(
            config,
            lang.raw("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                val buttonIndex = when {
                    incomingRequests.isNotEmpty() && outgoingRequests.isNotEmpty() -> clickedButton
                    incomingRequests.isNotEmpty() || outgoingRequests.isNotEmpty() -> clickedButton
                    else -> -1
                }

                when {
                    incomingRequests.isNotEmpty() && clickedButton == 0 -> openIncomingRequestsMenu()
                    outgoingRequests.isNotEmpty() && clickedButton == (if (incomingRequests.isNotEmpty()) 1 else 0) -> openOutgoingRequestsMenu()
                    clickedButton == maxOf(if (incomingRequests.isNotEmpty()) 1 else 0, if (outgoingRequests.isNotEmpty()) 1 else 0) -> getForm() // Back
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
            .title(lang.raw("bedrock.relations_management.declare_war.title"))
            .label(lang.raw("bedrock.relations_management.declare_war.description"))
            .input(
                lang.raw("bedrock.relations_management.declare_war.target.label"),
                lang.raw("bedrock.relations_management.declare_war.target.placeholder")
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
                // Response handling is done in the form builder's validResultHandler
                // This method is kept for interface compatibility
                onFormResponseReceived()
            }
        })
    }

    private fun handleDeclareWarResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        val targetGuildName = response.asInput(0)

        if (targetGuildName.isNullOrBlank()) {
            player.sendMessage(lang.msg("bedrock.relations_management.declare_war.target.required"))
            return
        }

        val targetGuild = guildService.getGuildByName(targetGuildName)
        if (targetGuild == null) {
            player.sendMessage(lang.msg("bedrock.relations_management.declare_war.feedback.not_found"))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(lang.msg("bedrock.relations_management.declare_war.feedback.self"))
            return
        }

        val success = relationService.declareWar(guild.id, targetGuild.id, player.uniqueId)
        if (success != null) {
            player.sendMessage(lang.msg("bedrock.relations_management.declare_war.feedback.declared", "guild" to targetGuild.name))
        } else {
            player.sendMessage(lang.msg("bedrock.relations_management.declare_war.feedback.failed"))
        }

        getForm() // Refresh main menu
    }

    private fun openRequestAllianceMenu() {
        val config = getBedrockConfig()

        val form = CustomForm.builder()
            .title(lang.raw("bedrock.relations_management.request_alliance.title"))
            .label(lang.raw("bedrock.relations_management.request_alliance.description"))
            .input(
                lang.raw("bedrock.relations_management.request_alliance.target.label"),
                lang.raw("bedrock.relations_management.request_alliance.target.placeholder")
            )
            .validResultHandler { response ->
                handleRequestAllianceResponse(response)
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

    private fun handleRequestAllianceResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        val targetGuildName = response.asInput(0)

        if (targetGuildName.isNullOrBlank()) {
            player.sendMessage(lang.msg("bedrock.relations_management.request_alliance.target.required"))
            return
        }

        val targetGuild = guildService.getGuildByName(targetGuildName)
        if (targetGuild == null) {
            player.sendMessage(lang.msg("bedrock.relations_management.request_alliance.feedback.not_found"))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(lang.msg("bedrock.relations_management.request_alliance.feedback.self"))
            return
        }

        val success = relationService.requestAlliance(guild.id, targetGuild.id, player.uniqueId)
        if (success != null) {
            player.sendMessage(lang.msg("bedrock.relations_management.request_alliance.feedback.sent", "guild" to targetGuild.name))
        } else {
            player.sendMessage(lang.msg("bedrock.relations_management.request_alliance.feedback.failed"))
        }

        getForm() // Refresh main menu
    }

    private fun openRequestTruceMenu() {
        val config = getBedrockConfig()

        val form = CustomForm.builder()
            .title(lang.raw("bedrock.relations_management.request_truce.title"))
            .label(lang.raw("bedrock.relations_management.request_truce.description"))
            .input(
                lang.raw("bedrock.relations_management.request_truce.target.label"),
                lang.raw("bedrock.relations_management.request_truce.target.placeholder")
            )
            .input(
                lang.raw("bedrock.relations_management.request_truce.duration.label"),
                lang.raw("bedrock.relations_management.request_truce.duration.placeholder")
            )
            .validResultHandler { response ->
                handleRequestTruceResponse(response)
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

    private fun handleRequestTruceResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        val targetGuildName = response.asInput(0)
        val durationStr = response.asInput(1) ?: "7"

        if (targetGuildName.isNullOrBlank()) {
            player.sendMessage(lang.msg("bedrock.relations_management.request_truce.target.required"))
            return
        }

        val targetGuild = guildService.getGuildByName(targetGuildName)
        if (targetGuild == null) {
            player.sendMessage(lang.msg("bedrock.relations_management.request_truce.feedback.not_found"))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(lang.msg("bedrock.relations_management.request_truce.feedback.self"))
            return
        }

        val duration = try {
            Duration.ofDays(durationStr.toLong().coerceIn(1, 365))
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            Duration.ofDays(7)
        }

        val success = relationService.requestTruce(guild.id, targetGuild.id, player.uniqueId, duration)
        if (success != null) {
            player.sendMessage(lang.msg("bedrock.relations_management.request_truce.feedback.sent", "guild" to targetGuild.name))
        } else {
            player.sendMessage(lang.msg("bedrock.relations_management.request_truce.feedback.failed"))
        }

        getForm() // Refresh main menu
    }

    private fun openRelationDetailsMenu(relation: net.lumalyte.lg.domain.entities.Relation) {
        val otherGuildId = relation.getOtherGuild(guild.id)
        val otherGuild = guildService.getGuild(otherGuildId)
        val otherName = otherGuild?.name ?: lang.raw("bedrock.relations_management.unknown_guild")

        val relationType = when (relation.type) {
            RelationType.ALLY -> lang.raw("bedrock.relations_management.relation_type.ally")
            RelationType.ENEMY -> lang.raw("bedrock.relations_management.relation_type.enemy")
            RelationType.TRUCE -> lang.raw("bedrock.relations_management.relation_type.truce")
            RelationType.NEUTRAL -> lang.raw("bedrock.relations_management.relation_type.neutral")
        }

        val status = if (relation.isActive()) {
            lang.raw("bedrock.relations_management.status.active")
        } else {
            lang.raw("bedrock.relations_management.status.inactive")
        }

        val form = SimpleForm.builder()
            .title("${lang.raw("bedrock.relations_management.relation_details")} - $otherName")
            .content("""
                |${lang.raw("bedrock.relations_management.other_guild")}: $otherName
                |${lang.raw("bedrock.relations_management.type")}: $relationType
                |${lang.raw("bedrock.relations_management.status.label")}: $status
                |${lang.raw("bedrock.relations_management.formed")}: ${relation.createdAt.toString().substring(0, 10)}
                |${if (relation.expiresAt != null) "${lang.raw("bedrock.relations_management.expires")}: ${relation.expiresAt.toString().substring(0, 10)}" else ""}
            """.trimMargin())
            .addButtonWithImage(
                getBedrockConfig(),
                lang.raw("bedrock.relations_management.back"),
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

    private fun openIncomingRequestsMenu() {
        val config = getBedrockConfig()
        val incomingRequests = relationService.getIncomingRequests(guild.id)

        val form = SimpleForm.builder()
            .title(lang.raw("bedrock.relations_management.incoming_requests"))
            .content(lang.raw("bedrock.relations_management.select.request"))

        incomingRequests.forEach { request ->
            val requestingGuild = guildService.getGuild(request.getOtherGuild(guild.id))
            val requestingName = requestingGuild?.name ?: lang.raw("bedrock.relations_management.unknown_guild")

            val requestType = when (request.type) {
                RelationType.ALLY -> lang.raw("bedrock.relations_management.request_type.alliance")
                RelationType.TRUCE -> lang.raw("bedrock.relations_management.request_type.truce")
                RelationType.NEUTRAL -> lang.raw("bedrock.relations_management.request_type.peace")
                else -> request.type.name
            }

            form.addButtonWithImage(
                config,
                "${lang.raw("bedrock.relations_management.from")}: $requestingName\n${lang.raw("bedrock.relations_management.type")}: $requestType",
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.raw("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton < incomingRequests.size) {
                    openRequestActionMenu(incomingRequests.elementAt(clickedButton), true)
                } else {
                    openRequestsMenu() // Back to requests menu
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                openRequestsMenu() // Back to requests menu
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

    private fun openOutgoingRequestsMenu() {
        val config = getBedrockConfig()
        val outgoingRequests = relationService.getOutgoingRequests(guild.id)

        val form = SimpleForm.builder()
            .title(lang.raw("bedrock.relations_management.outgoing_requests"))
            .content(lang.raw("bedrock.relations_management.select.request"))

        outgoingRequests.forEach { request ->
            val targetGuild = guildService.getGuild(request.getOtherGuild(guild.id))
            val targetName = targetGuild?.name ?: lang.raw("bedrock.relations_management.unknown_guild")

            val requestType = when (request.type) {
                RelationType.ALLY -> lang.raw("bedrock.relations_management.request_type.alliance")
                RelationType.TRUCE -> lang.raw("bedrock.relations_management.request_type.truce")
                RelationType.NEUTRAL -> lang.raw("bedrock.relations_management.request_type.peace")
                else -> request.type.name
            }

            form.addButtonWithImage(
                config,
                "${lang.raw("bedrock.relations_management.to")}: $targetName\n${lang.raw("bedrock.relations_management.type")}: $requestType",
                config.editIconUrl,
                config.editIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.raw("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton < outgoingRequests.size) {
                    openRequestActionMenu(outgoingRequests.elementAt(clickedButton), false)
                } else {
                    openRequestsMenu() // Back to requests menu
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                openRequestsMenu() // Back to requests menu
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

    private fun openRequestActionMenu(request: net.lumalyte.lg.domain.entities.Relation, isIncoming: Boolean) {
        val config = getBedrockConfig()

        val form = SimpleForm.builder()
            .title(if (isIncoming) lang.raw("bedrock.relations_management.request_action.incoming") else lang.raw("bedrock.relations_management.request_action.outgoing"))
            .content(lang.raw("bedrock.relations_management.request_action.description"))

        if (isIncoming) {
            form.addButtonWithImage(
                config,
                lang.raw("bedrock.relations_management.request_action.accept"),
                config.confirmIconUrl,
                config.confirmIconPath
            )
            form.addButtonWithImage(
                config,
                lang.raw("bedrock.relations_management.request_action.reject"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
        } else {
            form.addButtonWithImage(
                config,
                lang.raw("bedrock.relations_management.request_action.cancel"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
        }

        form.addButtonWithImage(
            config,
            lang.raw("bedrock.relations_management.back"),
            config.closeIconUrl,
            config.closeIconPath
        )

        val formWithHandler = form
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                when {
                    isIncoming && clickedButton == 0 -> {
                        // Accept request
                        when (request.type) {
                            RelationType.ALLY -> {
                                val success = relationService.acceptAlliance(request.id, guild.id, player.uniqueId)
                                if (success != null) {
                                    player.sendMessage(lang.msg("bedrock.relations_management.request_action.feedback.alliance_accepted"))
                                } else {
                                    player.sendMessage(lang.msg("bedrock.relations_management.request_action.feedback.failed"))
                                }
                            }
                            RelationType.TRUCE -> {
                                val success = relationService.acceptTruce(request.id, guild.id, player.uniqueId)
                                if (success != null) {
                                    player.sendMessage(lang.msg("bedrock.relations_management.request_action.feedback.truce_accepted"))
                                } else {
                                    player.sendMessage(lang.msg("bedrock.relations_management.request_action.feedback.failed"))
                                }
                            }
                            RelationType.NEUTRAL -> {
                                val success = relationService.acceptUnenemy(request.id, guild.id, player.uniqueId)
                                if (success != null) {
                                    player.sendMessage(lang.msg("bedrock.relations_management.request_action.feedback.peace_accepted"))
                                } else {
                                    player.sendMessage(lang.msg("bedrock.relations_management.request_action.feedback.failed"))
                                }
                            }
                            else -> player.sendMessage(lang.msg("bedrock.relations_management.request_action.feedback.failed"))
                        }
                        openRequestsMenu()
                    }
                    isIncoming && clickedButton == 1 -> {
                        // Reject request
                        val success = relationService.rejectRequest(request.id, guild.id, player.uniqueId)
                        if (success) {
                            player.sendMessage(lang.msg("bedrock.relations_management.request_action.feedback.rejected"))
                        } else {
                            player.sendMessage(lang.msg("bedrock.relations_management.request_action.feedback.failed"))
                        }
                        openRequestsMenu()
                    }
                    !isIncoming && clickedButton == 0 -> {
                        // Cancel request
                        val success = relationService.cancelRequest(request.id, guild.id, player.uniqueId)
                        if (success) {
                            player.sendMessage(lang.msg("bedrock.relations_management.request_action.feedback.cancelled"))
                        } else {
                            player.sendMessage(lang.msg("bedrock.relations_management.request_action.feedback.failed"))
                        }
                        openRequestsMenu()
                    }
                    else -> openRequestsMenu() // Back
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                openRequestsMenu() // Back to requests menu
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

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
        onFormResponseReceived()
    }
}
