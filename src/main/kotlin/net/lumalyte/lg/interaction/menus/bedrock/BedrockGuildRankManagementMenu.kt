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

        // Note: Priority slider removed as it's confusing for users
        // Priority is now automatically assigned based on creation order

        // Permission toggles organized by category
        formBuilder.addLocalizedLabel(
            player, bedrockLocalization,
            "rank.management.permissions.guild"
        )

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
        val statusColor = if (selectedRank != null) "§e" else "§a"
        val statusText = if (selectedRank != null) {
            bedrockLocalization.getBedrockString(player, "rank.management.info.editing", selectedRank.name)
        } else {
            bedrockLocalization.getBedrockString(player, "rank.management.info.creating")
        }

        return """
            |§7${bedrockLocalization.getBedrockString(player, "rank.management.description")}
            |
            |§6§l━━━ RANK OVERVIEW ━━━
            |§e${bedrockLocalization.getBedrockString(player, "guild.info.guild.name")}§7: §f${guild.name}
            |§b${bedrockLocalization.getBedrockString(player, "rank.list.total")}§7: §f$rankCount
            |$statusColor$statusText
        """.trimMargin()
    }

    private fun createSectionHeader(title: String): String {
        return "§6§l━━━ $title §r§6━━━"
    }

    private fun getPermissionDisplayName(permission: RankPermission): String {
        return when (permission) {
            RankPermission.MANAGE_RANKS -> bedrockLocalization.getBedrockString(player, "permission.manage.ranks")
            RankPermission.MANAGE_MEMBERS -> bedrockLocalization.getBedrockString(player, "permission.manage.members")
            RankPermission.MANAGE_BANNER -> bedrockLocalization.getBedrockString(player, "permission.manage.banner")
            RankPermission.MANAGE_EMOJI -> bedrockLocalization.getBedrockString(player, "permission.manage.emoji")
            RankPermission.MANAGE_DESCRIPTION -> bedrockLocalization.getBedrockString(player, "permission.manage.description")
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
            RankPermission.PLACE_VAULT -> bedrockLocalization.getBedrockString(player, "permission.place.vault")
            RankPermission.ACCESS_VAULT -> bedrockLocalization.getBedrockString(player, "permission.access.vault")
            RankPermission.DEPOSIT_TO_VAULT -> bedrockLocalization.getBedrockString(player, "permission.deposit.vault")
            RankPermission.WITHDRAW_FROM_VAULT -> bedrockLocalization.getBedrockString(player, "permission.withdraw.vault")
            RankPermission.MANAGE_VAULT -> bedrockLocalization.getBedrockString(player, "permission.manage.vault")
            RankPermission.BREAK_VAULT -> bedrockLocalization.getBedrockString(player, "permission.break.vault")
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
            RankPermission.ACCESS_SHOP_CHESTS -> bedrockLocalization.getBedrockString(player, "permission.access.shop.chests")
            RankPermission.EDIT_SHOP_STOCK -> bedrockLocalization.getBedrockString(player, "permission.edit.shop.stock")
            RankPermission.MODIFY_SHOP_PRICES -> bedrockLocalization.getBedrockString(player, "permission.modify.shop.prices")
        }
    }

    private fun createValidationSection(): String {
        return """
            |${createSectionHeader(bedrockLocalization.getBedrockString(player, "form.validation.title"))}
            |§7• ${bedrockLocalization.getBedrockString(player, "validation.rank.name.too.short")}
            |§7• ${bedrockLocalization.getBedrockString(player, "validation.rank.name.too.long")}
            |§7• ${bedrockLocalization.getBedrockString(player, "validation.rank.permissions.required")}
            |§7• ${bedrockLocalization.getBedrockString(player, "validation.rank.priority.conflict")}
        """.trimMargin()
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
                player.sendMessage("§c[ERROR] ${localize("rank.management.error.no.permission")}")
                navigateBack()
                return
            }

            // Validate rank name
            if (rankName.length !in 1..24) {
                player.sendMessage("§c[ERROR] ${localize("rank.management.validation.name.length.error", 1, 24)}")
                reopen()
                return
            }

            // Check for duplicate names
            val existingRank = rankService.getRankByName(guild.id, rankName)
            if (existingRank != null && existingRank != selectedRank) {
                player.sendMessage("§c[ERROR] ${localize("rank.management.validation.name.duplicate", rankName)}")
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

                    player.sendMessage("§a[SUCCESS] ${localize("rank.management.success.created", rankName)}")
                } else {
                    player.sendMessage("§c[ERROR] ${localize("rank.management.error.create.failed")}")
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
                        player.sendMessage("§a[SUCCESS] ${localize("rank.management.success.updated", rankName)}")
                    } else {
                        player.sendMessage("§c[ERROR] ${localize("rank.management.error.update.failed")}")
                    }
                } else {
                    player.sendMessage("§c[ERROR] ${localize("rank.management.error.rank.not.found")}")
                }
            }

            navigateBack()

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error processing rank management form response: ${e.message}")
            player.sendMessage("§c[ERROR] ${localize("form.error.processing")}")
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
