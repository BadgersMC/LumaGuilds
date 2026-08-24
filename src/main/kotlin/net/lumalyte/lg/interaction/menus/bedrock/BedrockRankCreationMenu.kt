package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition rank creation menu using Cumulus CustomForm
 * Allows creating a new rank with name and basic permissions
 */
class BedrockRankCreationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val rankService: RankService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val rankIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title(lang.legacy("bedrock.rank_creation.title", "guild" to guild.name))
            .apply { rankIcon?.let { icon(it) } }
            .label(lang.raw("bedrock.rank_creation.description"))
            .input(
                lang.raw("bedrock.rank_creation.name.label"),
                lang.raw("bedrock.rank_creation.name.placeholder"),
                ""
            )
            .label(lang.raw("bedrock.rank_creation.permissions.header"))
            .toggle(lang.raw("bedrock.rank_creation.permissions.invite"), false)
            .toggle(lang.raw("bedrock.rank_creation.permissions.kick"), false)
            .toggle(lang.raw("bedrock.rank_creation.permissions.promote"), false)
            .toggle(lang.raw("bedrock.rank_creation.permissions.manage_ranks"), false)
            .toggle(lang.raw("bedrock.rank_creation.permissions.manage_settings"), false)
            .toggle(lang.raw("bedrock.rank_creation.permissions.declare_war"), false)
            .toggle(lang.raw("bedrock.rank_creation.permissions.manage_bank"), false)
            .toggle(lang.raw("bedrock.rank_creation.permissions.access_vault"), true) // Default true
            .validResultHandler { response ->
                val rankName = response.asInput(1)?.trim() ?: ""
                val canInvite = response.asToggle(3)
                val canKick = response.asToggle(4)
                val canPromote = response.asToggle(5)
                val canManageRanks = response.asToggle(6)
                val canManageSettings = response.asToggle(7)
                val canDeclareWar = response.asToggle(8)
                val canManageBank = response.asToggle(9)
                val canAccessVault = response.asToggle(10)

                handleRankCreation(
                    rankName,
                    canInvite,
                    canKick,
                    canPromote,
                    canManageRanks,
                    canManageSettings,
                    canDeclareWar,
                    canManageBank,
                    canAccessVault
                )
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun handleRankCreation(
        rankName: String,
        canInvite: Boolean,
        canKick: Boolean,
        canPromote: Boolean,
        canManageRanks: Boolean,
        canManageSettings: Boolean,
        canDeclareWar: Boolean,
        canManageBank: Boolean,
        canAccessVault: Boolean
    ) {
        // Validate name
        if (rankName.isEmpty()) {
            player.sendMessage(lang.msg("bedrock.rank_creation.feedback.empty_name"))
            bedrockNavigator.goBack()
            return
        }

        if (rankName.length > 20) {
            player.sendMessage(lang.msg("bedrock.rank_creation.feedback.name_too_long"))
            bedrockNavigator.goBack()
            return
        }

        // Build permissions set
        val permissions = mutableSetOf<RankPermission>()
        if (canInvite) permissions.add(RankPermission.MANAGE_MEMBERS)
        if (canKick) permissions.add(RankPermission.MANAGE_MEMBERS)
        if (canPromote) permissions.add(RankPermission.MANAGE_MEMBERS)
        if (canManageRanks) permissions.add(RankPermission.MANAGE_RANKS)
        if (canManageSettings) permissions.add(RankPermission.MANAGE_GUILD_SETTINGS)
        if (canDeclareWar) permissions.add(RankPermission.DECLARE_WAR)
        if (canManageBank) permissions.add(RankPermission.MANAGE_BANK_SETTINGS)
        if (canAccessVault) permissions.add(RankPermission.ACCESS_VAULT)

        // Create rank
        val rank = rankService.addRank(guild.id, rankName, permissions, player.uniqueId)

        if (rank != null) {
            player.sendMessage(lang.msg("bedrock.rank_creation.feedback.created", "rank" to rankName))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(lang.msg("bedrock.rank_creation.feedback.failed"))
            bedrockNavigator.goBack()
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
