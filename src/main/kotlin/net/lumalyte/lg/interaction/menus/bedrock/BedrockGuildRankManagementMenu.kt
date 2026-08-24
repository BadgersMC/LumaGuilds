package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
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

/**
 * Bedrock Edition guild rank management menu using Cumulus CustomForm
 * Provides comprehensive rank configuration with all component types
 */
class BedrockGuildRankManagementMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val selectedRank: Rank? = null, // For editing existing ranks
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val rankService: RankService by inject()
    private val configService: ConfigService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val editIcon = BedrockFormUtils.createFormImage(config, config.editIconUrl, config.editIconPath)

        val formBuilder = CustomForm.builder()
            .title(lang.bedrock("bedrock.rank_management.title", "guild" to guild.name))
            .apply { editIcon?.let { icon(it) } }

        // Info section
        formBuilder.label(createInfoSection())

        // Mode selection: Create or Edit
        val defaultMode = if (selectedRank != null) 1 else 0
        formBuilder.dropdown(
            lang.bedrock("bedrock.rank_management.mode.label"),
            listOf(
                lang.bedrock("bedrock.rank_management.mode.create"),
                lang.bedrock("bedrock.rank_management.mode.edit")
            ),
            defaultMode
        )

        // Rank name input
        formBuilder.input(
            lang.bedrock("bedrock.rank_management.name.label"),
            lang.bedrock("bedrock.rank_management.name.placeholder"),
            selectedRank?.name ?: ""
        )

        // Existing ranks dropdown (for editing)
        val existingRanks = rankService.listRanks(guild.id).sortedBy { it.priority }
        formBuilder.dropdown(
            lang.bedrock("bedrock.rank_management.select_rank"),
            existingRanks.map { it.name }.ifEmpty { listOf(lang.bedrock("bedrock.rank_management.no_ranks")) },
            selectedRank?.let { existingRanks.indexOf(it).coerceAtLeast(0) } ?: 0
        )

        // Note: Priority slider removed as it's confusing for users
        // Priority is now automatically assigned based on creation order

        // Permission toggles organized by category
        formBuilder.label(lang.bedrock("bedrock.rank_management.permissions"))

        // Filter out claims permissions if claims are disabled
        val mainConfig = configService.loadConfig()
        val claimsEnabled = mainConfig.claimsEnabled
        val claimsPermissions = setOf(
            RankPermission.MANAGE_CLAIMS,
            RankPermission.MANAGE_FLAGS,
            RankPermission.MANAGE_PERMISSIONS,
            RankPermission.CREATE_CLAIMS,
            RankPermission.DELETE_CLAIMS
        )

        val availablePermissions = if (!claimsEnabled) {
            RankPermission.values().filterNot { it in claimsPermissions }
        } else {
            RankPermission.values().toList()
        }

        availablePermissions.forEach { permission ->
            formBuilder.toggle(
                getPermissionDisplayName(permission),
                selectedRank?.permissions?.contains(permission) ?: false
            )
        }

        // Note: Member limit slider removed as it was not functional and confusing

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
        return if (selectedRank != null) {
            lang.bedrock(
                "bedrock.rank_management.info.editing",
                "guild" to guild.name,
                "count" to rankCount,
                "rank" to selectedRank.name
            )
        } else {
            lang.bedrock("bedrock.rank_management.info.creating", "guild" to guild.name, "count" to rankCount)
        }
    }

    private fun getPermissionDisplayName(permission: RankPermission): String {
        val key = "permission.${permission.name.lowercase().replace("_", ".")}"
        return lang.bedrock(key)
    }

    private fun createValidationSection(): String {
        return lang.bedrock("bedrock.rank_management.validation.section")
    }

    private fun handleFormResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        try {
            onFormResponseReceived()

            val modeIndex = response.next() as? Int ?: 0
            val rankName = response.next() as? String ?: ""
            val selectedRankIndex = response.next() as? Int ?: 0

            // Collect permissions from toggles (must match the filtered list from form creation)
            val mainConfig = configService.loadConfig()
            val claimsEnabled = mainConfig.claimsEnabled
            val claimsPermissions = setOf(
                RankPermission.MANAGE_CLAIMS,
                RankPermission.MANAGE_FLAGS,
                RankPermission.MANAGE_PERMISSIONS,
                RankPermission.CREATE_CLAIMS,
                RankPermission.DELETE_CLAIMS
            )

            val availablePermissions = if (!claimsEnabled) {
                RankPermission.values().filterNot { it in claimsPermissions }
            } else {
                RankPermission.values().toList()
            }

            val permissions = mutableSetOf<RankPermission>()
            for (permission in availablePermissions) {
                val hasPermission = response.next() as? Boolean ?: false
                if (hasPermission) {
                    permissions.add(permission)
                }
            }

            // Validate permissions
            if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RANKS)) {
                player.sendMessage(lang.msg("bedrock.rank_management.error.no_permission"))
                navigateBack()
                return
            }

            // Validate rank name
            if (rankName.length !in 1..24) {
                player.sendMessage(lang.msg("bedrock.rank_management.error.name_length", "minimum" to 1, "maximum" to 24))
                reopen()
                return
            }

            // Check for duplicate names
            val existingRank = rankService.getRankByName(guild.id, rankName)
            if (existingRank != null && existingRank != selectedRank) {
                player.sendMessage(lang.msg("bedrock.rank_management.error.duplicate", "rank" to rankName))
                reopen()
                return
            }

            // Auto-assign priority (no longer checking for conflicts)
            val existingRanks = rankService.listRanks(guild.id)
            val newPriority = existingRanks.maxOfOrNull { it.priority }?.plus(1) ?: 1

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
                        rankService.updateRank(updatedRank, player.uniqueId)
                    }

                    player.sendMessage(lang.msg("bedrock.rank_management.success.created", "rank" to rankName))
                } else {
                    player.sendMessage(lang.msg("bedrock.rank_management.error.create_failed"))
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

                    val success = rankService.updateRank(updatedRank, player.uniqueId)
                    if (success) {
                        player.sendMessage(lang.msg("bedrock.rank_management.success.updated", "rank" to rankName))
                    } else {
                        player.sendMessage(lang.msg("bedrock.rank_management.error.update_failed"))
                    }
                } else {
                    player.sendMessage(lang.msg("bedrock.rank_management.error.not_found"))
                }
            }

            navigateBack()

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error processing rank management form response: ${e.message}")
            player.sendMessage(lang.msg("bedrock.rank_management.error.processing"))
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
