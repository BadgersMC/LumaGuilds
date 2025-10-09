package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Bedrock Edition guild rank management menu using Cumulus CustomForm
 * Provides comprehensive rank configuration with all component types
 */
class BedrockGuildRankManagementMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val selectedRank: Rank? = null, // For editing existing ranks
    logger: Logger,
    messageService: MessageService
) : BaseBedrockMenu(menuNavigator, player, logger, messageService) {

    private val rankService: RankService by inject()
    private val configService: ConfigService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val editIcon = BedrockFormUtils.createFormImage(config, config.editIconUrl, config.editIconPath)

        val formBuilder = CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "form.title.guild.rank.management")} - ${guild.name}")
            .apply { editIcon?.let { icon(it) } }

        // Info section
        formBuilder.label(createInfoSection())

        // Mode selection: Create or Edit
        val modes = listOf(
            bedrockLocalization.getBedrockString(player, "rank.management.mode.create"),
            bedrockLocalization.getBedrockString(player, "rank.management.mode.edit")
        )
        val defaultMode = if (selectedRank != null) 1 else 0
        formBuilder.addLocalizedDropdown(
            player, bedrockLocalization,
            "rank.management.mode.label",
            listOf("rank.management.mode.create", "rank.management.mode.edit"),
            if (selectedRank != null) "rank.management.mode.edit" else "rank.management.mode.create"
        )

        // Rank name input
        formBuilder.addLocalizedInput(
            player, bedrockLocalization,
            "rank.management.name.label",
            "rank.management.name.placeholder",
            selectedRank?.name ?: ""
        )

        // Existing ranks dropdown (for editing)
        val existingRanks = rankService.listRanks(guild.id).sortedBy { it.priority }
        formBuilder.addLocalizedDropdown(
            player, bedrockLocalization,
            "rank.management.select.rank.label",
            existingRanks.map { "rank.${it.name}" }, // This would need proper keys
            selectedRank?.name ?: existingRanks.firstOrNull()?.name ?: ""
        )

        // Priority step slider - using numeric priorities
        val maxPriority = existingRanks.maxOfOrNull { it.priority } ?: 0
        val priorityOptions = (0..maxPriority + 5).map { it.toString() }
        val selectedPriority = selectedRank?.priority ?: maxPriority + 1
        formBuilder.stepSlider(
            bedrockLocalization.getBedrockString(player, "rank.management.priority.label"),
            priorityOptions,
            selectedPriority.coerceIn(0, priorityOptions.size - 1)
        )

        // Permission toggles organized by category
        formBuilder.addLocalizedLabel(
            player, bedrockLocalization,
            "rank.management.permissions.guild"
        )
        RankPermission.values().forEach { permission ->
            formBuilder.addLocalizedToggle(
                player, bedrockLocalization,
                "permission.${permission.name.lowercase()}",
                selectedRank?.permissions?.contains(permission) ?: false
            )
        }

        // Member limits slider (placeholder for future feature)
        formBuilder.addLocalizedSlider(
            player, bedrockLocalization,
            "rank.management.member.limit.label",
            0f, 100f, 1f,
            selectedRank?.let { 50f } ?: 25f
        )

        // Validation section
        formBuilder.label(createValidationSection())

        formBuilder.validResultHandler { response ->
            handleFormResponse(response)
        }

        formBuilder.closedOrInvalidResultHandler { _, _ ->
            navigateBack()
        }

        return formBuilder.build()
    }

    private fun createInfoSection(): String {
        val rankCount = rankService.getRankCount(guild.id)
        return """
            |${bedrockLocalization.getBedrockString(player, "rank.management.description")}
            |
            |Guild: ${guild.name}
            |Total Ranks: $rankCount
            |Configure rank permissions and hierarchy below.
            |
            |${if (selectedRank != null)
                bedrockLocalization.getBedrockString(player, "rank.management.info.editing", selectedRank.name)
            else
                bedrockLocalization.getBedrockString(player, "rank.management.info.creating")
            }
        """.trimMargin()
    }

    private fun createSectionHeader(title: String): String {
        return "<yellow><bold>$title"
    }

    private fun getPermissionDisplayName(permission: RankPermission): String {
        return when (permission) {
            RankPermission.MANAGE_RANKS -> bedrockLocalization.getBedrockString(player, "permission.manage.ranks")
            RankPermission.MANAGE_MEMBERS -> bedrockLocalization.getBedrockString(player, "permission.manage.members")
            RankPermission.MANAGE_BANNER -> bedrockLocalization.getBedrockString(player, "permission.manage.banner")
            RankPermission.MANAGE_EMOJI -> bedrockLocalization.getBedrockString(player, "permission.manage.emoji")
            RankPermission.MANAGE_DESCRIPTION -> bedrockLocalization.getBedrockString(player, "permission.manage.description")
            RankPermission.MANAGE_GUILD_NAME -> bedrockLocalization.getBedrockString(player, "permission.manage.guild.name")
            RankPermission.MANAGE_BANK_SECURITY -> bedrockLocalization.getBedrockString(player, "permission.manage.bank.security")
            RankPermission.ACTIVATE_EMERGENCY_FREEZE -> bedrockLocalization.getBedrockString(player, "permission.activate.emergency.freeze")
            RankPermission.DEACTIVATE_EMERGENCY_FREEZE -> bedrockLocalization.getBedrockString(player, "permission.deactivate.emergency.freeze")
            RankPermission.VIEW_SECURITY_AUDITS -> bedrockLocalization.getBedrockString(player, "permission.view.security.audits")
            RankPermission.MANAGE_BUDGETS -> bedrockLocalization.getBedrockString(player, "permission.manage.budgets")
            RankPermission.MANAGE_HOME -> bedrockLocalization.getBedrockString(player, "permission.manage.home")
            RankPermission.MANAGE_MODE -> bedrockLocalization.getBedrockString(player, "permission.manage.mode")
            RankPermission.MANAGE_GUILD_SETTINGS -> bedrockLocalization.getBedrockString(player, "permission.manage.guild.settings")
            RankPermission.MANAGE_RELATIONS -> bedrockLocalization.getBedrockString(player, "permission.manage.relations")
            RankPermission.DECLARE_WAR -> bedrockLocalization.getBedrockString(player, "permission.declare.war")
            RankPermission.ACCEPT_ALLIANCES -> bedrockLocalization.getBedrockString(player, "permission.accept.alliances")
            RankPermission.MANAGE_PARTIES -> bedrockLocalization.getBedrockString(player, "permission.manage.parties")
            RankPermission.SEND_PARTY_REQUESTS -> bedrockLocalization.getBedrockString(player, "permission.send.party.requests")
            RankPermission.ACCEPT_PARTY_INVITES -> bedrockLocalization.getBedrockString(player, "permission.accept.party.invites")
            RankPermission.DEPOSIT_TO_BANK -> bedrockLocalization.getBedrockString(player, "permission.deposit.bank")
            RankPermission.WITHDRAW_FROM_BANK -> bedrockLocalization.getBedrockString(player, "permission.withdraw.bank")
            RankPermission.VIEW_BANK_TRANSACTIONS -> bedrockLocalization.getBedrockString(player, "permission.view.bank.transactions")
            RankPermission.EXPORT_BANK_DATA -> bedrockLocalization.getBedrockString(player, "permission.export.bank.data")
            RankPermission.MANAGE_BANK_SETTINGS -> bedrockLocalization.getBedrockString(player, "permission.manage.bank.settings")
            RankPermission.SEND_ANNOUNCEMENTS -> bedrockLocalization.getBedrockString(player, "permission.send.announcements")
            RankPermission.SEND_PINGS -> bedrockLocalization.getBedrockString(player, "permission.send.pings")
            RankPermission.MODERATE_CHAT -> bedrockLocalization.getBedrockString(player, "permission.moderate.chat")
            RankPermission.MANAGE_CLAIMS -> bedrockLocalization.getBedrockString(player, "permission.manage.claims")
            RankPermission.MANAGE_FLAGS -> bedrockLocalization.getBedrockString(player, "permission.manage.flags")
            RankPermission.MANAGE_PERMISSIONS -> bedrockLocalization.getBedrockString(player, "permission.manage.permissions")
            RankPermission.CREATE_CLAIMS -> bedrockLocalization.getBedrockString(player, "permission.create.claims")
            RankPermission.DELETE_CLAIMS -> bedrockLocalization.getBedrockString(player, "permission.delete.claims")
            RankPermission.ACCESS_ADMIN_COMMANDS -> bedrockLocalization.getBedrockString(player, "permission.access.admin.commands")
            RankPermission.BYPASS_RESTRICTIONS -> bedrockLocalization.getBedrockString(player, "permission.bypass.restrictions")
            RankPermission.VIEW_AUDIT_LOGS -> bedrockLocalization.getBedrockString(player, "permission.view.audit.logs")
            RankPermission.MANAGE_INTEGRATIONS -> bedrockLocalization.getBedrockString(player, "permission.manage.integrations")
            
            // Additional permissions
            RankPermission.DEPOSIT_MONEY -> bedrockLocalization.getBedrockString(player, "permission.deposit.money")
            RankPermission.WITHDRAW_MONEY -> bedrockLocalization.getBedrockString(player, "permission.withdraw.money") 
            RankPermission.VIEW_BANK_HISTORY -> bedrockLocalization.getBedrockString(player, "permission.view.bank.history")
            RankPermission.USE_CHAT -> bedrockLocalization.getBedrockString(player, "permission.use.chat")
            RankPermission.MANAGE_CHAT_SETTINGS -> bedrockLocalization.getBedrockString(player, "permission.manage.chat.settings")
            RankPermission.CLAIM_LAND -> bedrockLocalization.getBedrockString(player, "permission.claim.land")
            RankPermission.UNCLAIM_LAND -> bedrockLocalization.getBedrockString(player, "permission.unclaim.land")
            
            // Security
            RankPermission.OVERRIDE_PROTECTION -> bedrockLocalization.getBedrockString(player, "permission.override.protection")
            RankPermission.BYPASS_COOLDOWNS -> bedrockLocalization.getBedrockString(player, "permission.bypass.cooldowns")
            RankPermission.MANAGE_SECURITY -> bedrockLocalization.getBedrockString(player, "permission.manage.security")

            // Fallback for any unhandled permissions
            else -> permission.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private fun createValidationSection(): String {
        return """
            |${createSectionHeader(bedrockLocalization.getBedrockString(player, "form.validation.title"))}
            |• ${bedrockLocalization.getBedrockString(player, "validation.rank.name.too.short")}
            |• ${bedrockLocalization.getBedrockString(player, "validation.rank.name.too.long")}
            |• ${bedrockLocalization.getBedrockString(player, "validation.rank.permissions.required")}
            |• ${bedrockLocalization.getBedrockString(player, "validation.rank.priority.conflict")}
        """.trimMargin()
    }

    private fun handleFormResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        try {
            onFormResponseReceived()

            val modeIndex = response.next() as? Int ?: 0
            val rankName = response.next() as? String ?: ""
            val selectedRankIndex = response.next() as? Int ?: 0
            val priorityIndex = response.next() as? Int ?: 0
            val memberLimit = response.next() as? Float ?: 25f

            // Collect permissions from toggles
            val permissions = mutableSetOf<RankPermission>()
            for (permission in RankPermission.values()) {
                val hasPermission = response.next() as? Boolean ?: false
                if (hasPermission) {
                    permissions.add(permission)
                }
            }

            // Validate permissions
            if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RANKS)) {
                player.sendMessage("<red>[ERROR] ${localize("rank.management.error.no.permission")}")
                navigateBack()
                return
            }

            // Validate rank name
            if (rankName.length !in 1..24) {
                player.sendMessage("<red>[ERROR] ${localize("rank.management.validation.name.length.error", 1, 24)}")
                reopen()
                return
            }

            // Check for duplicate names
            val existingRank = rankService.getRankByName(guild.id, rankName)
            if (existingRank != null && existingRank != selectedRank) {
                player.sendMessage("<red>[ERROR] ${localize("rank.management.validation.name.duplicate", rankName)}")
                reopen()
                return
            }

            // Validate priority conflicts
            val existingRanks = rankService.listRanks(guild.id)
            val newPriority = priorityIndex
            val priorityConflict = existingRanks.any { it.priority == newPriority && it != selectedRank }
            if (priorityConflict) {
                player.sendMessage("<red>[ERROR] ${localize("rank.management.validation.priority.conflict", newPriority)}")
                reopen()
                return
            }

            // Process based on mode
            if (modeIndex == 0) {
                // Create new rank
                val createdRank = rankService.addRank(
                    guildId = guild.id,
                    name = rankName,
                    permissions = permissions,
                    actorId = player.uniqueId
                )

                if (createdRank != null) {
                    // Update priority if needed
                    if (createdRank.priority != newPriority) {
                        val updatedRank = createdRank.copy(priority = newPriority)
                        rankService.updateRank(updatedRank)
                    }

                    player.sendMessage("<green>[SUCCESS] ${localize("rank.management.success.created", rankName)}")
                } else {
                    player.sendMessage("<red>[ERROR] ${localize("rank.management.error.create.failed")}")
                }
            } else {
                // Edit existing rank
                val rankToEdit = if (selectedRank != null) selectedRank else {
                    val ranks = rankService.listRanks(guild.id).toList()
                    if (selectedRankIndex < ranks.size) ranks[selectedRankIndex] else null
                }

                if (rankToEdit != null) {
                    val updatedRank = rankToEdit.copy(
                        name = rankName,
                        priority = newPriority,
                        permissions = permissions
                    )

                    val success = rankService.updateRank(updatedRank)
                    if (success) {
                        player.sendMessage("<green>[SUCCESS] ${localize("rank.management.success.updated", rankName)}")
                    } else {
                        player.sendMessage("<red>[ERROR] ${localize("rank.management.error.update.failed")}")
                    }
                } else {
                    player.sendMessage("<red>[ERROR] ${localize("rank.management.error.rank.not.found")}")
                }
            }

            navigateBack()

        } catch (e: Exception) {
            logger.warning("Error processing rank management form response: ${e.message}")
            player.sendMessage("<red>[ERROR] ${localize("form.error.processing")}")
            navigateBack()
        }
    }

    override fun shouldCacheForm(): Boolean = false // Dynamic content based on selected rank

    override fun createCacheKey(): String {
        return "${this::class.simpleName}:${player.uniqueId}:${guild.id}:${selectedRank?.id}"
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        onFormResponseReceived()
    }
}

