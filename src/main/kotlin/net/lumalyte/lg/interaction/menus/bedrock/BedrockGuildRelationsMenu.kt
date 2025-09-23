package net.lumalyte.lg.interaction.menus.bedrock

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

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val canManageRelations = relationService.canManageRelations(player.uniqueId, guild.id)

        // Get relation counts
        val relations = relationService.getGuildRelations(guild.id)
        val allies = relations.count { it.type == RelationType.ALLY && it.isActive() }
        val enemies = relations.count { it.type == RelationType.ENEMY && it.isActive() }
        val truces = relations.count { it.type == RelationType.TRUCE && it.isActive() }

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.relations.management.title")} - ${guild.name}")
            .content("""
                |${bedrockLocalization.getBedrockString(player, "guild.relations.management.welcome")}
                |
                |${bedrockLocalization.getBedrockString(player, "guild.relations.management.description")}
                |
                |${bedrockLocalization.getBedrockString(player, "guild.relations.management.overview")}
                |${bedrockLocalization.getBedrockString(player, "guild.relations.management.allies")}: $allies
                |${bedrockLocalization.getBedrockString(player, "guild.relations.management.enemies")}: $enemies
                |${bedrockLocalization.getBedrockString(player, "guild.relations.management.truces")}: $truces
                |
                |${bedrockLocalization.getBedrockString(player, "menu.notice")}
            """.trimMargin())
            .addButtonWithImage(
                config,
                "${bedrockLocalization.getBedrockString(player, "guild.relations.management.allies")} ($allies)",
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
            .addButtonWithImage(
                config,
                "${bedrockLocalization.getBedrockString(player, "guild.relations.management.enemies")} ($enemies)",
                config.cancelIconUrl,
                config.cancelIconPath
            )
            .addButtonWithImage(
                config,
                "${bedrockLocalization.getBedrockString(player, "guild.relations.management.truces")} ($truces)",
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.relations.management.requests"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.relations.management.declare.war"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.relations.management.request.alliance"),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.relations.management.request.truce"),
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
            .title(bedrockLocalization.getBedrockString(player, "guild.relations.management.allies"))
            .content(
                if (allies.isEmpty()) {
                    bedrockLocalization.getBedrockString(player, "guild.relations.management.no.allies")
                } else {
                    "${bedrockLocalization.getBedrockString(player, "guild.relations.management.select.ally")}\n\n${bedrockLocalization.getBedrockString(player, "guild.relations.management.allies.count", allies.size)}"
                }
            )

        allies.forEach { relation ->
            val otherGuildId = relation.getOtherGuild(guild.id)
            val otherGuild = guildService.getGuild(otherGuildId)
            val otherName = otherGuild?.name ?: bedrockLocalization.getBedrockString(player, "guild.relations.management.unknown.guild")

            form.addButtonWithImage(
                config,
                "🤝 $otherName\n${bedrockLocalization.getBedrockString(player, "guild.relations.management.formed")}: ${relation.createdAt.toString().substring(0, 10)}",
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
            .title(bedrockLocalization.getBedrockString(player, "guild.relations.management.enemies"))
            .content(
                if (enemies.isEmpty()) {
                    bedrockLocalization.getBedrockString(player, "guild.relations.management.no.enemies")
                } else {
                    "${bedrockLocalization.getBedrockString(player, "guild.relations.management.select.enemy")}\n\n${bedrockLocalization.getBedrockString(player, "guild.relations.management.enemies.count", enemies.size)}"
                }
            )

        enemies.forEach { relation ->
            val otherGuildId = relation.getOtherGuild(guild.id)
            val otherGuild = guildService.getGuild(otherGuildId)
            val otherName = otherGuild?.name ?: bedrockLocalization.getBedrockString(player, "guild.relations.management.unknown.guild")

            form.addButtonWithImage(
                config,
                "⚔️ $otherName\n${bedrockLocalization.getBedrockString(player, "guild.relations.management.declared")}: ${relation.createdAt.toString().substring(0, 10)}",
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
            .title(bedrockLocalization.getBedrockString(player, "guild.relations.management.truces"))
            .content(
                if (truces.isEmpty()) {
                    bedrockLocalization.getBedrockString(player, "guild.relations.management.no.truces")
                } else {
                    "${bedrockLocalization.getBedrockString(player, "guild.relations.management.select.truce")}\n\n${bedrockLocalization.getBedrockString(player, "guild.relations.management.truces.count", truces.size)}"
                }
            )

        truces.forEach { relation ->
            val otherGuildId = relation.getOtherGuild(guild.id)
            val otherGuild = guildService.getGuild(otherGuildId)
            val otherName = otherGuild?.name ?: bedrockLocalization.getBedrockString(player, "guild.relations.management.unknown.guild")
            val expiresAt = relation.expiresAt?.toString()?.substring(0, 10) ?: bedrockLocalization.getBedrockString(player, "guild.relations.management.never")

            form.addButtonWithImage(
                config,
                "🕊️ $otherName\n${bedrockLocalization.getBedrockString(player, "guild.relations.management.expires")}: $expiresAt",
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
            .title(bedrockLocalization.getBedrockString(player, "guild.relations.management.requests"))
            .content("""
                |${bedrockLocalization.getBedrockString(player, "guild.relations.management.requests.incoming")}: ${incomingRequests.size}
                |${bedrockLocalization.getBedrockString(player, "guild.relations.management.requests.outgoing")}: ${outgoingRequests.size}
            """.trimMargin())

        if (incomingRequests.isNotEmpty()) {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.relations.management.view.incoming.requests"),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
        }

        if (outgoingRequests.isNotEmpty()) {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.relations.management.view.outgoing.requests"),
                config.editIconUrl,
                config.editIconPath
            )
        }

        if (incomingRequests.isEmpty() && outgoingRequests.isEmpty()) {
            form.content(bedrockLocalization.getBedrockString(player, "guild.relations.management.no.requests"))
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
            .title(bedrockLocalization.getBedrockString(player, "guild.relations.management.declare.war"))
            .label(bedrockLocalization.getBedrockString(player, "guild.relations.management.declare.war.description"))
            .addLocalizedInput(
                player, bedrockLocalization,
                "guild.relations.management.declare.war.target.label",
                "guild.relations.management.declare.war.target.placeholder"
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
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.declare.war.target.required"))
            return
        }

        val targetGuild = guildService.getGuildByName(targetGuildName)
        if (targetGuild == null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.declare.war.guild.not.found"))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.declare.war.self"))
            return
        }

        val success = relationService.declareWar(guild.id, targetGuild.id, player.uniqueId)
        if (success != null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.declare.war.declared", targetGuild.name))
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.declare.war.failed"))
        }

        getForm() // Refresh main menu
    }

    private fun openRequestAllianceMenu() {
        val config = getBedrockConfig()

        val form = CustomForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.alliance"))
            .label(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.alliance.description"))
            .addLocalizedInput(
                player, bedrockLocalization,
                "guild.relations.management.request.alliance.target.label",
                "guild.relations.management.request.alliance.target.placeholder"
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
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.alliance.target.required"))
            return
        }

        val targetGuild = guildService.getGuildByName(targetGuildName)
        if (targetGuild == null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.alliance.guild.not.found"))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.alliance.self"))
            return
        }

        val success = relationService.requestAlliance(guild.id, targetGuild.id, player.uniqueId)
        if (success != null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.alliance.sent", targetGuild.name))
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.alliance.failed"))
        }

        getForm() // Refresh main menu
    }

    private fun openRequestTruceMenu() {
        val config = getBedrockConfig()

        val form = CustomForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.truce"))
            .label(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.truce.description"))
            .addLocalizedInput(
                player, bedrockLocalization,
                "guild.relations.management.request.truce.target.label",
                "guild.relations.management.request.truce.target.placeholder"
            )
            .addLocalizedInput(
                player, bedrockLocalization,
                "guild.relations.management.request.truce.duration.label",
                "guild.relations.management.request.truce.duration.placeholder"
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
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.truce.target.required"))
            return
        }

        val targetGuild = guildService.getGuildByName(targetGuildName)
        if (targetGuild == null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.truce.guild.not.found"))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.truce.self"))
            return
        }

        val duration = try {
            Duration.ofDays(durationStr.toLong().coerceIn(1, 365))
        } catch (e: Exception) {
            Duration.ofDays(7)
        }

        val success = relationService.requestTruce(guild.id, targetGuild.id, player.uniqueId, duration)
        if (success != null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.truce.sent", targetGuild.name))
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.truce.failed"))
        }

        getForm() // Refresh main menu
    }

    private fun openRelationDetailsMenu(relation: net.lumalyte.lg.domain.entities.Relation) {
        val otherGuildId = relation.getOtherGuild(guild.id)
        val otherGuild = guildService.getGuild(otherGuildId)
        val otherName = otherGuild?.name ?: bedrockLocalization.getBedrockString(player, "guild.relations.management.unknown.guild")

        val relationType = when (relation.type) {
            RelationType.ALLY -> bedrockLocalization.getBedrockString(player, "guild.relations.management.ally")
            RelationType.ENEMY -> bedrockLocalization.getBedrockString(player, "guild.relations.management.enemy")
            RelationType.TRUCE -> bedrockLocalization.getBedrockString(player, "guild.relations.management.truce")
            RelationType.NEUTRAL -> bedrockLocalization.getBedrockString(player, "guild.relations.management.neutral")
        }

        val status = if (relation.isActive()) {
            bedrockLocalization.getBedrockString(player, "guild.relations.management.active")
        } else {
            bedrockLocalization.getBedrockString(player, "guild.relations.management.inactive")
        }

        val form = SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.relations.management.relation.details")} - $otherName")
            .content("""
                |${bedrockLocalization.getBedrockString(player, "guild.relations.management.other.guild")}: $otherName
                |${bedrockLocalization.getBedrockString(player, "guild.relations.management.type")}: $relationType
                |${bedrockLocalization.getBedrockString(player, "guild.relations.management.status")}: $status
                |${bedrockLocalization.getBedrockString(player, "guild.relations.management.formed")}: ${relation.createdAt.toString().substring(0, 10)}
                |${if (relation.expiresAt != null) "${bedrockLocalization.getBedrockString(player, "guild.relations.management.expires")}: ${relation.expiresAt.toString().substring(0, 10)}" else ""}
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
            .title(bedrockLocalization.getBedrockString(player, "guild.relations.management.incoming.requests"))
            .content(bedrockLocalization.getBedrockString(player, "guild.relations.management.select.request"))

        incomingRequests.forEach { request ->
            val requestingGuild = guildService.getGuild(request.getOtherGuild(guild.id))
            val requestingName = requestingGuild?.name ?: bedrockLocalization.getBedrockString(player, "guild.relations.management.unknown.guild")

            val requestType = when (request.type) {
                RelationType.ALLY -> bedrockLocalization.getBedrockString(player, "guild.relations.management.alliance.request")
                RelationType.TRUCE -> bedrockLocalization.getBedrockString(player, "guild.relations.management.truce.request")
                RelationType.NEUTRAL -> bedrockLocalization.getBedrockString(player, "guild.relations.management.peace.request")
                else -> request.type.name
            }

            form.addButtonWithImage(
                config,
                "${bedrockLocalization.getBedrockString(player, "guild.relations.management.from")}: $requestingName\n${bedrockLocalization.getBedrockString(player, "guild.relations.management.type")}: $requestType",
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
            .title(bedrockLocalization.getBedrockString(player, "guild.relations.management.outgoing.requests"))
            .content(bedrockLocalization.getBedrockString(player, "guild.relations.management.select.request"))

        outgoingRequests.forEach { request ->
            val targetGuild = guildService.getGuild(request.getOtherGuild(guild.id))
            val targetName = targetGuild?.name ?: bedrockLocalization.getBedrockString(player, "guild.relations.management.unknown.guild")

            val requestType = when (request.type) {
                RelationType.ALLY -> bedrockLocalization.getBedrockString(player, "guild.relations.management.alliance.request")
                RelationType.TRUCE -> bedrockLocalization.getBedrockString(player, "guild.relations.management.truce.request")
                RelationType.NEUTRAL -> bedrockLocalization.getBedrockString(player, "guild.relations.management.peace.request")
                else -> request.type.name
            }

            form.addButtonWithImage(
                config,
                "${bedrockLocalization.getBedrockString(player, "guild.relations.management.to")}: $targetName\n${bedrockLocalization.getBedrockString(player, "guild.relations.management.type")}: $requestType",
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
            .title(if (isIncoming) bedrockLocalization.getBedrockString(player, "guild.relations.management.request.action.incoming") else bedrockLocalization.getBedrockString(player, "guild.relations.management.request.action.outgoing"))
            .content(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.action.description"))

        if (isIncoming) {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.relations.management.request.accept"),
                config.confirmIconUrl,
                config.confirmIconPath
            )
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.relations.management.request.reject"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
        } else {
            form.addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.relations.management.request.cancel"),
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
                        // Accept request
                        when (request.type) {
                            RelationType.ALLY -> {
                                val success = relationService.acceptAlliance(request.id, guild.id, player.uniqueId)
                                if (success != null) {
                                    player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.alliance.accepted"))
                                } else {
                                    player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.failed"))
                                }
                            }
                            RelationType.TRUCE -> {
                                val success = relationService.acceptTruce(request.id, guild.id, player.uniqueId)
                                if (success != null) {
                                    player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.truce.accepted"))
                                } else {
                                    player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.failed"))
                                }
                            }
                            RelationType.NEUTRAL -> {
                                val success = relationService.acceptUnenemy(request.id, guild.id, player.uniqueId)
                                if (success != null) {
                                    player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.peace.accepted"))
                                } else {
                                    player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.failed"))
                                }
                            }
                            else -> player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.failed"))
                        }
                        openRequestsMenu()
                    }
                    isIncoming && clickedButton == 1 -> {
                        // Reject request
                        val success = relationService.rejectRequest(request.id, guild.id, player.uniqueId)
                        if (success) {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.rejected"))
                        } else {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.failed"))
                        }
                        openRequestsMenu()
                    }
                    !isIncoming && clickedButton == 0 -> {
                        // Cancel request
                        val success = relationService.cancelRequest(request.id, guild.id, player.uniqueId)
                        if (success) {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.cancelled"))
                        } else {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.relations.management.request.failed"))
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
