package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
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
    private val guildService: GuildService by inject()
    private val authorization by lazy { BedrockGuildAuthorization(guildService) }
    private val configService: ConfigService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        if (!authorization.canManageRanks(player.uniqueId, guild.id)) {
            return SimpleForm.builder()
                .title(lang.bedrock("bedrock.rank_management.title", "guild" to guild.name))
                .content(lang.bedrock("bedrock.rank_management.error.no_permission"))
                .button(lang.bedrock("bedrock.rank_management.back"))
                .validResultHandler {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        if (player.isOnline) bedrockNavigator.goBack()
                    })
                }
                .closedOrInvalidResultHandler { _, _ ->
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        if (player.isOnline) bedrockNavigator.goBack()
                    })
                }
                .build()
        }
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
            RankPermission.entries.filterNot { it in claimsPermissions }
        } else {
            RankPermission.entries.toList()
        }

        availablePermissions.forEach { permission ->
            formBuilder.toggle(
                permissionDisplayName(lang, permission),
                selectedRank?.permissions?.contains(permission) ?: false
            )
        }

        // Note: Member limit slider removed as it was not functional and confusing

        // Validation section
        formBuilder.label(createValidationSection())

        formBuilder.validResultHandler { response ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (player.isOnline) handleFormResponse(response)
            })
        }

        formBuilder.closedOrInvalidResultHandler { _, _ ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (player.isOnline) navigateBack()
            })
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

    companion object {
        fun permissionDisplayName(lang: LangService, permission: RankPermission): String {
            val key = "permission.${permission.name.lowercase().replace("_", ".")}"
            return lang.bedrock(key)
        }
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
                RankPermission.entries.filterNot { it in claimsPermissions }
            } else {
                RankPermission.entries.toList()
            }

            val permissions = mutableSetOf<RankPermission>()
            for (permission in availablePermissions) {
                val hasPermission = response.next() as? Boolean ?: false
                if (hasPermission) {
                    permissions.add(permission)
                }
            }

            // Validate permissions
            if (!authorization.canManageRanks(player.uniqueId, guild.id)) {
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

            val rankToEdit = if (modeIndex == 1) {
                selectedRank ?: rankService.listRanks(guild.id)
                    .sortedBy { it.priority }
                    .getOrNull(selectedRankIndex)
            } else {
                null
            }

            // Check for duplicate names without rejecting the rank currently being edited.
            val existingRank = rankService.getRankByName(guild.id, rankName)
            if (existingRank != null && existingRank.id != rankToEdit?.id) {
                player.sendMessage(lang.msg("bedrock.rank_management.error.duplicate", "rank" to rankName))
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
                    // RankService owns creation-order priority; the Bedrock adapter does not reorder it.
                    player.sendMessage(lang.msg("bedrock.rank_management.success.created", "rank" to rankName))
                } else {
                    player.sendMessage(lang.msg("bedrock.rank_management.error.create_failed"))
                }
            } else {
                // Ordinary edits preserve immutable identity and existing priority.
                if (rankToEdit != null) {
                    val effectivePermissions = permissions.toMutableSet()
                    if (!claimsEnabled) {
                        effectivePermissions += rankToEdit.permissions.filter { it in claimsPermissions }
                    }
                    val updatedRank = rankToEdit.copy(
                        name = rankName,
                        priority = rankToEdit.priority,
                        permissions = effectivePermissions
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
